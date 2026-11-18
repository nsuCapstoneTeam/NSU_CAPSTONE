package com.nsu.capstone.verification.externalaccount.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 원칙 테스트 15: 설정이 어긋나면 앱(스프링 컨텍스트)이 뜨지 않는다. */
class ExternalAccountSettingsStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "external-account.youtube.enabled=true",
                    "external-account.youtube.client-secret=secret",
                    "external-account.youtube.api-key=key",
                    "external-account.youtube.redirect-uri=http://localhost:5173/callback",
                    "external-account.frontend-base-uri=http://localhost:5173",
                    "auth.oauth.google.client-id=111111111111-login.apps.googleusercontent.com");

    @Test
    void 연결용_클라이언트가_로그인과_같은_프로젝트면_컨텍스트가_뜨지_않는다() {
        runner.withPropertyValues("external-account.youtube.client-id=111111111111-link.apps.googleusercontent.com")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage() + rootMessage(context.getStartupFailure()))
                            .contains("프로젝트");
                });
    }

    @Test
    void 프로젝트가_다르면_컨텍스트가_뜬다() {
        runner.withPropertyValues("external-account.youtube.client-id=222222222222-link.apps.googleusercontent.com")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ExternalAccountProperties.class, OAuthProperties.class})
    @Import(ExternalAccountSettingsValidator.class)
    static class TestConfig {}
}
