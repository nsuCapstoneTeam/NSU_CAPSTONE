package com.nsu.capstone.recommendation.eligibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserAccountRow;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import com.nsu.capstone.music.domain.RightsBasis;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EligibilityFilterIntegrationTest {

    private static final EligibilityInput EVENT = new EligibilityInput(
        LocalDateTime.of(2026, 11, 15, 18, 0), LocalDateTime.of(2026, 11, 15, 22, 0),
        60, "11680", 500_000, 1_000_000, null
    );

    @Autowired EligibilityFilter filter;
    @Autowired UserRepository userRepository;
    @Autowired UuidV7Generator uuidV7Generator;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean ArtistProfileReader profileReader;
    @MockitoSpyBean ArtistScheduleReader scheduleReader;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void readsAccountsFromUsersKeepingOnlyActiveArtists() {
        MusicCandidate active = candidate(saveArtist(UserStatus.ACTIVE));
        MusicCandidate suspended = candidate(saveArtist(UserStatus.SUSPENDED));
        MusicCandidate withdrawn = candidate(saveArtist(UserStatus.WITHDRAWN));
        MusicCandidate eventPartner = candidate(saveEventPartner());
        MusicCandidate unknownUser = candidate(uuidV7Generator.generate());

        List<MusicCandidate> result = filter.filterAccountAndCopyright(
            List.of(active, suspended, withdrawn, eventPartner, unknownUser)
        );

        assertThat(result).containsExactly(active);
    }

    @Test
    void dropsEverySongWhileProfileAndScheduleSourcesAreEmpty() {
        List<MusicCandidate> candidates = IntStream.range(0, 3)
            .mapToObj(i -> candidate(saveArtist(UserStatus.ACTIVE)))
            .toList();

        assertThat(filter.filter(EVENT, candidates)).isEmpty();

        verify(profileReader).findByArtistIds(any());
        verify(scheduleReader).findByArtistIds(any(), any(), any());
    }

    @Test
    void doesNotReadProfileOrScheduleWhenNoArtistIsActive() {
        MusicCandidate suspended = candidate(saveArtist(UserStatus.SUSPENDED));

        assertThat(filter.filter(EVENT, List.of(suspended))).isEmpty();

        verify(profileReader, never()).findByArtistIds(any());
        verify(scheduleReader, never()).findByArtistIds(any(), any(), any());
    }

    @Test
    void readsAccountsWithoutLoadingUserEntities() {
        List<MusicCandidate> candidates = IntStream.range(0, 3)
            .mapToObj(i -> candidate(saveArtist(UserStatus.ACTIVE)))
            .toList();

        long loadedEntities = measure(
            () -> assertThat(filter.filterAccountAndCopyright(candidates)).hasSize(3),
            Statistics::getEntityLoadCount
        );

        assertThat(loadedEntities).isZero();
    }

    @Test
    void accountProjectionCarriesOnlyIdRoleAndStatus() {
        assertThat(UserAccountRow.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .containsExactly("id", "role", "status");
    }

    @Test
    void issuesSameNumberOfStatementsForOneAndHundredArtists() {
        List<MusicCandidate> candidates = IntStream.range(0, 100)
            .mapToObj(i -> candidate(saveArtist(UserStatus.ACTIVE)))
            .toList();

        long oneArtistFilter = countStatements(() -> filter.filter(EVENT, candidates.subList(0, 1)));
        long hundredArtistsFilter = countStatements(() -> filter.filter(EVENT, candidates));
        long oneArtistRecheck = countStatements(
            () -> assertThat(filter.filterAccountAndCopyright(candidates.subList(0, 1))).hasSize(1)
        );
        long hundredArtistsRecheck = countStatements(
            () -> assertThat(filter.filterAccountAndCopyright(candidates)).hasSize(100)
        );

        assertThat(oneArtistFilter).isPositive();
        assertThat(hundredArtistsFilter).isEqualTo(oneArtistFilter);
        assertThat(oneArtistRecheck).isPositive();
        assertThat(hundredArtistsRecheck).isEqualTo(oneArtistRecheck);
    }

    private long countStatements(Runnable query) {
        return measure(query, Statistics::getPrepareStatementCount);
    }

    private long measure(Runnable query, ToLongFunction<Statistics> metric) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            query.run();
            return metric.applyAsLong(statistics);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private static MusicCandidate candidate(UUID artistId) {
        return new MusicCandidate(UUID.randomUUID(), 1L, artistId, RightsBasis.DECLARED, false);
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
