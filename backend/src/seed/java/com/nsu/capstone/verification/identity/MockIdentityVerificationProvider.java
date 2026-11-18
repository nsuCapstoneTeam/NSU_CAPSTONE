package com.nsu.capstone.verification.identity;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 자동화 테스트와 시드가 코드로만 쓰는 Mock 본인확인 제공자. 서버 jar에는 들어가지 않는다. */
final class MockIdentityVerificationProvider implements IdentityVerificationProvider {

    private final ProviderOutcome defaultOutcome;
    private final ConcurrentMap<IdentityVerificationId, List<ProviderOutcome>> scripts = new ConcurrentHashMap<>();
    private final ConcurrentMap<IdentityVerificationId, AtomicInteger> lookups = new ConcurrentHashMap<>();
    private final AtomicInteger totalLookups = new AtomicInteger();
    private volatile Runnable lookupHook = () -> {};

    private MockIdentityVerificationProvider(ProviderOutcome defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    /** 시드용: 어떤 시도든 MOCK 출처와 이 이름으로 성공한다. */
    static MockIdentityVerificationProvider alwaysVerifiedAs(IdentityName name) {
        return new MockIdentityVerificationProvider(new ProviderOutcome.Verified(IdentitySource.MOCK, name));
    }

    /** 흐름 시험용: 정해 두지 않은 시도는 창이 아직 끝나지 않은 것으로 답한다. */
    static MockIdentityVerificationProvider notCompletedUnlessScripted() {
        return new MockIdentityVerificationProvider(new ProviderOutcome.NotCompleted());
    }

    /** 이 시도의 조회마다 차례로 돌려줄 결과. 마지막 결과는 이후 조회에도 반복된다. */
    void script(IdentityVerificationId id, ProviderOutcome... outcomes) {
        scripts.put(id, List.of(outcomes));
        lookups.remove(id);
    }

    /** 조회가 끝나기 직전에 부를 일. 시험이 제공자 응답 지연만큼 시계를 앞당기는 데 쓴다. */
    void onLookup(Runnable hook) {
        this.lookupHook = hook;
    }

    int lookupCount(IdentityVerificationId id) {
        AtomicInteger count = lookups.get(id);
        return count == null ? 0 : count.get();
    }

    int totalLookups() {
        return totalLookups.get();
    }

    @Override
    public ProviderOutcome lookup(IdentityVerificationId id) {
        totalLookups.incrementAndGet();
        int index = lookups.computeIfAbsent(id, key -> new AtomicInteger()).getAndIncrement();
        List<ProviderOutcome> script = scripts.get(id);
        ProviderOutcome outcome =
                script == null ? defaultOutcome : script.get(Math.min(index, script.size() - 1));
        lookupHook.run();
        return outcome;
    }
}
