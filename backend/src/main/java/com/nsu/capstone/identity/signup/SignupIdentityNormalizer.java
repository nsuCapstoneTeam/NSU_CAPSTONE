package com.nsu.capstone.identity.signup;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Locale;

public final class SignupIdentityNormalizer {
    private SignupIdentityNormalizer() {}

    public static String email(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    public static String phone(String value) {
        if (value == null || !value.matches("01[016789][0-9]{7,8}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }
}
