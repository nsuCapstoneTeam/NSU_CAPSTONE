package com.nsu.capstone.global.requestlimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 시각을 옮길 수 있는 테스트 시계. */
final class TestClock extends Clock {

    static final long START_MILLIS = 1_800_000_000_000L;

    private volatile Instant instant = Instant.ofEpochMilli(START_MILLIS);

    void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
