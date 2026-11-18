package com.nsu.capstone.verification.identity;

import java.time.Duration;

/** 시작·완료 서비스가 쓰는 값. storeId·channelKey는 브라우저 SDK에 공개되는 값이고 서버가 응답 채널을 대조하는 설정과 같다. */
record IdentityVerificationSettings(
        String storeId, String channelKey, Duration attemptLifetime, int requestLimitPerHour) {}
