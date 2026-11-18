package com.nsu.capstone.verification.externalaccount.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 같은 키로 동시에 들어온 호출을 한 번의 실행으로 합친다(06 §6.5, §7 "캐시가 빈 동시 요청은 서버 안에서 한 번만 호출").
 * 결과를 저장하지 않는다. 캐시는 Redis가 맡고, 이 클래스는 캐시가 빈 순간의 중복 호출만 막는다.
 */
public class SingleFlight {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    /** 같은 키의 실행이 진행 중이면 그 결과를 기다려 받고, 없으면 {@code loader}를 실행한다. 실패는 기다린 호출에도 전해진다. */
    @SuppressWarnings("unchecked")
    public <T> T run(String key, Supplier<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return (T) join(running);
        }
        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (Throwable e) {
            // Error도 기다리는 호출에 전한다. 그렇지 않으면 이미 붙은 호출이 영구히 기다린다
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private static Object join(CompletableFuture<Object> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }
}
