package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PortOneResultClassifierTest {

    private static final PortOneChannel TEST_CHANNEL = new PortOneChannel("TEST", "kcp", "AO6R1", null);

    private final PortOneResultClassifier classifier =
            new PortOneResultClassifier(new ChannelPolicy("kcp", "AO6R1", "channel-key-1"));

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void PortOne_결과를_계약_표대로_나눈다(String row, int httpStatus, PortOneResponse body, ProviderOutcome expected) {
        assertThat(classifier.classify(httpStatus, body)).isEqualTo(expected);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                row(
                        "200 VERIFIED 설정한 테스트 채널",
                        200,
                        new PortOneResponse("VERIFIED", TEST_CHANNEL),
                        new ProviderOutcome.Verified(IdentitySource.SANDBOX, null)),
                row(
                        "200 VERIFIED channel 없음",
                        200,
                        new PortOneResponse("VERIFIED", null),
                        new ProviderOutcome.Rejected(RejectionReason.CHANNEL_MISSING)),
                row(
                        "200 VERIFIED LIVE 채널",
                        200,
                        new PortOneResponse("VERIFIED", new PortOneChannel("LIVE", "kcp", "AO6R1", null)),
                        new ProviderOutcome.Rejected(RejectionReason.CHANNEL_NOT_TEST)),
                row(
                        "200 VERIFIED pgMerchantId 다름",
                        200,
                        new PortOneResponse("VERIFIED", new PortOneChannel("TEST", "kcp", "OTHER", null)),
                        new ProviderOutcome.Rejected(RejectionReason.PG_MERCHANT_ID_MISMATCH)),
                row("200 READY", 200, new PortOneResponse("READY", null), new ProviderOutcome.NotCompleted()),
                row("404", 404, null, new ProviderOutcome.NotCompleted()),
                row("200 FAILED", 200, new PortOneResponse("FAILED", TEST_CHANNEL), new ProviderOutcome.Failed()),
                row(
                        "200 status 없음",
                        200,
                        new PortOneResponse(null, TEST_CHANNEL),
                        unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row(
                        "200 모르는 status",
                        200,
                        new PortOneResponse("CANCELLED", TEST_CHANNEL),
                        unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row("200 깨진 본문", 200, null, unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row("401", 401, null, unavailable(UnavailableCause.CREDENTIAL_REJECTED)),
                row("403", 403, null, unavailable(UnavailableCause.CREDENTIAL_REJECTED)),
                row("400", 400, null, unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("429", 429, null, unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("500", 500, null, unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("302", 302, null, unavailable(UnavailableCause.UNEXPECTED_STATUS)));
    }

    private static ProviderOutcome unavailable(UnavailableCause cause) {
        return new ProviderOutcome.Unavailable(cause);
    }

    private static Arguments row(String name, int httpStatus, PortOneResponse body, ProviderOutcome expected) {
        return Arguments.of(name, httpStatus, body, expected);
    }
}
