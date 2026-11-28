package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 추천 Eligibility가 쓰는 일괄 조회 {@code ArtistSchedules.findByArtistIds}(11 §5.4). */
class ArtistSchedulesFindIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Autowired
    ArtistSchedules schedules;

    @Test
    void 조회_구간과_겹치는_일정을_아티스트별_시작_순서로_서울_벽시계로_돌려주고_일정이_없는_아티스트는_키가_없다() {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        User none = newUser(UserRole.ARTIST, "none@example.com");
        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 22:00:00+09", "HOLD", null);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", "공개되면 안 되는 이름");
        insert(other, "2026-12-12 19:40:00+09", "2026-12-12 20:10:00+09", "BOOKED", null);

        Map<UUID, List<SavedArtistSchedule>> found = schedules.findByArtistIds(
                List.of(artist.getId(), other.getId(), none.getId()), at(12, 19, 0), at(12, 21, 0));

        assertThat(found.keySet()).containsExactlyInAnyOrder(artist.getId(), other.getId());
        assertThat(found.get(artist.getId()))
                .containsExactly(
                        new SavedArtistSchedule(at(12, 18, 0), at(12, 20, 0), ArtistScheduleStatus.AVAILABLE),
                        new SavedArtistSchedule(at(12, 20, 0), at(12, 22, 0), ArtistScheduleStatus.HOLD));
        assertThat(found.get(other.getId()))
                .containsExactly(new SavedArtistSchedule(at(12, 19, 40), at(12, 20, 10), ArtistScheduleStatus.BOOKED));
    }

    @Test
    void 구간에_맞닿는_일정과_from_24시간_전에_시작한_일정은_조회하고_30분_전에_끝났거나_1분_뒤에_시작하는_일정은_조회하지_않는다() {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        insert(artist, "2026-12-11 19:00:00+09", "2026-12-12 19:00:00+09", "BOOKED", null);
        insert(artist, "2026-12-12 21:00:00+09", "2026-12-12 22:00:00+09", "HOLD", null);
        insert(other, "2026-12-12 18:00:00+09", "2026-12-12 18:30:00+09", "BOOKED", null);
        insert(other, "2026-12-12 21:01:00+09", "2026-12-12 21:30:00+09", "BOOKED", null);

        Map<UUID, List<SavedArtistSchedule>> found =
                schedules.findByArtistIds(List.of(artist.getId(), other.getId()), at(12, 19, 0), at(12, 21, 0));

        assertThat(found.keySet()).containsExactly(artist.getId());
        assertThat(found.get(artist.getId()))
                .extracting(SavedArtistSchedule::status)
                .containsExactly(ArtistScheduleStatus.BOOKED, ArtistScheduleStatus.HOLD);
    }

    @Test
    void 서버_기본_시간대가_UTC나_미국_서부여도_조회_구간과_돌려주는_시각은_서울_벽시계다() {
        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 22:00:00+09", "HOLD", null);
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[] {"UTC", "America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));

                Map<UUID, List<SavedArtistSchedule>> found =
                        schedules.findByArtistIds(List.of(artist.getId()), at(12, 22, 0), at(12, 23, 0));

                assertThat(found.get(artist.getId()))
                        .as(zone)
                        .containsExactly(
                                new SavedArtistSchedule(at(12, 20, 0), at(12, 22, 0), ArtistScheduleStatus.HOLD));
                assertThat(schedules.findByArtistIds(List.of(artist.getId()), at(12, 22, 1), at(12, 23, 0)))
                        .as(zone)
                        .isEmpty();
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void from이_to보다_늦으면_IllegalArgumentException이고_인자나_원소가_null이면_NullPointerException이다() {
        List<UUID> ids = List.of(artist.getId());

        assertThatThrownBy(() -> schedules.findByArtistIds(ids, at(12, 21, 0), at(12, 19, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedules.findByArtistIds(List.of(), at(12, 21, 0), at(12, 19, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedules.findByArtistIds(null, at(12, 19, 0), at(12, 21, 0)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        schedules.findByArtistIds(Arrays.asList(artist.getId(), null), at(12, 19, 0), at(12, 21, 0)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> schedules.findByArtistIds(ids, null, at(12, 21, 0)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> schedules.findByArtistIds(ids, at(12, 19, 0), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 12, day, hour, minute);
    }
}
