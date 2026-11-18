package com.nsu.capstone.global.logging;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

/**
 * 다른 스레드로 일을 넘길 때 호출한 스레드의 MDC(요청 ID 등)를 작업 스레드로 옮기는 실행기 래퍼(ADR logging/00 Decision 8, S5).
 * 우리 코드가 직접 만든 실행기·스레드는 이 래퍼의 인자 안에서 만들어야 정적 검사 S5를 통과한다.
 */
public final class RequestIdExecutors {

    private RequestIdExecutors() {}

    /**
     * @param delegate 감쌀 실행기. 큐 상한, 거절 정책, 스레드 종류는 이 실행기 것을 그대로 쓴다.
     * @return 일을 넘기는 순간(execute, submit, invokeAll, invokeAny) 호출 스레드의 MDC를 복사해 작업 스레드에서 넣고, 작업이 끝나면 작업 전 상태로
     *     되돌리는 실행기. 종료(shutdown, close 등)는 감싼 실행기에 넘긴다.
     */
    public static ExecutorService wrap(ExecutorService delegate) {
        return new MdcPropagatingExecutor(delegate);
    }

    /** submit, invokeAll, invokeAny는 AbstractExecutorService가 모두 execute로 모은다. 그래서 execute 한 곳에서 감싼다. */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class MdcPropagatingExecutor extends AbstractExecutorService {

        private final ExecutorService delegate;

        @Override
        public void execute(Runnable command) {
            // 일을 넘기는 순간의 MDC다. 작업이 실제로 도는 때가 아니다.
            Map<String, String> submitterContext = MDC.getCopyOfContextMap();
            delegate.execute(() -> runWithContext(submitterContext, command));
        }

        private static void runWithContext(Map<String, String> submitterContext, Runnable command) {
            Map<String, String> before = MDC.getCopyOfContextMap();
            try {
                setContext(submitterContext);
                command.run();
            } finally {
                setContext(before);
            }
        }

        /** null이면 비운다(작업 전에 MDC가 비어 있었다). */
        private static void setContext(Map<String, String> context) {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
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

        @Override
        public void close() {
            delegate.close();
        }
    }
}
