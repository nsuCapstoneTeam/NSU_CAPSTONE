package com.nsu.capstone.identity.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.nsu.capstone.identity.domain.UserRole;
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
        return issue(principal.userId(), principal.role());
    }

    public IssuedAccessToken issue(UUID userId, UserRole role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("role", role.name())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.ttl().toSeconds());
    }
}
