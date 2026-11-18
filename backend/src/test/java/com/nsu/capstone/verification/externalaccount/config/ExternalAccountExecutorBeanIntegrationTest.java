package com.nsu.capstone.verification.externalaccount.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * ADR "외부 호출": Spring Boot는 {@code Executor} bean이 하나라도 있으면 기본 실행기 {@code applicationTaskExecutor}를
 * 만들지 않는다. 표시 정보용 병렬 실행기가 그 자리를 밀어내면 다른 모듈의 {@code @Async}·MVC 비동기 처리가 조용히 바뀐다.
 */
class ExternalAccountExecutorBeanIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void 외부_계정_실행기가_있어도_Boot의_기본_실행기_applicationTaskExecutor가_그대로_만들어진다() {
        assertThat(context.getBean("externalAccountDisplayExecutor")).isInstanceOf(ExecutorService.class);
        assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
    }
}
