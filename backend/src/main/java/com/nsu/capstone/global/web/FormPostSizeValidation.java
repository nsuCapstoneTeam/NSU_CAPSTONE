package com.nsu.capstone.global.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat의 폼 파싱 상한(server.tomcat.max-http-form-post-size)이 필터의 상한과 같은지 기동할 때 검증한다. 폼 POST 본문은 필터의 래퍼를
 * 거치지 않고 Tomcat이 이 값으로 직접 끊으므로(ADR request_body_limit/00 Decision 8), 환경 변수 같은 설정이 값을 바꾸면 chunked 폼 POST의
 * 상한만 조용히 달라진다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TomcatServerProperties.class)
@RequiredArgsConstructor
class FormPostSizeValidation implements InitializingBean {

    private final TomcatServerProperties tomcat;

    @Override
    public void afterPropertiesSet() {
        long formPostSize = tomcat.getMaxHttpFormPostSize().toBytes();
        if (formPostSize != RequestBodyLimitFilter.MAX_BODY_BYTES) {
            throw new IllegalStateException("server.tomcat.max-http-form-post-size는 요청 본문 상한과 같아야 한다 (상한 "
                    + RequestBodyLimitFilter.MAX_BODY_BYTES + "바이트, 현재 " + formPostSize + "바이트)");
        }
    }
}
