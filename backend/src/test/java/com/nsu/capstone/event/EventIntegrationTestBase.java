package com.nsu.capstone.event;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, EventIntegrationTestBase.MutableClockConfig.class})
public abstract class EventIntegrationTestBase {

    /** 테스트의 '지금' 기본값: 2026-10-18 10:00 (Asia/Seoul) */
    protected static final Instant NOW = Instant.parse("2026-10-18T01:00:00Z");

    /** 시각을 옮길 수 있는 테스트용 시계. 토큰 발급·검증도 같은 시계를 쓴다. */
    public static class MutableClock extends Clock {
        private volatile Instant instant = NOW;

        public void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class MutableClockConfig {
        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired protected MutableClock clock;

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UuidV7Generator uuidV7Generator;
    @Autowired protected JwtAccessTokenService jwtAccessTokenService;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected User partner;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        userRepository.deleteAll();
        partner = newUser(UserRole.EVENT_PARTNER, "partner@example.com");
    }

    protected User newUser(UserRole role, String email) {
        UUID id = uuidV7Generator.generate();
        return userRepository.saveAndFlush(role == UserRole.ARTIST
            ? User.createArtist(id, email, "hash", "01011112222")
            : User.createEventPartner(id, email, "hash", "01011112222"));
    }

    protected String bearer(User user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getId(), user.getRole()).value();
    }

    /** 03 §10.1의 예시 본문. 시작은 지금(10-18 10:00)보다 뒤다. */
    protected Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "2026 가을 축제 1일차 밴드 공연");
        body.put("eventType", "UNIVERSITY_FESTIVAL");
        body.put("startDate", "2026-11-06");
        body.put("startTime", "18:00");
        body.put("endDate", "2026-11-06");
        body.put("endTime", "19:00");
        body.put("performanceMinutes", 40);
        body.put("venue", "대운동장 특설 무대");
        body.put("regionCode", "11680");
        body.put("artistType", "BAND");
        body.put("genres", List.of("ROCK", "POP"));
        body.put("budgetMin", 300000);
        body.put("budgetMax", 500000);
        body.put("expectedAudience", 800);
        body.put("description", "축제 첫날 저녁 무대를 여는 밴드 공연이에요.");
        body.put("visibility", "PUBLIC");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rehearsal", "공연 2시간 전 30분");
        details.put("stageSize", "8m × 6m");
        details.put("isPaProvided", true);
        details.put("microphones", "무선 2개, 유선 4개");
        details.put("instruments", "드럼 세트, 베이스 앰프");
        details.put("isWaitingRoomProvided", true);
        details.put("isMealProvided", true);
        details.put("isTransportationFeeProvided", false);
        details.put("isAccommodationProvided", null);
        details.put("otherConditions", null);
        body.put("details", details);
        return body;
    }

    /** 요청마다 새 멱등 키를 붙여 등록한다. */
    protected ResultActions register(User caller, Map<String, Object> body) throws Exception {
        return register(caller, body, UUID.randomUUID().toString());
    }

    protected ResultActions register(User caller, Map<String, Object> body, String idempotencyKey)
        throws Exception {
        return mockMvc.perform(post("/api/v1/events")
            .header("Authorization", bearer(caller))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    /** 멱등 키 헤더 없이 등록한다. */
    protected ResultActions registerWithoutKey(User caller, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
            .header("Authorization", bearer(caller))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    protected String registeredId(Map<String, Object> body) throws Exception {
        String json = register(partner, body).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asString();
    }
}
