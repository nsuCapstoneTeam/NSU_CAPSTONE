package com.nsu.capstone.global.logging;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/** 빈이 없는 가벼운 Boot 앱. 설정 파일과 시작 리스너만 거친다. */
final class TestApp {

    private TestApp() {}

    /**
     * OS 환경 변수와 JVM 시스템 속성을 뺀 환경으로 띄운다. 배포와 같은 설정(커밋한 설정 파일 그대로)이 만드는 결과를 보려는 테스트가
     * 개발자 컴퓨터의 LOG_FORMAT 같은 값에 흔들리지 않게 한다.
     */
    static ConfigurableApplicationContext runWithoutOsEnvironment(String... properties) {
        return new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .environment(new StandardEnvironment() {
                    @Override
                    protected void customizePropertySources(MutablePropertySources propertySources) {
                        // 시스템 속성과 환경 변수 소스를 더하지 않는다
                    }
                })
                // Boot는 작업 디렉터리의 ./config/application.properties도 읽는다. 개발자의 파일 설정이 섞이지 않게 클래스패스로 고정한다
                .properties("spring.config.location=classpath:/application.properties")
                .properties(properties)
                .run();
    }

    /** 다른 통합 테스트의 컴포넌트 스캔에 잡히지 않도록 @TestConfiguration으로 둔다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class EmptyConfig {}
}
