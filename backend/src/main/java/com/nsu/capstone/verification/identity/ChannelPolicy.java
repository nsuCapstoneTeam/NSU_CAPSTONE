package com.nsu.capstone.verification.identity;

import java.util.Optional;

/** 응답 채널이 설정한 테스트 채널일 때만 받는다(ADR identity_verification/00 "출처 결정"). */
record ChannelPolicy(String pgProvider, String pgMerchantId, String channelKey) {

    private static final String TEST_CHANNEL_TYPE = "TEST";

    /** 받을 수 없으면 그 이유를, 받을 수 있으면 빈 값을 돌려준다. key는 응답에 있을 때만 대조한다. */
    Optional<RejectionReason> reject(PortOneChannel channel) {
        if (channel == null) {
            return Optional.of(RejectionReason.CHANNEL_MISSING);
        }
        if (isBlank(channel.type()) || isBlank(channel.pgProvider()) || isBlank(channel.pgMerchantId())) {
            return Optional.of(RejectionReason.CHANNEL_INCOMPLETE);
        }
        if (!TEST_CHANNEL_TYPE.equals(channel.type())) {
            return Optional.of(RejectionReason.CHANNEL_NOT_TEST);
        }
        if (!pgProvider.equals(channel.pgProvider())) {
            return Optional.of(RejectionReason.PG_PROVIDER_MISMATCH);
        }
        if (!pgMerchantId.equals(channel.pgMerchantId())) {
            return Optional.of(RejectionReason.PG_MERCHANT_ID_MISMATCH);
        }
        if (channel.key() != null && !channelKey.equals(channel.key())) {
            return Optional.of(RejectionReason.CHANNEL_KEY_MISMATCH);
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
