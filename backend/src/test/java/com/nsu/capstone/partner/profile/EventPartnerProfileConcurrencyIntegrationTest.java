package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EventPartnerProfileConcurrencyIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    private static final int ROUNDS = 20;

    @Test
    void 동시_첫_저장_두_개는_매번_둘_다_200이고_행은_하나이며_값이_섞이지_않는다() throws Exception {
        Map<String, Object> first = validProfile();
        first.put("name", "첫째");
        first.put("organization", "첫째 소속");
        first.put("regionCodes", List.of("11"));
        Map<String, Object> second = validProfile();
        second.put("name", "둘째");
        second.put("organization", "둘째 소속");
        second.put("regionCodes", List.of("44"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                jdbcTemplate.update("DELETE FROM event_partner_profiles");
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> a = pool.submit(putAtSameTime(first, ready, go));
                Future<Integer> b = pool.submit(putAtSameTime(second, ready, go));
                ready.await();
                go.countDown();

                assertThat(a.get()).as("round %d 첫째", round).isEqualTo(200);
                assertThat(b.get()).as("round %d 둘째", round).isEqualTo(200);
                assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM event_partner_profiles", Integer.class)).isEqualTo(1);
                getProfile(partner).andExpect(status().isOk());
                String name = jdbcTemplate.queryForObject(
                    "SELECT name FROM event_partner_profiles", String.class);
                String organization = jdbcTemplate.queryForObject(
                    "SELECT organization FROM event_partner_profiles", String.class);
                String regions = jdbcTemplate.queryForObject(
                    "SELECT array_to_string(region_codes, ',') FROM event_partner_profiles", String.class);
                assertThat(List.of(name, organization, regions))
                    .isIn(List.of("첫째", "첫째 소속", "11"), List.of("둘째", "둘째 소속", "44"));
            }
        } finally {
            // 단언이 실패해도 진행 중인 PUT이 끝난 뒤 정리(@AfterEach)가 돌게 한다.
            // shutdownNow는 진행 중인 요청을 멈추지 못해, 정리 뒤에 커밋된 행이 다른 테스트의 users 삭제를 FK로 막을 수 있다
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void 다른_연결이_먼저_같은_행을_커밋해도_PUT은_200이고_새_값으로_바뀐다() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO event_partner_profiles
                (user_id, name, event_types, region_codes, updated_at)
            VALUES (?, '먼저 들어온 값', ARRAY[]::varchar[], ARRAY[]::varchar[], now())
            """, partner.getId());

        putProfile(partner, validProfile()).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("김담당"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_partner_profiles", Integer.class)).isEqualTo(1);
    }

    private Callable<Integer> putAtSameTime(Map<String, Object> body, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            return putProfile(partner, body).andReturn().getResponse().getStatus();
        };
    }
}
