package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * ADR logging/00 Decision 8, S5: 다른 스레드로 넘어가면 MDC가 없어 요청 ID가 끊긴다. 래퍼는 일을 넘기는 순간 호출 스레드의 MDC를 복사해 작업
 * 스레드에서 넣고, 작업 전 상태로 되돌린다.
 */
class RequestIdExecutorsTest {

    /** 감싸지 않은 실행기. 래퍼를 거치지 않은 작업이 같은 스레드를 쓸 때 앞 작업의 MDC가 남았는지 본다. */
    private ExecutorService delegate;

    private ExecutorService executor;

    @BeforeEach
    void createExecutor() {
        // 스레드 하나를 두 실행기가 공유한다. 앞 작업이 MDC를 남기면 뒤 작업에서 보인다
        delegate = Executors.newSingleThreadExecutor();
        executor = RequestIdExecutors.wrap(delegate);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        MDC.clear();
    }

    /** 풀의 스레드는 다음 작업이 다시 쓴다. 앞 요청의 ID가 래퍼를 거치지 않은 뒤 작업 로그에 섞이면 안 된다. */
    @Test
    void 작업이_끝나면_작업_스레드의_MDC가_비어_있다() throws Exception {
        MDC.put("requestId", "req-1");
        executor.submit(() -> null).get(5, TimeUnit.SECONDS);

        // 래퍼를 거치지 않고 같은 스레드를 쓴다. 작업 전에 비어 있었으니 지금도 비어 있어야 한다
        Map<String, String> next = delegate.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS);

        assertThat(next).as("앞 작업의 requestId가 남아 있으면 안 된다").isNull();
    }

    @Test
    void 작업_스레드에_원래_있던_MDC는_작업_뒤_그대로_복원한다() throws Exception {
        // 작업 스레드에 호출 스레드와 무관한 값을 직접 심는다
        delegate.submit(() -> MDC.put("workerOnly", "kept")).get(5, TimeUnit.SECONDS);
        MDC.put("requestId", "req-2");

        Map<String, String> inside = executor.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS);
        Map<String, String> after = delegate.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS);

        assertThat(inside).as("작업 안에서는 호출 스레드의 MDC를 본다").containsEntry("requestId", "req-2");
        assertThat(after)
                .as("작업 뒤에는 작업 스레드에 원래 있던 값으로 돌아간다")
                .containsEntry("workerOnly", "kept")
                .doesNotContainKey("requestId");
    }

    @Test
    void execute_invokeAll_invokeAny도_호출_스레드의_MDC를_넘긴다() throws Exception {
        MDC.put("requestId", "req-3");

        java.util.concurrent.CompletableFuture<String> viaExecute = new java.util.concurrent.CompletableFuture<>();
        executor.execute(() -> viaExecute.complete(MDC.get("requestId")));
        String viaInvokeAll = executor.invokeAll(
                        java.util.List.<java.util.concurrent.Callable<String>>of(() -> MDC.get("requestId")))
                .getFirst()
                .get(5, TimeUnit.SECONDS);
        String viaInvokeAny = executor.invokeAny(
                java.util.List.<java.util.concurrent.Callable<String>>of(() -> MDC.get("requestId")),
                5,
                TimeUnit.SECONDS);

        assertThat(viaExecute.get(5, TimeUnit.SECONDS)).isEqualTo("req-3");
        assertThat(viaInvokeAll).isEqualTo("req-3");
        assertThat(viaInvokeAny).isEqualTo("req-3");
    }

    @Test
    void 종료는_감싼_실행기에_넘긴다() throws Exception {
        executor.shutdown();

        assertThat(delegate.isShutdown()).isTrue();
        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(delegate.isTerminated()).isTrue();
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void 넘긴_작업은_호출_스레드의_MDC_전체를_본다() throws Exception {
        MDC.put("requestId", "req-from-caller");
        MDC.put("eventId", "event-1");

        Map<String, String> seen = executor.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS);

        assertThat(seen).containsEntry("requestId", "req-from-caller").containsEntry("eventId", "event-1");
    }
}
