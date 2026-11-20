package com.nsu.capstone.verification.identity;

/** 인증 시도의 상태. 만료는 저장하지 않고 expires_at과 서버 시계로 판정한다. */
enum AttemptStatus {
    PENDING,
    VERIFIED,
    FAILED,
    REJECTED
}
