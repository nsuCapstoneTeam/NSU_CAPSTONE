package com.nsu.capstone.recommendation.eligibility.domain;

/**
 * 필수 조건(SSOT AI-MATCH-051). 선언 순서가 API 02 §2.3의 {@code passedConditions} 순서다.
 */
public enum EligibilityCondition {
    ACCOUNT_ACTIVE,
    COPYRIGHT_SATISFIED,
    SCHEDULE_AVAILABLE,
    REGION_REACHABLE,
    BUDGET_WITHIN_RANGE,
    PERFORMANCE_TYPE_MATCHED
}
