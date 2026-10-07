package com.nsu.capstone.identity.security;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtAccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtAccessTokenProperties properties;
    private final Clock clock;

    public JwtAccessTokenService(
        JwtEncoder jwtEncoder,
        JwtAccessTokenProperties properties,
        Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(LoginPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(principal.userId().toString())
            .claim("role", principal.role().name())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.ttl().toSeconds());
    }
}
