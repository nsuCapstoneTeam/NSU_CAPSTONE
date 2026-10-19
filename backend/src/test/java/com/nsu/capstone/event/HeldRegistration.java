package com.nsu.capstone.event;

import com.nsu.capstone.event.application.EventRegistrationService;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 등록 트랜잭션을 커밋하지 않고 붙잡아 두는 테스트 도구. 행사를 INSERT한 뒤 release()를 부를 때까지
 * 커밋하지 않아서, 같은 키의 다른 요청이 커밋 전 행과 부딪히는 상황을 만든다.
 */
final class HeldRegistration implements AutoCloseable {

    private final CountDownLatch inserted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CompletableFuture<Event> result;

    HeldRegistration(
        PlatformTransactionManager transactionManager,
        EventRegistrationService registrationService,
        UUID eventPartnerId,
        UUID idempotencyKey,
        EventContent content
    ) throws InterruptedException {
        this.result = CompletableFuture.supplyAsync(() ->
            new TransactionTemplate(transactionManager).execute(status -> {
                Event event = registrationService.register(eventPartnerId, idempotencyKey, content);
                inserted.countDown();
                awaitRelease();
                return event;
            }));
        if (!inserted.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("붙잡아 둔 등록이 행사를 저장하지 못했다");
        }
    }

    private void awaitRelease() {
        try {
            release.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 커밋하게 하고 저장된 행사를 돌려준다. */
    Event release() {
        release.countDown();
        return result.orTimeout(10, TimeUnit.SECONDS).join();
    }

    /** 커밋(또는 롤백)이 끝날 때까지 기다린다. 다음 테스트의 정리가 미커밋 행을 놓치지 않게 하기 위해서다. */
    @Override
    public void close() {
        release.countDown();
        try {
            result.orTimeout(10, TimeUnit.SECONDS).join();
        } catch (RuntimeException ignored) {
            // 등록 자체가 실패했어도 트랜잭션은 끝났다.
        }
    }
}
