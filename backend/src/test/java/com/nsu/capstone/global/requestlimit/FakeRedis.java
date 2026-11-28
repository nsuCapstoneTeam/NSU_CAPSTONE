package com.nsu.capstone.global.requestlimit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis 호출만 흉내 내는 템플릿. 호출 수와 탐침 여부 인자를 기록하고, 실패·대기·거절 답을 정해 둘 수 있다. */
final class FakeRedis extends StringRedisTemplate {

    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> probeFlags = new CopyOnWriteArrayList<>();
    private volatile Supplier<RuntimeException> failure;
    private volatile long answer = -1;
    private volatile CountDownLatch gate;

    void failWith(Supplier<RuntimeException> failure) {
        this.failure = failure;
    }

    void recover() {
        this.failure = null;
    }

    /** 스크립트가 돌려줄 값. -1은 받음, 0 이상은 거절(남은 ms). */
    void answer(long answer) {
        this.answer = answer;
    }

    /** 문이 열릴 때까지 호출이 멈춘다. */
    CountDownLatch holdCalls() {
        CountDownLatch latch = new CountDownLatch(1);
        this.gate = latch;
        return latch;
    }

    /** 멈춘 호출이 남아 있으면 풀어 준다. 테스트가 실패해도 정리가 멈추지 않게 한다. */
    void releaseHeldCalls() {
        CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    int calls() {
        return calls.get();
    }

    List<String> probeFlags() {
        return probeFlags;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        calls.incrementAndGet();
        probeFlags.add((String) args[3]);
        CountDownLatch held = gate;
        if (held != null) {
            try {
                held.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("테스트 대기가 끊겼다", e);
            }
        }
        Supplier<RuntimeException> fail = failure;
        if (fail != null) {
            throw fail.get();
        }
        return (T) Long.valueOf(answer);
    }
}
