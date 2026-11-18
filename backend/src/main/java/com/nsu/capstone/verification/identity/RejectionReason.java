package com.nsu.capstone.verification.identity;

/** 제공자는 확인했지만 받을 수 없는 이유. 사람을 가리키지 않는 값만 둔다(저장·로그에 남는다). */
enum RejectionReason {
    CHANNEL_MISSING,
    CHANNEL_INCOMPLETE,
    CHANNEL_NOT_TEST,
    PG_PROVIDER_MISMATCH,
    PG_MERCHANT_ID_MISMATCH,
    CHANNEL_KEY_MISMATCH
}
