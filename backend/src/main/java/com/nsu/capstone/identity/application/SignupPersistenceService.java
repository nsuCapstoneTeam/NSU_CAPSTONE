package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserTermsAgreement;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.repository.UserTermsAgreementRepository;
import com.nsu.capstone.identity.signup.SignupTermsConsent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupPersistenceService {
    private final UserRepository users;
    private final UserTermsAgreementRepository terms;

    public SignupPersistenceService(UserRepository users, UserTermsAgreementRepository terms) {
        this.users = users;
        this.terms = terms;
    }

    @Transactional
    public User save(User user, SignupTermsConsent consent) {
        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "uk_users_email".equals(violation.getConstraintName())) {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }
            throw exception;
        }
        terms.saveAllAndFlush(consent.agreements().stream()
                .map(t -> new UserTermsAgreement(saved, t.id(), t.version(), consent.agreedAt()))
                .toList());
        return saved;
    }
}
