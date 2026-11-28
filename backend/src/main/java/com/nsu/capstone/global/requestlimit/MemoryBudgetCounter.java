package com.nsu.capstone.global.requestlimit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Redis를 쓸 수 없을 때 이 서버가 계정·한도마다 TAT를 메모리로 세는 곳(ADR user_request_limit/00 Decision 6). Lua와 같은 GCRA를
 * {@link BudgetRate}로 계산한다.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MemoryBudgetCounter {

    record Slot(RequestBudget budget, UUID accountId) {}

    /** 항목이 가득 차 새 계정을 세지 못하고 통과시켰다는 표시. 비교는 참조로 한다. */
    static final BudgetRate.Verdict UNCOUNTED = new BudgetRate.Verdict(true, 0, 0);

    private final Map<Slot, Long> tats = new HashMap<>();
    private final int maxEntries;

    /**
     * 1회를 쓰려 한다. 받으면 TAT를 올리고, 거절하면 아무것도 바꾸지 않는다.
     *
     * @return 판정. 새 계정인데 항목이 상한에 차 있으면 세지 않고 {@link #UNCOUNTED}를 돌려준다
     */
    synchronized BudgetRate.Verdict admit(Slot slot, BudgetRate rate, long now) {
        if (!tats.containsKey(slot) && tats.size() >= maxEntries) {
            // 버킷이 다시 가득 찬 항목(TAT <= 지금)은 항목이 없는 것과 같으므로 먼저 지운다
            // shortcut: 가득 찼을 때 새 계정마다 전체를 훑는다(O(항목 수), 최대 1만). 장애 중 새 계정이 몰릴 때만 돈다. 부하 측정에서 길게 걸리면 다시 본다
            tats.values().removeIf(tat -> tat <= now);
            if (tats.size() >= maxEntries) {
                return UNCOUNTED;
            }
        }
        BudgetRate.Verdict verdict = rate.admit(tats.get(slot), now);
        if (verdict.accepted()) {
            tats.put(slot, verdict.nextTat());
        }
        return verdict;
    }
}
