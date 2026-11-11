package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record SignupTermsConsent(List<TermVersion> agreements, Instant agreedAt) {
    public SignupTermsConsent {
        agreements = agreements.stream()
                .sorted(Comparator.comparing(TermVersion::id))
                .toList();
        if (agreements.isEmpty() || agreedAt == null) throw new IllegalArgumentException("동의 기록이 필요합니다");
    }

    public String encoded() {
        return agreements.stream()
                .map(t -> encode(t.id()) + "." + encode(t.version()))
                .collect(Collectors.joining(","));
    }

    public static SignupTermsConsent decode(String encoded, String agreedAt) {
        if (encoded == null || encoded.isEmpty() || agreedAt == null || agreedAt.isEmpty()) return null;
        var terms = Arrays.stream(encoded.split(","))
                .map(value -> {
                    var parts = value.split("\\.", -1);
                    return new TermVersion(decodePart(parts[0]), decodePart(parts[1]));
                })
                .toList();
        return new SignupTermsConsent(terms, Instant.parse(agreedAt));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
