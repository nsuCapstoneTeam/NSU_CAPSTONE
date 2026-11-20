package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.logging.LogCapture;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * ADR logging/00 Decision 8, S5: 표시 정보는 요청 스레드가 넘긴 작업을 가상 스레드에서 병렬로 받는다. 그 스레드에서 남기는 WARN 줄에도 요청
 * ID가 있어야 한다(실행기가 요청 스레드의 MDC를 작업 스레드로 옮겨야 한다).
 */
class ExternalAccountDisplayRequestIdIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    Environment environment;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 병렬로_받다_실패한_계정의_WARN_줄에_그_요청의_요청_ID가_있다() {
        User artist = newUser(UserRole.ARTIST, "yt-display-request-id@example.com");
        existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"));
        try (LogCapture capture = LogCapture.start(environment)) {
            MDC.put("requestId", "req-for-display");

            service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());

            // linksOf는 작업이 모두 끝나야 돌아오므로 기다릴 필요가 없다
            List<String> lines = capture.lines().stream()
                    .filter(line -> line.contains("YouTube 표시 정보를 받지 못했다"))
                    .toList();
            assertThat(lines).as("병렬 작업이 남긴 WARN 줄").isNotEmpty();
            assertThat(lines).allMatch(line -> line.contains("\"requestId\":\"req-for-display\""));
        }
    }
}
