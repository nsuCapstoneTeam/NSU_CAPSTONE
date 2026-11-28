package com.nsu.capstone.global.requestlimit;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 제출된 작업 수와 지금 도는 작업 수를 센다. */
final class CountingExecutor extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();

    CountingExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    int submitted() {
        return submitted.get();
    }

    int active() {
        return active.get();
    }

    @Override
    public void execute(Runnable command) {
        submitted.incrementAndGet();
        active.incrementAndGet();
        try {
            delegate.execute(() -> {
                try {
                    command.run();
                } finally {
                    active.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {
            active.decrementAndGet();
            throw e;
        }
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
