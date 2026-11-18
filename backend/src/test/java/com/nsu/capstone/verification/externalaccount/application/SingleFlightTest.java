package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SingleFlightTest {

    private final SingleFlight singleFlight = new SingleFlight();

    @Test
    void 같은_키로_동시에_온_열_개_호출은_로더를_한_번만_부르고_같은_결과를_받는다() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<Future<String>> results = new ArrayList<>();
        results.add(pool.submit(() -> singleFlight.run("key", () -> {
            loads.incrementAndGet();
            loaderStarted.countDown();
            await(releaseLoader);
            return "value";
        })));
        loaderStarted.await();
        for (int i = 0; i < 9; i++) {
            results.add(pool.submit(() -> singleFlight.run("key", () -> {
                loads.incrementAndGet();
                return "other";
            })));
        }
        Thread.sleep(200);
        releaseLoader.countDown();

        for (Future<String> result : results) {
            assertThat(result.get()).isEqualTo("value");
        }
        pool.shutdown();
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void 끝난_뒤의_호출은_결과를_재사용하지_않고_다시_실행한다() {
        AtomicInteger loads = new AtomicInteger();

        singleFlight.run("key", loads::incrementAndGet);
        singleFlight.run("key", loads::incrementAndGet);

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void 로더가_실패하면_기다린_호출에도_같은_실패가_전해지고_다음_호출은_다시_시도한다() throws Exception {
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<String> first = pool.submit(() -> singleFlight.<String>run("key", () -> {
            loaderStarted.countDown();
            await(releaseLoader);
            throw new IllegalStateException("플랫폼 장애");
        }));
        loaderStarted.await();
        Future<String> waiter = pool.submit(() -> singleFlight.run("key", () -> "unused"));
        Thread.sleep(200);
        releaseLoader.countDown();

        assertThatThrownBy(first::get).hasRootCauseMessage("플랫폼 장애");
        assertThatThrownBy(waiter::get).hasRootCauseMessage("플랫폼 장애");
        pool.shutdown();
        assertThat(singleFlight.run("key", () -> "회복")).isEqualTo("회복");
    }

    @Test
    void 다른_키는_서로_기다리지_않는다() throws Exception {
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch aStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> a = pool.submit(() -> singleFlight.run("a", () -> {
            aStarted.countDown();
            await(releaseA);
            return "A";
        }));
        aStarted.await();

        assertThat(singleFlight.run("b", () -> "B")).isEqualTo("B");

        releaseA.countDown();
        assertThat(a.get()).isEqualTo("A");
        pool.shutdown();
    }

    /** backend-verify 지적: RuntimeException만 잡으면 Error 때 기다리던 호출이 영구 대기한다. */
    @Test
    void 실행이_Error로_끝나도_기다리던_호출은_영구_대기하지_않고_같은_Error를_받는다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> owner = pool.submit(() -> singleFlight.run("k", () -> {
            started.countDown();
            await(release);
            throw new AssertionError("로더가 Error를 던졌다");
        }));
        started.await();
        Future<String> waiter = pool.submit(() -> singleFlight.run("k", () -> "필요 없음"));
        Thread.sleep(200); // 기다리는 호출이 진행 중인 실행에 붙을 시간
        release.countDown();

        try {
            assertThat(waiter.get(3, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
        } catch (java.util.concurrent.ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(Throwable.class);
        }
        // 소유자는 Error를 그대로 받는다
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> owner.get(3, java.util.concurrent.TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(AssertionError.class);
        pool.shutdownNow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
