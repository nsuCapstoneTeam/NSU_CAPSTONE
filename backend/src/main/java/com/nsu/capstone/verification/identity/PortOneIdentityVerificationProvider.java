package com.nsu.capstone.verification.identity;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 본인인증 단건 조회로 결과를 다시 확인한다. 자동으로 다시 부르지 않는다(연결 3초, 읽기 5초).
 *
 * <p>200이 아니면 본문을 읽지 않고, 모든 예외를 메시지·원인 없는 결과 값으로 바꾼다. 로그에는 시도 ID, 이유 enum, 예외 클래스 이름과 첫 스택 프레임만 남긴다.
 */
@Slf4j
class PortOneIdentityVerificationProvider implements IdentityVerificationProvider {

    private final RestClient restClient;
    private final String storeId;
    private final String authorization;
    private final PortOneResultClassifier classifier;

    PortOneIdentityVerificationProvider(
            RestClient restClient, String storeId, String apiSecret, PortOneResultClassifier classifier) {
        this.restClient = restClient;
        this.storeId = storeId;
        this.authorization = "PortOne " + apiSecret;
        this.classifier = classifier;
    }

    static RestClient restClient(
            RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public ProviderOutcome lookup(IdentityVerificationId id) {
        try {
            ProviderOutcome outcome = restClient
                    .get()
                    .uri("/identity-verifications/{id}?storeId={storeId}", id.external(), storeId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        return classifier.classify(status, status == 200 ? readBody(response) : null);
                    });
            report(id, outcome);
            return outcome;
        } catch (RuntimeException exception) {
            UnavailableCause cause = causeOf(exception);
            Throwable innermost = innermost(exception);
            log.warn(
                    "PortOne 본인확인 조회에 실패했다. 시도: {}, 이유: {}, 예외: {}, 위치: {}",
                    id.external(),
                    cause,
                    innermost.getClass().getName(),
                    firstFrame(innermost));
            return new ProviderOutcome.Unavailable(cause);
        }
    }

    /** 200일 때만 읽는다. 읽을 수 없으면 null이고, 읽지 못한 이유나 본문은 남기지 않는다. */
    private static PortOneResponse readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(PortOneResponse.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** 서버 설정 오류로 보이는 결과는 ERROR, 그 밖에 받을 수 없는 결과는 WARN이다. */
    private void report(IdentityVerificationId id, ProviderOutcome outcome) {
        switch (outcome) {
            case ProviderOutcome.Rejected rejected -> {
                if (rejected.reason().isServerMisconfiguration()) {
                    log.error("PortOne 응답 채널이 설정과 다르다. 시도: {}, 이유: {}", id.external(), rejected.reason());
                } else {
                    log.warn("PortOne 응답 채널을 받을 수 없다. 시도: {}, 이유: {}", id.external(), rejected.reason());
                }
            }
            case ProviderOutcome.Unavailable unavailable -> {
                if (unavailable.cause() == UnavailableCause.CREDENTIAL_REJECTED) {
                    log.error("PortOne가 서버의 인증 정보를 거절했다. 시도: {}, 이유: {}", id.external(), unavailable.cause());
                } else {
                    log.warn("PortOne 본인확인 결과를 알 수 없다. 시도: {}, 이유: {}", id.external(), unavailable.cause());
                }
            }
            default -> {}
        }
    }

    private static UnavailableCause causeOf(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return UnavailableCause.TIMEOUT;
            }
        }
        return UnavailableCause.CONNECTION_FAILED;
    }

    private static Throwable innermost(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String firstFrame(Throwable exception) {
        StackTraceElement[] frames = exception.getStackTrace();
        if (frames.length == 0) {
            return "-";
        }
        return frames[0].getClassName() + "." + frames[0].getMethodName() + ":" + frames[0].getLineNumber();
    }
}
