package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdentityVerificationWiringIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void 컨텍스트의_본인확인_제공자_빈은_PortOne_구현_하나이고_Mock은_없다() {
        Map<String, IdentityVerificationProvider> providers =
                context.getBeansOfType(IdentityVerificationProvider.class);

        assertThat(providers.values()).singleElement().isInstanceOf(PortOneIdentityVerificationProvider.class);
    }

    @Test
    void 다른_모듈이_쓰는_입구와_시작_완료_서비스가_조립된다() {
        assertThat(context.getBean(IdentityVerifications.class)).isNotNull();
        assertThat(context.getBean(IdentityVerificationService.class)).isNotNull();
    }

    @Test
    void 설정_파일은_ADR이_정한_환경_변수_이름만_읽는다() throws IOException {
        Properties file = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config/identity-verification.properties")) {
            assertThat(in).isNotNull();
            file.load(in);
        }

        assertThat(file)
                .containsEntry("identity-verification.provider", "${IDENTITY_VERIFICATION_PROVIDER}")
                .containsEntry("identity-verification.name-encryption-key", "${IDENTITY_NAME_ENCRYPTION_KEY}")
                .containsEntry("identity-verification.portone.api-secret", "${PORTONE_API_SECRET}")
                .containsEntry("identity-verification.portone.store-id", "${PORTONE_STORE_ID}")
                .containsEntry("identity-verification.portone.channel-key", "${PORTONE_CHANNEL_KEY}")
                .containsEntry(
                        "identity-verification.portone.test-channel.pg-provider", "${PORTONE_TEST_CHANNEL_PG_PROVIDER}")
                .containsEntry(
                        "identity-verification.portone.test-channel.pg-merchant-id",
                        "${PORTONE_TEST_CHANNEL_PG_MERCHANT_ID}");
        assertThat(file.stringPropertyNames()).noneMatch(name -> name.startsWith("logging."));
    }
}
