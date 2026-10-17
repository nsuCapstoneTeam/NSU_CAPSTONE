package com.nsu.capstone;

import static com.nsu.capstone.TrustProfileExamples.insufficientEvidenceProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import com.nsu.capstone.trust.profile.application.PerformanceOutcomeReader;
import com.nsu.capstone.trust.profile.application.VerificationReader;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrustProfileApiIntegrationTest {

    private static final String TRUST_PROFILE_PATH = "/api/v1/artists/{artistId}/trust-profile";

    /**
     * API 01 §4.1의 객체 필드 경로. 배열 원소의 필드는 {@link #CONTRACT_ELEMENT_KEYS}가 정한다.
     */
    private static final Set<String> CONTRACT_KEY_PATHS = Set.of(
        "artistId",
        "policyVersion",
        "verifications",
        "reliability",
        "reliability.confidenceLevel",
        "reliability.observationCount",
        "reliability.ratePercent",
        "evidence",
        "evidence.observations",
        "evidence.observations.completedCount",
        "evidence.observations.artistCancellations",
        "evidence.observations.artistCancellations.noticeAtLeast7DaysCount",
        "evidence.observations.artistCancellations.notice24HoursTo7DaysCount",
        "evidence.observations.artistCancellations.noticeUnder24HoursCount",
        "evidence.observations.noShowCount",
        "evidence.exclusions",
        "riskSignals",
        "activityFreshness",
        "activityFreshness.lastPerformedMonth",
        "activityFreshness.completedCountLast12Months"
    );
    /**
     * 배열 필드마다 원소가 가져야 하는 필드. API 01 §4.3·§4.4·§4.5.
     */
    private static final Map<String, Set<String>> CONTRACT_ELEMENT_KEYS = Map.of(
        "verifications", Set.of("type", "provider"),
        "evidence.exclusions", Set.of("outcomeType", "responsibleParty", "count"),
        "riskSignals", Set.of("type", "count", "windowObservationCount", "lastOccurredMonth")
    );
    private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern DATE_WITH_DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UuidV7Generator uuidV7Generator;
    @Autowired JwtAccessTokenService accessTokenService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PerformanceOutcomeReader performanceOutcomeReader;
    @MockitoBean VerificationReader verificationReader;

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
    void showsEachVerifiedItemOnceInContractOrder() throws Exception {
        UUID artistId = saveArtist();
        when(verificationReader.findVerifiedByArtistIds(any())).thenReturn(Map.of(artistId, List.of(
            new VerificationItem(VerificationType.OFFICIAL_ACTIVITY, null),
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE),
            new VerificationItem(VerificationType.COPYRIGHT, null),
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.SOUNDCLOUD),
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE),
            new VerificationItem(VerificationType.PERFORMANCE_PARTICIPATION, null),
            new VerificationItem(VerificationType.BUSINESS_REGISTRATION, null),
            new VerificationItem(VerificationType.COPYRIGHT, null)
        )));

        String body = mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                .header("Authorization", bearer(uuidV7Generator.generate(), UserRole.EVENT_PARTNER)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals("""
            {
              "verifications": [
                { "type": "EXTERNAL_ACCOUNT", "provider": "SOUNDCLOUD" },
                { "type": "EXTERNAL_ACCOUNT", "provider": "YOUTUBE" },
                { "type": "COPYRIGHT", "provider": null },
                { "type": "BUSINESS_REGISTRATION", "provider": null },
                { "type": "PERFORMANCE_PARTICIPATION", "provider": null },
                { "type": "OFFICIAL_ACTIVITY", "provider": null }
              ]
            }
            """, body, JSONCompareMode.STRICT_ORDER);
    }

    @Test
    void answersInternalErrorWithoutExposingSourceFailure() throws Exception {
        UUID artistId = saveArtist();
        when(verificationReader.findVerifiedByArtistIds(any()))
            .thenThrow(new IllegalStateException("verification storage detail"));

        mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                .header("Authorization", bearer(uuidV7Generator.generate(), UserRole.EVENT_PARTNER)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(content().string(not(containsString("verification storage detail"))));
    }

    @Test
    void exposesOnlyContractFieldsWithoutPrivateLedgerDetails() throws Exception {
        UUID artistId = saveArtist();
        UUID correctedPerformance = UUID.randomUUID();
        UUID voidedPerformance = UUID.randomUUID();
        List<PerformanceOutcome> ledger = List.of(
            outcome(UUID.randomUUID(), 1, OutcomeType.COMPLETED, null, "2026-05-02T19:00", null),
            outcome(UUID.randomUUID(), 1, OutcomeType.COMPLETED, null, "2026-05-09T19:00", null),
            outcome(UUID.randomUUID(), 1, OutcomeType.CANCELLED, ResponsibleParty.ARTIST,
                "2026-06-01T19:00", "2026-06-01T10:00"),
            outcome(UUID.randomUUID(), 1, OutcomeType.CANCELLED, ResponsibleParty.ARTIST,
                "2026-07-01T19:00", "2026-06-10T10:00"),
            outcome(UUID.randomUUID(), 1, OutcomeType.NO_SHOW, ResponsibleParty.ARTIST,
                "2026-07-04T19:00", null),
            outcome(UUID.randomUUID(), 1, OutcomeType.CANCELLED, ResponsibleParty.EVENT_PARTNER,
                "2026-07-18T19:00", "2026-07-10T10:00"),
            outcome(correctedPerformance, 1, OutcomeType.NO_SHOW, ResponsibleParty.EVENT_PARTNER,
                "2026-08-01T19:00", null),
            outcome(correctedPerformance, 2, OutcomeType.COMPLETED, null, "2026-08-01T19:00", null),
            outcome(voidedPerformance, 1, OutcomeType.COMPLETED, null, "2026-08-08T19:00", null),
            outcome(voidedPerformance, 2, OutcomeType.VOIDED, null, "2026-08-08T19:00", null)
        );
        when(performanceOutcomeReader.findByArtistIds(any())).thenReturn(Map.of(artistId, ledger));
        when(verificationReader.findVerifiedByArtistIds(any())).thenReturn(Map.of(artistId, List.of(
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE),
            new VerificationItem(VerificationType.COPYRIGHT, null)
        )));

        String body = mockMvc.perform(get(TRUST_PROFILE_PATH, artistId)
                .header("Authorization", bearer(uuidV7Generator.generate(), UserRole.EVENT_PARTNER)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);

        for (String arrayPath : CONTRACT_ELEMENT_KEYS.keySet()) {
            JsonNode array = nodeAt(response, arrayPath);
            assertThat(array.isArray()).as("%s는 배열이다", arrayPath).isTrue();
            assertThat(array.size()).as("%s 원소 수", arrayPath).isPositive();
        }
        assertThat(keyPathsOf(response))
            .containsExactlyInAnyOrderElementsOf(contractKeyPathsOf(response));
        assertThat(response.at("/activityFreshness/lastPerformedMonth").stringValue())
            .matches(YEAR_MONTH);
        assertThat(response.get("riskSignals").values()).isNotEmpty().allSatisfy(signal ->
            assertThat(signal.get("lastOccurredMonth").stringValue()).matches(YEAR_MONTH));
        List<JsonNode> scalars = scalarsOf(response);
        assertThat(scalars).filteredOn(JsonNode::isString).extracting(JsonNode::stringValue)
            .noneMatch(value -> DATE_WITH_DAY.matcher(value).find());
        assertThat(scalars).filteredOn(JsonNode::isNumber).allMatch(JsonNode::isIntegralNumber);
        assertThat(body).doesNotContain(ledger.stream()
            .map(outcome -> outcome.performanceId().toString())
            .toArray(String[]::new));
        assertThat(body).doesNotContain(
            "SUPERSEDED", "VOIDED", "NON_ARTIST_ATTRIBUTION",
            "REPUTATION_MANIPULATION_SUSPECTED", "ACCOUNT_SANCTION", "ADMIN_DECISION"
        );
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

    private static PerformanceOutcome outcome(
        UUID performanceId,
        int revision,
        OutcomeType outcomeType,
        ResponsibleParty responsibleParty,
        String scheduledStartAt,
        String cancellationRequestedAt
    ) {
        return new PerformanceOutcome(
            performanceId,
            revision,
            outcomeType,
            responsibleParty,
            seoul(scheduledStartAt),
            cancellationRequestedAt == null ? null : seoul(cancellationRequestedAt)
        );
    }

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }

    /**
     * 응답에 있는 모든 객체 키의 경로. 배열 원소는 {@code riskSignals[1].count}처럼 순번을 붙여
     * 원소 안의 중첩 키까지 모은다.
     */
    private static Set<String> keyPathsOf(JsonNode node) {
        Set<String> paths = new TreeSet<>();
        collectKeyPaths(node, "", paths);
        return paths;
    }

    private static void collectKeyPaths(JsonNode node, String prefix, Set<String> paths) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                String path = prefix.isEmpty() ? property.getKey() : prefix + "." + property.getKey();
                paths.add(path);
                collectKeyPaths(property.getValue(), path, paths);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectKeyPaths(node.get(index), prefix + "[" + index + "]", paths);
            }
        }
    }

    /**
     * 계약이 요구하는 키 경로를 응답의 배열 크기만큼 펼친다. 배열 원소마다 계약 필드를 모두 기대한다.
     */
    private static Set<String> contractKeyPathsOf(JsonNode response) {
        Set<String> paths = new TreeSet<>(CONTRACT_KEY_PATHS);
        CONTRACT_ELEMENT_KEYS.forEach((arrayPath, elementKeys) -> {
            for (int index = 0; index < nodeAt(response, arrayPath).size(); index++) {
                for (String key : elementKeys) {
                    paths.add(arrayPath + "[" + index + "]." + key);
                }
            }
        });
        return paths;
    }

    private static JsonNode nodeAt(JsonNode response, String path) {
        return response.at("/" + path.replace('.', '/'));
    }

    private static List<JsonNode> scalarsOf(JsonNode node) {
        if (!node.isContainer()) {
            return List.of(node);
        }
        return node.values().stream()
            .flatMap(child -> scalarsOf(child).stream())
            .toList();
    }
}
