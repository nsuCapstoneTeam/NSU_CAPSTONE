package com.nsu.capstone.identity.verification.config;

import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.*;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SignupSendRedisProperties.class)
public class SignupSendRedisConfig {
    @Bean(destroyMethod = "close")
    SignupSendPreflight signupSendPreflight(
            LettuceConnectionFactory sharedFactory,
            SignupSessionProperties sessions,
            SignupVerificationProperties verification,
            VerificationTimingProperties timing,
            VerificationAlertService alerts,
            SignupSendRedisProperties limits) {
        var shared = sharedFactory.getClientConfiguration();
        var options = shared.getClientOptions()
                .orElseGet(ClientOptions::create)
                .mutate()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(limits.connectTimeout())
                        .build())
                .timeoutOptions(TimeoutOptions.enabled(limits.commandTimeout()))
                .requestQueueSize(32)
                .build();
        var builder = LettuceClientConfiguration.builder()
                .commandTimeout(limits.commandTimeout())
                .shutdownTimeout(java.time.Duration.ZERO)
                .clientOptions(options);
        shared.getClientResources().ifPresent(builder::clientResources);
        if (shared.isUseSsl()) {
            var ssl = builder.useSsl();
            if (!shared.isVerifyPeer()) ssl.disablePeerVerification();
            if (shared.isStartTls()) ssl.startTls();
        }
        var factory = new LettuceConnectionFactory(sharedFactory.getStandaloneConfiguration(), builder.build());
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        return new SignupSendPreflight(
                new RedisSignupSessionStore(redis, sessions),
                new RedisVerificationChallengeStore(redis, verification, timing, alerts),
                limits,
                factory::destroy);
    }
}
