package com.nsu.capstone.trust.reliability.domain;

/** 관측 건수로 정하는 확신 수준(04 D09). */
public enum ConfidenceLevel {
    /** 낮음. 관측이 적어 이행률을 표시하지 않는다. V1은 관측 3건 이하다. */
    LOW,
    /** 보통. 이행률을 표시한다. V1은 관측 4~9건이다. */
    MEDIUM,
    /** 높음. 관측이 충분히 쌓였다. V1은 관측 10건 이상이다. */
    HIGH
}
