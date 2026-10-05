package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import org.springframework.stereotype.Component;

@Component
public class SignupRequiredConditionsValidator {

    public void validate(SignupSession signupSession, UserRole expectedRole) {
        if (signupSession.role() != expectedRole) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
        if (!signupSession.emailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
        if (!signupSession.phoneVerified()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        if (!signupSession.requiredTermsAgreed()) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED);
        }
        if (!signupSession.adultConfirmed()) {
            throw new BusinessException(ErrorCode.ADULT_CONFIRMATION_REQUIRED);
        }
    }
}
