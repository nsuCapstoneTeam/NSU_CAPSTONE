package com.nsu.capstone.global.requestlimit;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 한도 확인 전용 Redis 연결을 만든다(ADR user_request_limit/00 Decision 6). 공유 연결 설정의 주소·TLS·client resources를 그대로 쓰고,
 * 연결·명령 timeout과 명령 큐만 한도 확인용으로 바꾼다.
 */
final class DedicatedRedis {

    private DedicatedRedis() {}

    /**
     * @param requestQueueSize 명령 큐 크기. 한도 확인 실행기의 최대 크기와 같게 둔다
     */
    static LettuceConnectionFactory factory(
            LettuceConnectionFactory shared, UserRequestLimitProperties limits, int requestQueueSize) {
        LettuceClientConfiguration sharedConfig = shared.getClientConfiguration();
        ClientOptions options = sharedConfig
                .getClientOptions()
                .orElseGet(ClientOptions::create)
                .mutate()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(limits.redisConnectTimeout())
                        .build())
                .timeoutOptions(TimeoutOptions.enabled(limits.redisCommandTimeout()))
                .requestQueueSize(requestQueueSize)
                // 연결이 끊긴 동안 명령을 큐에 쌓지 않고 바로 거절한다. Lettuce는 뒤에서 재연결을 계속한다
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(limits.redisCommandTimeout())
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(options);
        sharedConfig.getClientResources().ifPresent(builder::clientResources);
        if (sharedConfig.isUseSsl()) {
            LettuceClientConfiguration.LettuceSslClientConfigurationBuilder ssl = builder.useSsl();
            if (!sharedConfig.isVerifyPeer()) {
                ssl.disablePeerVerification();
            }
            if (sharedConfig.isStartTls()) {
                ssl.startTls();
            }
        }
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(shared.getStandaloneConfiguration(), builder.build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
