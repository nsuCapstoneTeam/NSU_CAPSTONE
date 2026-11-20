package com.nsu.capstone.verification.identity;

import java.time.Instant;

/** 시작 응답에 담을 값. 화면은 앞의 세 값을 그대로 PortOne SDK에 넘긴다(계약 08 §2.2). */
record StartedAttempt(String identityVerificationId, String storeId, String channelKey, Instant expiresAt) {}
