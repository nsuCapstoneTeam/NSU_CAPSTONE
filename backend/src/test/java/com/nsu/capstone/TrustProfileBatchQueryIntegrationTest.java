package com.nsu.capstone;

import static com.nsu.capstone.TrustProfileExamples.insufficientEvidenceProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import com.nsu.capstone.trust.profile.application.PerformanceOutcomeReader;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import com.nsu.capstone.trust.profile.application.VerificationReader;
import com.nsu.capstone.trust.profile.domain.ConfidenceLevel;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Reliability;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TrustProfileBatchQueryIntegrationTest {

    @Autowired TrustProfileQueryService queryService;
    @Autowired UserRepository userRepository;
    @Autowired UuidV7Generator uuidV7Generator;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean PerformanceOutcomeReader performanceOutcomeReader;
    @MockitoSpyBean VerificationReader verificationReader;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void returnsInsufficientEvidenceProfilesOfArtistsOnlyWhateverAccountStatus() throws Exception {
        List<UUID> artistIds = List.of(
            saveArtist(UserStatus.ACTIVE),
            saveArtist(UserStatus.SUSPENDED),
            saveArtist(UserStatus.WITHDRAWN)
        );
        UUID eventPartnerId = saveEventPartner();
        UUID missingUserId = uuidV7Generator.generate();
        List<UUID> requestedIds = new ArrayList<>(artistIds);
        requestedIds.add(eventPartnerId);
        requestedIds.add(missingUserId);

        Map<UUID, TrustProfile> profiles = queryService.findByArtistIds(requestedIds);

        assertThat(profiles).containsOnlyKeys(artistIds);
        for (UUID artistId : artistIds) {
            JSONAssert.assertEquals(
                insufficientEvidenceProfile(artistId),
                objectMapper.writeValueAsString(profiles.get(artistId)),
                JSONCompareMode.STRICT
            );
        }
    }

    @Test
    void verificationsDoNotChangeReliabilityEvidenceOrRiskSignals() {
        UUID verifiedArtistId = saveArtist(UserStatus.ACTIVE);
        UUID unverifiedArtistId = saveArtist(UserStatus.ACTIVE);
        List<VerificationItem> verifications = List.of(
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE),
            new VerificationItem(VerificationType.COPYRIGHT, null)
        );
        doReturn(Map.of(verifiedArtistId, verifications))
            .when(verificationReader).findVerifiedByArtistIds(any());

        Map<UUID, TrustProfile> profiles = queryService.findByArtistIds(
            List.of(verifiedArtistId, unverifiedArtistId)
        );

        TrustProfile verified = profiles.get(verifiedArtistId);
        TrustProfile unverified = profiles.get(unverifiedArtistId);
        assertThat(verified.verifications()).isEqualTo(verifications);
        assertThat(unverified.verifications()).isEmpty();
        assertThat(verified.reliability()).isEqualTo(unverified.reliability());
        assertThat(verified.evidence()).isEqualTo(unverified.evidence());
        assertThat(verified.riskSignals()).isEqualTo(unverified.riskSignals());
    }

    @Test
    void issuesSameNumberOfStatementsForOneAndThirtyArtists() {
        List<UUID> artistIds = IntStream.range(0, 30)
            .mapToObj(i -> saveArtist(UserStatus.ACTIVE))
            .toList();

        long oneArtistStatements = countStatements(
            () -> assertThat(queryService.findByArtistIds(artistIds.subList(0, 1))).hasSize(1)
        );
        long thirtyArtistStatements = countStatements(
            () -> assertThat(queryService.findByArtistIds(artistIds)).hasSize(30)
        );

        assertThat(oneArtistStatements).isPositive();
        assertThat(thirtyArtistStatements).isEqualTo(oneArtistStatements);
    }

    @Test
    void computesFromLedgerReadingEachSourceOncePerBatch() {
        List<UUID> artistIds = IntStream.range(0, 30)
            .mapToObj(i -> saveArtist(UserStatus.ACTIVE))
            .toList();
        UUID artistWithLedger = artistIds.getFirst();
        doReturn(Map.of(artistWithLedger, fourCompletedPerformances()))
            .when(performanceOutcomeReader).findByArtistIds(any());

        Map<UUID, TrustProfile> profiles = queryService.findByArtistIds(artistIds);

        assertThat(profiles.get(artistWithLedger).reliability())
            .isEqualTo(new Reliability(ConfidenceLevel.MEDIUM, 4, 85));
        assertThat(profiles.get(artistIds.getLast()).reliability())
            .isEqualTo(new Reliability(ConfidenceLevel.LOW, 0, null));
        Set<UUID> requested = Set.copyOf(artistIds);
        verify(performanceOutcomeReader)
            .findByArtistIds(argThat(ids -> Set.copyOf(ids).equals(requested)));
        verify(verificationReader)
            .findVerifiedByArtistIds(argThat(ids -> Set.copyOf(ids).equals(requested)));
        // 위 verify는 인자가 맞는 호출 1회만 본다. 아티스트별 추가 조회가 없는지 출처마다 정확히 1번 읽었음을 확인한다.
        verifyNoMoreInteractions(performanceOutcomeReader, verificationReader);
    }

    @Test
    void returnsEmptyMapWithoutQueryingForEmptyInput() {
        long statements = countStatements(
            () -> assertThat(queryService.findByArtistIds(List.of())).isEmpty()
        );

        assertThat(statements).isZero();
    }

    @Test
    void skipsSourceReadsWhenNoRequestedIdIsArtist() {
        UUID eventPartnerId = saveEventPartner();
        UUID missingUserId = uuidV7Generator.generate();

        Map<UUID, TrustProfile> profiles =
            queryService.findByArtistIds(List.of(eventPartnerId, missingUserId));

        assertThat(profiles).isEmpty();
        verifyNoInteractions(performanceOutcomeReader, verificationReader);
    }

    private long countStatements(Runnable query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            query.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private static List<PerformanceOutcome> fourCompletedPerformances() {
        return IntStream.rangeClosed(1, 4)
            .mapToObj(day -> new PerformanceOutcome(
                UUID.randomUUID(), 1, OutcomeType.COMPLETED, null,
                Instant.parse("2026-09-0%dT10:00:00Z".formatted(day)), null
            ))
            .toList();
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
}
