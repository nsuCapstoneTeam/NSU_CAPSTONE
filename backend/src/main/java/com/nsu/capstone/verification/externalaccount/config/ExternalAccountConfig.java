package com.nsu.capstone.verification.externalaccount.config;

import com.nsu.capstone.global.logging.RequestIdExecutors;
import com.nsu.capstone.verification.externalaccount.presentation.MyExternalAccountsInterceptor;
import com.nsu.capstone.verification.externalaccount.redis.RequestLimiter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 외부 계정 모듈 설정. 공유 설정 파일을 고치지 않고 모듈 설정 파일을 직접 읽는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExternalAccountProperties.class)
@PropertySource("classpath:config/external-account.properties")
public class ExternalAccountConfig {

    public static final String WRITE_REST_CLIENT = "externalAccountWriteRestClientBuilder";
    public static final String READ_REST_CLIENT = "externalAccountReadRestClientBuilder";
    public static final String DISPLAY_EXECUTOR = "externalAccountDisplayExecutor";

    /**
     * 연결·존재 확인·후보처럼 결과가 저장이나 오류로 이어지는 호출: 연결 3초, 응답 5초, 재시도 없음.
     *
     * <p>기본 후보에서 뺀다(NSU-161). {@code RestClient.Builder}가 기본 후보로 하나라도 있으면 Boot 자동 설정의 기본 빌더
     * {@code restClientBuilder}가 빠진다. 그러면 이름 없이 {@code RestClient.Builder}를 주입받는 다른 모듈(SMS·이메일 발송기)이
     * Boot 빌더 대신 이 모듈의 빌더를 조용히 받고, 기본 후보가 쓰기·읽기 둘이면 "expected single matching bean but found 2"로
     * 기동에 실패한다(PR #232 뒤 실제로 난 경우). 받는 쪽은 {@link #WRITE_REST_CLIENT}로 이름을 지정한다.
     */
    @Bean(name = WRITE_REST_CLIENT, defaultCandidate = false)
    @Qualifier(WRITE_REST_CLIENT)
    RestClient.Builder externalAccountWriteRestClientBuilder(ExternalAccountProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(
                        properties.http().writeConnectTimeout(),
                        properties.http().writeReadTimeout()));
    }

    /**
     * 표시 정보처럼 실패해도 링크만 남기면 되는 호출: 연결 1초, 응답 2초. 기본 후보에서 빼는 이유는
     * {@link #WRITE_REST_CLIENT} 빌더와 같다.
     */
    @Bean(name = READ_REST_CLIENT, defaultCandidate = false)
    @Qualifier(READ_REST_CLIENT)
    RestClient.Builder externalAccountReadRestClientBuilder(ExternalAccountProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(
                        properties.http().readConnectTimeout(),
                        properties.http().readReadTimeout()));
    }

    /**
     * {@code /api/v1/me/external-accounts/**}에만 역할·요청 제한 인터셉터를 건다. 조회 API와 콜백은 건너뛴다(06 §1).
     * 설정 클래스 안의 {@code @Bean}이라 {@code @WebMvcTest} 슬라이스에는 읽히지 않는다.
     */
    @Bean
    WebMvcConfigurer externalAccountWebMvcConfigurer(RequestLimiter requestLimiter) {
        MyExternalAccountsInterceptor interceptor = new MyExternalAccountsInterceptor(requestLimiter);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/v1/me/external-accounts/**");
            }
        };
    }

    /**
     * 표시 정보를 계정마다 병렬로 받는 실행기(가상 스레드). 응답 전체 3초 상한 때문에 병렬이 필요하다. 종료 때 Spring이 닫는다.
     *
     * <p>기본 후보에서 뺀다. Spring Boot는 {@code Executor} bean이 하나라도 있으면 기본 실행기
     * {@code applicationTaskExecutor}를 만들지 않으므로, 빼지 않으면 다른 모듈의 {@code @Async}·MVC 비동기 처리가
     * 이 실행기로 조용히 바뀐다. 받는 쪽은 {@link #DISPLAY_EXECUTOR}로 이름을 지정한다.
     */
    @Bean(name = DISPLAY_EXECUTOR, defaultCandidate = false)
    ExecutorService externalAccountDisplayExecutor() {
        return RequestIdExecutors.wrap(Executors.newVirtualThreadPerTaskExecutor());
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
