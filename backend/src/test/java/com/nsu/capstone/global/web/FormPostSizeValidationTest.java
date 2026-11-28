package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("폼 상한 기동 검사")
class FormPostSizeValidationTest {

    private static final String FORM_POST_SIZE = "server.tomcat.max-http-form-post-size=";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(FormPostSizeValidation.class);

    @Test
    void 폼_상한이_필터의_상한보다_크면_기동에_실패한다() {
        contextRunner.withPropertyValues(FORM_POST_SIZE + "2MB").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void 폼_상한이_필터의_상한과_같으면_기동한다() {
        contextRunner
                .withPropertyValues(FORM_POST_SIZE + RequestBodyLimitFilter.MAX_BODY_BYTES + "B")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 폼_상한이_필터의_상한보다_작아도_기동에_실패한다() {
        // 작으면 길이가 상한 안인 정상 폼 요청을 Tomcat이 먼저 413으로 끊는다
        contextRunner.withPropertyValues(FORM_POST_SIZE + "32KB").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void 폼_상한_설정이_없어_Tomcat_기본값이_되어도_기동에_실패한다() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void 실패_메시지에_상한과_현재_값을_바이트로_적는다() {
        contextRunner.withPropertyValues(FORM_POST_SIZE + "2MB").run(context -> {
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("server.tomcat.max-http-form-post-size")
                    .hasMessageContaining(String.valueOf(RequestBodyLimitFilter.MAX_BODY_BYTES))
                    .hasMessageContaining(String.valueOf(2 * 1024 * 1024));
        });
    }
}
