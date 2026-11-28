package com.nsu.capstone.global.requestlimit;

import java.time.Duration;

/**
 * GCRA(Generic Cell Rate Algorithm)의 순수 계산. 시각은 모두 epoch 밀리초다.
 *
 * <p>{@code T}({@code intervalMillis})는 1건이 돌아오는 시간, {@code τ}({@code toleranceMillis})는 {@code T × (capacity - 1)}이다.
 * 저장하는 값 하나가 이론상 다음 도착 시각(TAT)이다. Redis의 Lua 스크립트와 같은 결과를 내야 한다.
 */
record BudgetRate(long intervalMillis, long toleranceMillis) {

    static BudgetRate of(int capacity, Duration refillEvery) {
        long interval = refillEvery.toMillis();
        return new BudgetRate(interval, interval * (capacity - 1));
    }

    /**
     * @param storedTat 저장된 TAT. 없으면 {@code null}
     * @param now 지금
     */
    Verdict admit(Long storedTat, long now) {
        long tat = storedTat == null || storedTat < now ? now : storedTat;
        long allowAt = tat - toleranceMillis;
        if (now < allowAt) {
            return new Verdict(false, tat, allowAt - now);
        }
        return new Verdict(true, tat + intervalMillis, 0);
    }

    /** 남은 밀리초를 초로 올린다. 가장 작은 값은 1이다. */
    static long retryAfterSeconds(long waitMillis) {
        return Math.max(1, Math.ceilDiv(waitMillis, 1000));
    }

    /**
     * @param accepted 요청을 받았는가
     * @param nextTat 받았다면 저장할 TAT. 거절이면 저장하지 않으므로 지금 TAT 그대로다
     * @param waitMillis 거절이면 1건을 다시 보낼 수 있을 때까지 남은 밀리초
     */
    record Verdict(boolean accepted, long nextTat, long waitMillis) {}
}
