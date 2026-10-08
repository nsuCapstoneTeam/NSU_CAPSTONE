package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthAccount;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.oauth.OAuthSignupStateStore.Claimed;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.repository.OAuthAccountRepository;
import com.nsu.capstone.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OAuthSignupPersistenceService {
    private final UserRepository users;
    private final OAuthAccountRepository accounts;
    private final UserIdGenerator ids;

    public OAuthSignupPersistenceService(UserRepository users, OAuthAccountRepository accounts,
        UserIdGenerator ids) {
        this.users = users;
        this.accounts = accounts;
        this.ids = ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArtistSignupResponse createArtist(Claimed state, Runnable onConfirmedRollback) {
        // Only transaction completion can prove rollback. UNKNOWN intentionally keeps the lease.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) onConfirmedRollback.run();
            }
        });
        if (accounts.findByProviderAndProviderUserId(state.provider(), state.providerUserId()).isPresent()) {
            throw new BusinessException(ErrorCode.OAUTH_ACCOUNT_ALREADY_EXISTS);
        }
        if (users.existsByEmail(state.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = users.save(User.createArtist(ids.generate(), state.email(), null, state.phone()));
        accounts.saveAndFlush(OAuthAccount.create(ids.generate(), user, state.provider(), state.providerUserId()));
        return new ArtistSignupResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
    }
}
