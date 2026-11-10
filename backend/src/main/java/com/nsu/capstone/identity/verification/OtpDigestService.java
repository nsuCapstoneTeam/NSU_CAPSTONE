package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.net.InetAddress;
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
        this.secretKey = new SecretKeySpec(properties.hmacSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String codeDigest(
            String signupSessionId, VerificationChannel channel, String destination, String generationId, String code) {
        return digest(String.join("\u001f", "otp", signupSessionId, channel.name(), destination, generationId, code));
    }

    public String destinationDigest(VerificationChannel channel, String destination) {
        return digest(String.join("\u001f", "destination", channel.name(), destination));
    }

    public String rateLimitDestinationDigest(VerificationChannel channel, String destination) {
        String normalized = channel == VerificationChannel.EMAIL
                ? SignupIdentityNormalizer.email(destination)
                : canonicalMobileNumber(destination);
        return destinationDigest(channel, normalized);
    }

    public String canonicalMobileNumber(String phone) {
        return SignupIdentityNormalizer.phone(phone);
    }

    public String clientIpDigest(String remoteAddress) {
        byte[] address = InetAddress.ofLiteral(remoteAddress).getAddress();
        // IPv4는 주소 전체, IPv6는 앞 64비트로 제한한다.
        String prefix = HexFormat.of().formatHex(address, 0, Math.min(address.length, 8));
        return digest("ip\u001f" + prefix);
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
