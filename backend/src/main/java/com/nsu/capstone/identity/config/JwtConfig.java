package com.nsu.capstone.identity.config;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.security.JwtAccessTokenProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtAccessTokenProperties.class)
public class JwtConfig {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;
    private static final String INVALID_TOKEN = "invalid_token";

    @Bean
    SecretKey jwtAccessTokenSecretKey(JwtAccessTokenProperties properties) {
        String encodedSecret = properties.secret();
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("JWT access token secret must be configured");
        }

        byte[] decodedSecret;
        try {
            decodedSecret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT access token secret must be valid Base64");
        }
        if (decodedSecret.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalStateException("JWT access token secret must be at least 256 bits");
        }
        return new SecretKeySpec(decodedSecret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtAccessTokenSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtAccessTokenSecretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtAccessTokenSecretKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(jwtAccessTokenSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            timestampValidator,
            subjectValidator(),
            roleValidator()
        ));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> subjectValidator() {
        return jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return invalidClaim("JWT subject must be a UUID");
            }
        };
    }

    private OAuth2TokenValidator<Jwt> roleValidator() {
        return jwt -> {
            try {
                UserRole.valueOf(jwt.getClaimAsString("role"));
                return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return invalidClaim("JWT role is invalid");
            }
        };
    }

    private OAuth2TokenValidatorResult invalidClaim(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(INVALID_TOKEN, description, null));
    }
}
