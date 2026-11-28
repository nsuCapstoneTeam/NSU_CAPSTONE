package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 요청 여러 건이 한꺼번에 출발하게 하고, 끝난 요청의 결과를 모은다. */
final class SpendBurst implements AutoCloseable {

    static final String ACCEPTED = "ACCEPTED";

    private final ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Future<String>> futures = new ArrayList<>();

    /** accounts가 주는 계정으로 count건이 동시에 출발한다. 끝나기를 기다리지 않는다. */
    static SpendBurst start(BudgetLedger ledger, RequestBudget budget, Supplier<UUID> accounts, int count) {
        SpendBurst burst = new SpendBurst();
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < count; i++) {
            UUID account = accounts.get();
            burst.futures.add(burst.callers.submit(() -> {
                go.await();
                try {
                    ledger.spend(account, budget);
                    return ACCEPTED;
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            }));
        }
        go.countDown();
        return burst;
    }

    /** expected건 이상이 끝날 때까지 최대 5초 기다린다. */
    void awaitDone(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (futures.stream().filter(Future::isDone).count() < expected) {
            assertThat(System.nanoTime()).as("끝난 요청이 %d건 미만이다", expected).isLessThan(deadline);
            Thread.sleep(5);
        }
    }

    /** 지금까지 끝난 요청의 결과. 받으면 {@link #ACCEPTED}, 거절이면 에러 코드 이름이다. */
    List<String> outcomes() throws Exception {
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            if (future.isDone()) {
                outcomes.add(future.get());
            }
        }
        return outcomes;
    }

    @Override
    public void close() {
        callers.shutdownNow();
    }
}
