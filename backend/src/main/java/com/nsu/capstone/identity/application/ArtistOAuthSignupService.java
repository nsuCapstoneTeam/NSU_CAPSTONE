package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthSignupStateStore;
import com.nsu.capstone.identity.oauth.OAuthSignupStateStore.Prepared;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import com.nsu.capstone.identity.verification.terms.RequiredTermsPolicy;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class ArtistOAuthSignupService {
    private static final Logger log = LoggerFactory.getLogger(ArtistOAuthSignupService.class);
    private final RequiredTermsPolicy termsPolicy;
    private final Clock clock;
    private final RedisPublicRequestLimitStore limits;
    private final OAuthSignupStateStore stateStore;
    private final OAuthSignupPersistenceService persistence;
    private final OAuthOpaqueValueService opaqueValues;

    public ArtistOAuthSignupService(
            OAuthSignupStateStore stateStore,
            OAuthSignupPersistenceService persistence,
            OAuthOpaqueValueService opaqueValues,
            RequiredTermsPolicy termsPolicy,
            Clock clock,
            RedisPublicRequestLimitStore limits) {
        this.limits = limits;
        this.termsPolicy = termsPolicy;
        this.clock = clock;
        this.stateStore = stateStore;
        this.persistence = persistence;
        this.opaqueValues = opaqueValues;
    }

    public Prepared prepare(String id, String phone, String email, List<TermVersion> agreements, String remoteAddress) {
        limits.signup(remoteAddress);
        var consent = termsPolicy.accept(agreements, Instant.now(clock));
        try {
            return stateStore.prepare(
                    id,
                    SignupIdentityNormalizer.phone(phone),
                    SignupIdentityNormalizer.email(email),
                    UserRole.ARTIST,
                    consent);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
    }

    public ArtistSignupResponse signup(String id) {
        String owner = opaqueValues.generate();
        var state = stateStore.claim(id, owner, UserRole.ARTIST);
        ArtistSignupResponse response;
        try {
            response = persistence.createArtist(state, () -> releaseAfterRollback(id, owner));
        } catch (RuntimeException exception) {
            // Outside the failed transaction; classify only known UNIQUE violations.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())) {
                    if ("uk_users_email".equals(violation.getConstraintName())) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                    }
                    if ("uk_oauth_accounts_provider_user_id".equals(violation.getConstraintName())) {
                        throw new BusinessException(ErrorCode.OAUTH_ACCOUNT_ALREADY_EXISTS);
                    }
                }
            }
            throw exception;
        }
        // The separate bean's transactional proxy has committed before returning here.
        try {
            stateStore.cleanup(id, owner);
        } catch (RuntimeException exception) {
            log.warn(
                    "OAuth signup committed; temporary state cleanup failed ({}). TTL will expire it.",
                    exception.getClass().getSimpleName());
        }
        return response;
    }

    private void releaseAfterRollback(String id, String owner) {
        try {
            stateStore.release(id, owner);
        } catch (RuntimeException exception) {
            log.warn(
                    "OAuth signup rollback confirmed; claim release failed ({}). Lease will expire it.",
                    exception.getClass().getSimpleName());
        }
    }
}
