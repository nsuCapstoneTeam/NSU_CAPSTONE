package com.nsu.capstone.identity.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OtpCodeGeneratorTest {

    @Test
    void generatesZeroPaddedSixDigitCode() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);

        String code = new OtpCodeGenerator(secureRandom, properties()).generate();

        assertEquals("000042", code);
        assertTrue(code.matches("\\d{6}"));
    }

    private SignupVerificationProperties properties() {
        return new SignupVerificationProperties(
            Duration.ofMinutes(5),
            Duration.ofSeconds(60),
            5,
            6,
            "test-secret",
            List.of("service-terms:v1")
        );
    }
}
