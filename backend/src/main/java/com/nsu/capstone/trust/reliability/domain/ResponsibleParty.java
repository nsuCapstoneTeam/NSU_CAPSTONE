package com.nsu.capstone.trust.reliability.domain;

/** 판정으로 확정된 귀책 주체. 판정 전에는 값이 없다. */
public enum ResponsibleParty {
    /** 아티스트 귀책. 이 귀책의 취소·노쇼만 관측으로 계산한다. */
    ARTIST,
    /** EVENT_PARTNER(행사를 등록하고 아티스트를 섭외하는 사용자) 귀책. */
    EVENT_PARTNER,
    /** 아티스트와 EVENT_PARTNER의 공동 귀책. */
    MUTUAL,
    /** 불가항력이나 정당한 사유가 승인된 경우. */
    FORCE_MAJEURE,
    /** 플랫폼 귀책. */
    PLATFORM,
    /** 위에 해당하지 않는 기타 귀책. */
    OTHER
}
