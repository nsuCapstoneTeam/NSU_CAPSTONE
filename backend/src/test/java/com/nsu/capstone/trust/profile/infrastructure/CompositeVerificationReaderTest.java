package com.nsu.capstone.trust.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.trust.profile.application.VerificationSource;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 01 §7 합치는 규칙. 출처 도메인(IDENTITY, COPYRIGHT)이 아직 없어도 가짜 source로 시험한다. */
class CompositeVerificationReaderTest {

    private static final VerificationItem IDENTITY = new VerificationItem(VerificationType.IDENTITY, null);
    private static final VerificationItem YOUTUBE =
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE);
    private static final VerificationItem SOUNDCLOUD =
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.SOUNDCLOUD);
    private static final VerificationItem COPYRIGHT = new VerificationItem(VerificationType.COPYRIGHT, null);

    private final UUID artist = UUID.randomUUID();

    @Test
    void source마다_한_번만_부르고_쿼리_수가_아티스트_수와_관계없다() {
        CountingSource identity = new CountingSource(Map.of());
        CountingSource external = new CountingSource(Map.of());
        CompositeVerificationReader reader = new CompositeVerificationReader(List.of(identity, external));

        reader.findVerifiedByArtistIds(List.of(artist));
        assertThat(identity.calls).isEqualTo(1);
        assertThat(external.calls).isEqualTo(1);

        List<UUID> hundred =
                IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID()).toList();
        reader.findVerifiedByArtistIds(hundred);
        assertThat(identity.calls).isEqualTo(2);
        assertThat(external.calls).isEqualTo(2);
        assertThat(identity.lastIds).hasSize(100);
    }

    @Test
    void 합친_뒤_IDENTITY_EXTERNAL_ACCOUNT_COPYRIGHT_순서이고_EXTERNAL_ACCOUNT끼리는_provider_사전순이다() {
        // source들이 뒤섞인 순서로 준다
        VerificationSource copyright = source(Map.of(artist, List.of(COPYRIGHT)));
        VerificationSource external = source(Map.of(artist, List.of(YOUTUBE, SOUNDCLOUD)));
        VerificationSource identity = source(Map.of(artist, List.of(IDENTITY)));

        Map<UUID, List<VerificationItem>> merged = new CompositeVerificationReader(
                        List.of(copyright, external, identity))
                .findVerifiedByArtistIds(List.of(artist));

        assertThat(merged.get(artist)).containsExactly(IDENTITY, SOUNDCLOUD, YOUTUBE, COPYRIGHT);
    }

    @Test
    void 같은_type_provider는_하나만_남는다() {
        VerificationSource first = source(Map.of(artist, List.of(YOUTUBE, IDENTITY)));
        VerificationSource second = source(Map.of(artist, List.of(YOUTUBE, YOUTUBE)));

        Map<UUID, List<VerificationItem>> merged =
                new CompositeVerificationReader(List.of(first, second)).findVerifiedByArtistIds(List.of(artist));

        assertThat(merged.get(artist)).containsExactly(IDENTITY, YOUTUBE);
    }

    @Test
    void IDENTITY가_없는_아티스트의_COPYRIGHT는_버리고_아티스트_ID를_담아_경고_로그를_남긴다() {
        UUID withoutIdentity = UUID.randomUUID();
        UUID withIdentity = UUID.randomUUID();
        VerificationSource source = source(Map.of(
                withoutIdentity, List.of(COPYRIGHT, YOUTUBE),
                withIdentity, List.of(IDENTITY, COPYRIGHT)));
        Logger logger = (Logger) LoggerFactory.getLogger(CompositeVerificationReader.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Map<UUID, List<VerificationItem>> merged = new CompositeVerificationReader(List.of(source))
                    .findVerifiedByArtistIds(List.of(withoutIdentity, withIdentity));

            assertThat(merged.get(withoutIdentity)).containsExactly(YOUTUBE);
            assertThat(merged.get(withIdentity)).containsExactly(IDENTITY, COPYRIGHT);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage()).contains(withoutIdentity.toString());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void source_하나가_실패하면_부분_결과_없이_전체가_실패한다() {
        VerificationSource healthy = source(Map.of(artist, List.of(IDENTITY)));
        VerificationSource broken = ids -> {
            throw new IllegalStateException("source 장애");
        };

        assertThatThrownBy(() -> new CompositeVerificationReader(List.of(healthy, broken))
                        .findVerifiedByArtistIds(List.of(artist)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("source 장애");
    }

    @Test
    void source가_없거나_입력이_비면_빈_결과이고_항목이_없는_아티스트는_키가_없다() {
        assertThat(new CompositeVerificationReader(List.of()).findVerifiedByArtistIds(List.of(artist)))
                .isEmpty();
        CountingSource counting = new CountingSource(Map.of());
        assertThat(new CompositeVerificationReader(List.of(counting)).findVerifiedByArtistIds(List.of()))
                .isEmpty();
        assertThat(new CompositeVerificationReader(List.of(counting)).findVerifiedByArtistIds(List.of(artist)))
                .isEmpty();
    }

    private static VerificationSource source(Map<UUID, List<VerificationItem>> items) {
        return ids -> items;
    }

    private static final class CountingSource implements VerificationSource {
        private final Map<UUID, List<VerificationItem>> items;
        int calls;
        List<UUID> lastIds = new ArrayList<>();

        CountingSource(Map<UUID, List<VerificationItem>> items) {
            this.items = items;
        }

        @Override
        public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
            calls++;
            lastIds = new ArrayList<>(artistIds);
            return items;
        }
    }
}
