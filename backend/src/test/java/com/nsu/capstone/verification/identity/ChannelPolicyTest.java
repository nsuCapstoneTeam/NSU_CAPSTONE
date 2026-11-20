package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelPolicyTest {

    private final ChannelPolicy policy = new ChannelPolicy("kcp", "AO6R1", "channel-key-1");

    @Test
    void key가_없는_TEST_채널은_받는다() {
        assertThat(policy.reject(new PortOneChannel("TEST", "kcp", "AO6R1", null)))
                .isEmpty();
    }

    @Test
    void key가_있고_설정과_같은_TEST_채널은_받는다() {
        assertThat(policy.reject(new PortOneChannel("TEST", "kcp", "AO6R1", "channel-key-1")))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("rejectedChannels")
    void 설정한_테스트_채널이_아니면_이유와_함께_거절한다(String row, PortOneChannel channel, RejectionReason reason) {
        assertThat(policy.reject(channel)).contains(reason);
    }

    static Stream<Arguments> rejectedChannels() {
        return Stream.of(
                row("channel 없음", null, RejectionReason.CHANNEL_MISSING),
                row("type 없음", new PortOneChannel(null, "kcp", "AO6R1", null), RejectionReason.CHANNEL_INCOMPLETE),
                row("type 빈 값", new PortOneChannel("", "kcp", "AO6R1", null), RejectionReason.CHANNEL_INCOMPLETE),
                row(
                        "pgProvider 없음",
                        new PortOneChannel("TEST", null, "AO6R1", null),
                        RejectionReason.CHANNEL_INCOMPLETE),
                row(
                        "pgProvider 공백",
                        new PortOneChannel("TEST", "  ", "AO6R1", null),
                        RejectionReason.CHANNEL_INCOMPLETE),
                row(
                        "pgMerchantId 없음",
                        new PortOneChannel("TEST", "kcp", null, null),
                        RejectionReason.CHANNEL_INCOMPLETE),
                row(
                        "pgMerchantId 빈 값",
                        new PortOneChannel("TEST", "kcp", "", null),
                        RejectionReason.CHANNEL_INCOMPLETE),
                row("LIVE 채널", new PortOneChannel("LIVE", "kcp", "AO6R1", null), RejectionReason.CHANNEL_NOT_TEST),
                row("모르는 type", new PortOneChannel("SANDBOX", "kcp", "AO6R1", null), RejectionReason.CHANNEL_NOT_TEST),
                row("소문자 type", new PortOneChannel("test", "kcp", "AO6R1", null), RejectionReason.CHANNEL_NOT_TEST),
                row(
                        "LIVE이면서 값도 다름",
                        new PortOneChannel("LIVE", "other", "OTHER", "other-key"),
                        RejectionReason.CHANNEL_NOT_TEST),
                row(
                        "pgProvider 다름",
                        new PortOneChannel("TEST", "other", "AO6R1", null),
                        RejectionReason.PG_PROVIDER_MISMATCH),
                row(
                        "pgMerchantId 다름",
                        new PortOneChannel("TEST", "kcp", "OTHER", null),
                        RejectionReason.PG_MERCHANT_ID_MISMATCH),
                row(
                        "key 다름",
                        new PortOneChannel("TEST", "kcp", "AO6R1", "other-key"),
                        RejectionReason.CHANNEL_KEY_MISMATCH),
                row("key가 빈 값", new PortOneChannel("TEST", "kcp", "AO6R1", ""), RejectionReason.CHANNEL_KEY_MISMATCH));
    }

    private static Arguments row(String name, PortOneChannel channel, RejectionReason reason) {
        return Arguments.of(name, channel, reason);
    }
}
