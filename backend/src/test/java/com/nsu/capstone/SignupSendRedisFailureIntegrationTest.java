package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.*;
import com.nsu.capstone.identity.verification.port.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;

/** This context owns its Redis container; pausing it cannot affect other test contexts. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupSendRedisFailureIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    @Qualifier("redisContainer")
    GenericContainer<?> redis;

    @MockitoSpyBean
    SignupSendPreflight preflight;

    @MockitoBean
    EmailSender email;

    @MockitoBean
    SmsSender sms;

    @MockitoBean
    VerificationAlertService alerts;

    private String session() {
        String id = UUID.randomUUID().toString();
        sessions.save(SignupSession.createArtist(id, id + "@example.com", "01012345678")
                .withConsent(SignupTestTerms.consent()));
        return id;
    }

    @Test
    void pausedBeforeRequestFailsWithinOneSecondWithoutProviderThenReconnects() throws Exception {
        String id = session();
        // Warm the dedicated connection before simulating a stalled established socket.
        preflight.sessions().findById(id);
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        try {
            assertUnavailable(id, "before-request");
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
        verifyNoInteractions(email, sms);
        // Late replies cannot resume the request or start a provider call.
        assertTrue(preflight.sessions().findById(id).isPresent());
        verifyNoInteractions(email, sms);
        assertHealthySends();
    }

    @Test
    void pausedImmediatelyAfterReservationFailsWithoutProviderAndLateStartCannotSend() throws Exception {
        String id = session();
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
                    Object value = invocation.callRealMethod();
                    if (calls.incrementAndGet() == 2)
                        redis.getDockerClient()
                                .pauseContainerCmd(redis.getContainerId())
                                .exec();
                    return value;
                })
                .when(preflight)
                .await(anyLong(), any());
        try {
            assertUnavailable(id, "after-reservation");
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
            reset(preflight);
        }
        assertTrue(preflight.sessions().findById(id).isPresent());
        verifyNoInteractions(email, sms);
        assertHealthySends();
    }

    private void assertUnavailable(String id, String phase) throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                        .contentType("application/json")
                        .content("{\"signupSessionId\":\"" + id + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VERIFICATION_DELIVERY_FAILED"))
                .andExpect(header().doesNotExist("Retry-After"));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("SIGNUP_REDIS_FAILURE " + phase + " elapsed_ms=" + millis);
        assertTrue(millis < 1000, "Response exceeded 1 second: " + millis);
    }

    @Test
    void healthyAndReconnectedClientsDoNotTimeout() throws Exception {
        assertHealthySends();
    }

    private void assertHealthySends() throws Exception {
        // Force established clients to reconnect, including the dedicated send connection.
        redis.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "normal", "SKIPME", "yes");
        for (int i = 0; i < 3; i++) {
            String id = session();
            mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                            .with(r -> {
                                r.setRemoteAddr("2001:db8:"
                                        + UUID.randomUUID().toString().substring(0, 4) + ":1::1");
                                return r;
                            })
                            .contentType("application/json")
                            .content("{\"signupSessionId\":\"" + id + "\"}"))
                    .andExpect(status().isAccepted());
        }
        verify(email, times(3)).sendVerificationCode(anyString(), anyString(), any());
    }
}
