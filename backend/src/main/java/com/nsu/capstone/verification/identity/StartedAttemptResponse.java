package com.nsu.capstone.verification.identity;

/** 시도 시작 응답(계약 07 §2.2). 앞의 세 값은 화면이 그대로 PortOne SDK에 넘긴다. */
record StartedAttemptResponse(String identityVerificationId, String storeId, String channelKey, String expiresAt) {

    static StartedAttemptResponse from(StartedAttempt attempt) {
        return new StartedAttemptResponse(
                attempt.identityVerificationId(),
                attempt.storeId(),
                attempt.channelKey(),
                ResponseTimestamp.of(attempt.expiresAt()));
    }
}
