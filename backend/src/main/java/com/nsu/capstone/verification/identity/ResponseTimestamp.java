package com.nsu.capstone.verification.identity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 응답 시각 표기(00 §4): Asia/Seoul, 초 단위, 소수 초 없음, 오프셋 +09:00. */
final class ResponseTimestamp {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneId.of("Asia/Seoul"));

    private ResponseTimestamp() {}

    static String of(Instant instant) {
        return instant == null ? null : FORMAT.format(instant);
    }
}
