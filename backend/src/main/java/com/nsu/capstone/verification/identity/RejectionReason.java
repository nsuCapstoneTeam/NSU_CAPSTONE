package com.nsu.capstone.verification.identity;

/** 제공자는 확인했지만 받을 수 없는 이유. 사람을 가리키지 않는 값만 둔다(저장·로그에 남는다). */
enum RejectionReason {
    CHANNEL_MISSING,
    CHANNEL_INCOMPLETE,
    CHANNEL_NOT_TEST,
    PG_PROVIDER_MISMATCH,
    PG_MERCHANT_ID_MISMATCH,
    CHANNEL_KEY_MISMATCH;

    /** 응답 채널의 값이 서버 설정과 어긋난 경우다. 시연 설정(채널 값)이 틀렸을 가능성이 커서 ERROR로 알린다. */
    boolean isServerMisconfiguration() {
        return this == PG_PROVIDER_MISMATCH || this == PG_MERCHANT_ID_MISMATCH || this == CHANNEL_KEY_MISMATCH;
    }
}
