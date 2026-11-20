package com.nsu.capstone.verification.identity;

import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.support.UuidV7Generator;
import com.nsu.capstone.trust.profile.application.VerificationSource;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.client.RestClient;

/**
 * 실명 본인확인 모듈의 빈을 조립한다. 설정 파일은 이 클래스가 읽는다(application.properties를 고치지 않는다). 컨텍스트의 제공자 빈은 PortOne 구현 하나다.
 */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:config/identity-verification.properties")
@EnableConfigurationProperties(IdentityVerificationProperties.class)
class IdentityVerificationConfig {

    @Bean
    NameCipher nameCipher(IdentityVerificationProperties properties) {
        return NameCipher.fromBase64(properties.nameEncryptionKey());
    }

    /** 공유 RestClient.Builder 빈을 주입받지 않는다. 다른 모듈이 이름 있는 빌더를 여럿 두면 주입이 모호해지고, PortOne 클라이언트가 그 설정을 물려받아서도 안 된다. */
    @Bean
    IdentityVerificationProvider identityVerificationProvider(IdentityVerificationProperties properties) {
        IdentityVerificationProperties.PortOne portone = properties.portone();
        RestClient client = PortOneIdentityVerificationProvider.restClient(
                RestClient.builder(), portone.baseUrl(), portone.connectTimeout(), portone.readTimeout());
        ChannelPolicy channelPolicy = new ChannelPolicy(
                portone.testChannel().pgProvider(), portone.testChannel().pgMerchantId(), portone.channelKey());
        return new PortOneIdentityVerificationProvider(
                client, portone.storeId(), portone.apiSecret(), new PortOneResultClassifier(channelPolicy));
    }

    @Bean
    IdentityVerificationService identityVerificationService(
            IdentityVerificationRepository repository,
            IdentityVerificationProvider provider,
            RedisPublicRequestLimitStore limits,
            NameCipher nameCipher,
            Clock clock,
            UuidV7Generator ids,
            IdentityVerificationProperties properties) {
        IdentityVerificationSettings settings = new IdentityVerificationSettings(
                properties.portone().storeId(),
                properties.portone().channelKey(),
                properties.attemptLifetime(),
                properties.requestLimitPerHour());
        return new IdentityVerificationService(repository, provider, limits, nameCipher, clock, ids, settings);
    }

    /** Trust Profile의 CompositeVerificationReader가 모으는 source 중 IDENTITY를 주는 것(08 §7). */
    @Bean
    VerificationSource identityVerificationSource(IdentityVerificationRepository repository) {
        return new IdentityVerificationSource(repository);
    }

    @Bean
    IdentityVerifications identityVerifications(IdentityVerificationRepository repository, NameCipher nameCipher) {
        return new IdentityVerifications(repository, nameCipher);
    }
}
