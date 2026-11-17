package com.nsu.capstone.verification.externalaccount.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 계정 모듈 설정. 공유 설정 파일을 고치지 않고 모듈 설정 파일을 직접 읽는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExternalAccountProperties.class)
@PropertySource("classpath:config/external-account.properties")
public class ExternalAccountConfig {

    public static final String WRITE_REST_CLIENT = "externalAccountWriteRestClientBuilder";
    public static final String READ_REST_CLIENT = "externalAccountReadRestClientBuilder";

    /** 연결·존재 확인·후보처럼 결과가 저장이나 오류로 이어지는 호출: 연결 3초, 응답 5초, 재시도 없음. */
    @Bean(WRITE_REST_CLIENT)
    @Qualifier(WRITE_REST_CLIENT)
    RestClient.Builder externalAccountWriteRestClientBuilder(ExternalAccountProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(
                        properties.http().writeConnectTimeout(),
                        properties.http().writeReadTimeout()));
    }

    /** 표시 정보처럼 실패해도 링크만 남기면 되는 호출: 연결 1초, 응답 2초. */
    @Bean(READ_REST_CLIENT)
    @Qualifier(READ_REST_CLIENT)
    RestClient.Builder externalAccountReadRestClientBuilder(ExternalAccountProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(
                        properties.http().readConnectTimeout(),
                        properties.http().readReadTimeout()));
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
