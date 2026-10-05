package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class OtpDigestService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public OtpDigestService(SignupVerificationProperties properties) {
        this.secretKey = new SecretKeySpec(
            properties.hmacSecret().getBytes(StandardCharsets.UTF_8),
            ALGORITHM
        );
    }

    public String codeDigest(
        String signupSessionId,
        VerificationChannel channel,
        String destination,
        String generationId,
        String code
    ) {
        return digest(String.join(
            "\u001f",
            "otp",
            signupSessionId,
            channel.name(),
            destination,
            generationId,
            code
        ));
    }

    public String destinationDigest(VerificationChannel channel, String destination) {
        return digest(String.join("\u001f", "destination", channel.name(), destination));
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate verification digest", exception);
        }
    }
}
