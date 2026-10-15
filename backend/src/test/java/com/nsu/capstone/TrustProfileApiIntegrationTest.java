package com.nsu.capstone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
