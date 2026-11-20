package com.nsu.capstone.verification.externalaccount.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * NSU-161: 외부 계정 모듈이 {@code RestClient.Builder}를 기본 후보 빈으로 등록하면 Boot 자동 설정의 기본 빌더
 * {@code restClientBuilder}가 {@code @ConditionalOnMissingBean}으로 빠진다. 그러면 이름 없이 {@code RestClient.Builder}를
 * 주입받는 {@code SolapiSmsSender}·{@code ResendEmailSender}가 "expected single matching bean but found 2"로 기동을 막는다.
 * 시연 서버(DEMO)는 두 발송 설정을 켠다.
 */
@SpringBootTest(
        properties = {
            "auth.verification.sms.solapi.enabled=true",
            "auth.verification.sms.solapi.api-key=test-api-key",
            "auth.verification.sms.solapi.api-secret=test-api-secret",
            "auth.verification.sms.solapi.from-number=0212345678",
            "auth.verification.email.resend.enabled=true",
            "auth.verification.email.resend.api-key=test-resend-key",
            "auth.verification.email.resend.from-email=no-reply@example.com"
        })
@Import(TestcontainersConfiguration.class)
class ExternalAccountRestClientBuilderBeanIntegrationTest {

    /** Boot {@code RestClientAutoConfiguration}이 만드는 기본 빌더의 bean 이름. */
    private static final String BOOT_REST_CLIENT_BUILDER = "restClientBuilder";

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void 실제_SMS_이메일_발송을_켜도_컨텍스트가_뜨고_실제_발송기가_만들어진다() {
        assertThat(context.getBean(SmsSender.class)).isInstanceOf(SolapiSmsSender.class);
        assertThat(context.getBean(EmailSender.class)).isInstanceOf(ResendEmailSender.class);
    }

    /** 원칙: 앱 코드는 {@code RestClient.Builder}를 기본 후보로 등록하지 않는다. 이름을 붙여 쓰는 빌더는 기본 후보에서 뺀다. */
    @Test
    void RestClient_Builder의_기본_후보는_Boot_기본_빌더_하나뿐이다() {
        String[] builders = context.getBeanNamesForType(RestClient.Builder.class);
        assertThat(builders)
                .contains(
                        BOOT_REST_CLIENT_BUILDER,
                        ExternalAccountConfig.WRITE_REST_CLIENT,
                        ExternalAccountConfig.READ_REST_CLIENT);

        List<String> defaultCandidates = Arrays.stream(builders)
                .filter(name -> isDefaultCandidate(context.getBeanFactory().getBeanDefinition(name)))
                .toList();
        assertThat(defaultCandidates).containsExactly(BOOT_REST_CLIENT_BUILDER);
    }

    private static boolean isDefaultCandidate(BeanDefinition definition) {
        return !(definition instanceof AbstractBeanDefinition abstractDefinition)
                || abstractDefinition.isDefaultCandidate();
    }
}
