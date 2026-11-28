package com.nsu.capstone.identity.signup;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OtpCodeGenerator {

    private final SecureRandom secureRandom;
    private final int length;
    private final int upperBound;

    OtpCodeGenerator(SecureRandom secureRandom, SignupVerificationProperties properties) {
        this.secureRandom = secureRandom;
        this.length = properties.otpLength();
        this.upperBound = powerOfTen(length);
    }

    public String generate() {
        return String.format("%0" + length + "d", secureRandom.nextInt(upperBound));
    }

    private int powerOfTen(int exponent) {
        if (exponent > 9) {
            throw new IllegalArgumentException("인증 코드 길이는 9자리를 넘을 수 없습니다");
        }
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }
}
