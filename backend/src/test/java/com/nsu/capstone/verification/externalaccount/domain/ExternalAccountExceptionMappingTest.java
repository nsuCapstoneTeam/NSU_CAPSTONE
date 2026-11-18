package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 06 §10: 사유마다 정해진 HTTP 상태와 같은 이름의 code로 나가고, 429에는 Retry-After가 붙는다. 매핑은 ErrorCode 한 곳에 있다. */
class ExternalAccountExceptionMappingTest {

    private static final Map<ExternalAccountFailure, HttpStatus> EXPECTED_STATUS = new EnumMap<>(Map.ofEntries(
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED, HttpStatus.CONFLICT),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED, HttpStatus.CONFLICT),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE, HttpStatus.BAD_REQUEST),
            Map.entry(ExternalAccountFailure.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ExternalAccountFailure.SPOTIFY_ARTIST_NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_URL_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_ALREADY_LINKED, HttpStatus.CONFLICT),
            Map.entry(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ExternalAccountFailure.EXTERNAL_PROVIDER_DISABLED, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ExternalAccountFailure.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ExternalAccountFailure.RATE_LIMIT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE)));

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 모든_사유는_계약_10절의_표에_있다() {
        // 사유를 더하면 이 표에도 상태를 정해 넣어야 한다
        assertThat(EXPECTED_STATUS.keySet()).containsExactlyInAnyOrder(ExternalAccountFailure.values());
    }

    @Test
    void 사유마다_계약의_HTTP_상태와_같은_이름의_code로_응답한다() {
        for (ExternalAccountFailure failure : ExternalAccountFailure.values()) {
            ResponseEntity<ErrorResponse> response =
                    handler.handleBusinessException(new ExternalAccountException(failure, "서버 로그용 메시지"));

            assertThat(response.getStatusCode()).as(failure.name()).isEqualTo(EXPECTED_STATUS.get(failure));
            assertThat(response.getBody().code()).as(failure.name()).isEqualTo(failure.name());
            assertThat(response.getBody().message())
                    .as(failure.name() + " 안내 문구")
                    .isNotBlank();
        }
    }

    @Test
    void 응답_본문에는_서버_로그용_메시지를_싣지_않는다() {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(new ExternalAccountException(
                ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 하루 예산을 확보하지 못했다"));

        assertThat(response.getBody().message()).doesNotContain("예산");
    }

    @Test
    void 요청_제한을_넘으면_Retry_After에_남은_초를_담는다() {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
                new ExternalAccountException(ExternalAccountFailure.TOO_MANY_REQUESTS, "요청이 많다", 120L));

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("120");
    }

    @Test
    void 다른_사유에는_Retry_After를_붙이지_않는다() {
        ResponseEntity<ErrorResponse> unavailable = handler.handleBusinessException(
                new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "플랫폼 장애"));
        // 06 §10: 잠금 대기 초과는 끝나는 때를 알 수 없어 Retry-After를 붙이지 않는다
        ResponseEntity<ErrorResponse> busy = handler.handleBusinessException(
                new ExternalAccountException(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY, "아티스트 잠금 대기 초과"));

        assertThat(unavailable.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER))
                .isFalse();
        assertThat(busy.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }
}
