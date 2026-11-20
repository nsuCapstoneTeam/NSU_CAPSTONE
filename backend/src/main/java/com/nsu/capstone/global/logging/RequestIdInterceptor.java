package com.nsu.capstone.global.logging;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** AI 서버 호출에 지금 요청의 ID를 X-Request-Id로 싣는다. NSU-63이 AI client에만 붙인다(ADR logging/00 Decision 10). */
public final class RequestIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        RequestId.current().ifPresent(id -> request.getHeaders().set(RequestId.HEADER, id));
        return execution.execute(request, body);
    }
}
