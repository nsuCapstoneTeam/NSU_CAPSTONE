package com.nsu.capstone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrustProfileApiIntegrationTest {

    private static final String TRUST_PROFILE_PATH = "/api/v1/artists/{artistId}/trust-profile";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UuidV7Generator uuidV7Generator;
    @Autowired JwtAccessTokenService accessTokenService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtEncoder jwtEncoder;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void eventPartnerGetsInsufficientEvidenceProfileOfActiveArtist() throws Exception {
        UUID artistId = saveArtist();

        mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                .header("Authorization", bearer(UUID.randomUUID(), UserRole.EVENT_PARTNER)))
            .andExpect(status().isOk())
            .andExpect(content().json(
                insufficientEvidenceProfile(artistId),
                JsonCompareMode.STRICT
            ));
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void artistGetsOwnProfileWhateverAccountStatus(UserStatus accountStatus) throws Exception {
        UUID artistId = saveArtist(accountStatus);

        mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                .header("Authorization", bearer(artistId, UserRole.ARTIST)))
            .andExpect(status().isOk())
            .andExpect(content().json(
                insufficientEvidenceProfile(artistId),
                JsonCompareMode.STRICT
            ));
    }

    @Test
    void artistIsDeniedAnotherArtistsProfileWhetherOrNotItExists() throws Exception {
        String artist = bearer(uuidV7Generator.generate(), UserRole.ARTIST);
        UUID existingArtistId = saveArtist();
        UUID missingArtistId = uuidV7Generator.generate();

        String existingTargetBody = mockMvc.perform(get(TRUST_PROFILE_PATH, existingArtistId)
                .header("Authorization", artist))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(TRUST_PROFILE_PATH, missingArtistId)
                .header("Authorization", artist))
            .andExpect(status().isForbidden())
            .andExpect(content().json(existingTargetBody, JsonCompareMode.STRICT));
    }

    @Test
    void eventPartnerGetsSameNotFoundForMissingNonArtistOrInactiveArtist() throws Exception {
        String eventPartner = bearer(uuidV7Generator.generate(), UserRole.EVENT_PARTNER);
        List<UUID> hiddenTargets = List.of(
            uuidV7Generator.generate(),
            saveEventPartner(),
            saveArtist(UserStatus.WITHDRAWN),
            saveArtist(UserStatus.SUSPENDED)
        );

        Set<String> bodies = new HashSet<>();
        for (UUID target : hiddenTargets) {
            bodies.add(mockMvc.perform(get(TRUST_PROFILE_PATH, target)
                    .header("Authorization", eventPartner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString());
        }

        assertThat(bodies).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-uuid",
        "1-1-1-1-1",
        "0192f3a47b1c7d2e8f001a2b3c4d5e6f",
        "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6g",
        "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f0"
    })
    void rejectsNonCanonicalArtistIdBeforeCheckingAccess(String artistId) throws Exception {
        for (UserRole callerRole : UserRole.values()) {
            mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                    .header("Authorization", bearer(uuidV7Generator.generate(), callerRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_ID"));
        }
    }

    @Test
    void acceptsUppercaseArtistIdAndAnswersInLowercase() throws Exception {
        UUID artistId = saveArtist();
        String uppercaseArtistId = artistId.toString().toUpperCase(Locale.ROOT);

        mockMvc.perform(get(TRUST_PROFILE_PATH, uppercaseArtistId)
                .header("Authorization", bearer(uuidV7Generator.generate(), UserRole.EVENT_PARTNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artistId").value(artistId.toString()));

        mockMvc.perform(get(TRUST_PROFILE_PATH, uppercaseArtistId)
                .header("Authorization", bearer(artistId, UserRole.ARTIST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artistId").value(artistId.toString()));
    }

    @Test
    void rejectsRequestWithoutTokenBeforeValidatingPath() throws Exception {
        for (Object artistId : List.of(saveArtist(), "not-a-uuid")) {
            mockMvc.perform(get(TRUST_PROFILE_PATH, artistId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void rejectsTokenWithForeignSignatureOrPastExpiryBeforeValidatingPath() throws Exception {
        Instant now = Instant.now();
        JwtEncoder foreignEncoder = NimbusJwtEncoder
            .withSecretKey(new SecretKeySpec(
                "foreign-test-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ))
            .algorithm(MacAlgorithm.HS256)
            .build();
        List<String> rejectedTokens = List.of(
            eventPartnerToken(foreignEncoder, now.plusSeconds(600)),
            eventPartnerToken(jwtEncoder, now.minusSeconds(600))
        );

        for (String token : rejectedTokens) {
            for (Object artistId : List.of(saveArtist(), "not-a-uuid")) {
                mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                        .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            }
        }
    }

    private static String eventPartnerToken(JwtEncoder encoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(UUID.randomUUID().toString())
            .claim("role", UserRole.EVENT_PARTNER.name())
            .issuedAt(expiresAt.minusSeconds(1800))
            .expiresAt(expiresAt)
            .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private UUID saveArtist() {
        return saveArtist(UserStatus.ACTIVE);
    }

    private UUID saveArtist(UserStatus accountStatus) {
        UUID id = uuidV7Generator.generate();
        userRepository.saveAndFlush(User.createArtist(id, id + "@example.com", null, "01000000000"));
        createdUserIds.add(id);
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", accountStatus.name(), id);
        return id;
    }

    private UUID saveEventPartner() {
        UUID id = uuidV7Generator.generate();
        userRepository.saveAndFlush(
            User.createEventPartner(id, id + "@example.com", null, "01000000000")
        );
        createdUserIds.add(id);
        return id;
    }

    private String bearer(UUID userId, UserRole role) {
        return "Bearer " + accessTokenService.issue(userId, role).value();
    }

    private static String insufficientEvidenceProfile(UUID artistId) {
        return """
            {
              "artistId": "%s",
              "policyVersion": "reliability-v1",
              "verifications": [],
              "reliability": {
                "confidenceLevel": "LOW",
                "observationCount": 0,
                "ratePercent": null
              },
              "evidence": {
                "observations": {
                  "completedCount": 0,
                  "artistCancellations": {
                    "noticeAtLeast7DaysCount": 0,
                    "notice24HoursTo7DaysCount": 0,
                    "noticeUnder24HoursCount": 0
                  },
                  "noShowCount": 0
                },
                "exclusions": []
              },
              "riskSignals": [],
              "activityFreshness": {
                "lastPerformedMonth": null,
                "completedCountLast12Months": 0
              }
            }
            """.formatted(artistId);
    }
}
