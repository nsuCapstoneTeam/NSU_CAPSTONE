package com.nsu.capstone.verification.identity;

/** 제공자 조회 결과. 예외 대신 값으로 돌려주어 제공자 응답이나 예외 메시지가 밖으로 새지 않게 한다. */
sealed interface ProviderOutcome {

    /** 받을 수 있는 결과. 이름은 MOCK에만 있다. */
    record Verified(IdentitySource source, IdentityName name) implements ProviderOutcome {}

    /** 제공자는 확인했지만 설정한 테스트 채널의 결과가 아니다. */
    record Rejected(RejectionReason reason) implements ProviderOutcome {}

    /** 제공자에서 아직 끝나지 않았다(창을 닫음, 인증 중). 시도는 열려 있다. */
    record NotCompleted() implements ProviderOutcome {}

    /** 제공자가 실패로 끝냈다. */
    record Failed() implements ProviderOutcome {}

    /** 결과를 알 수 없다. 시도는 열려 있고 같은 ID로 다시 완료할 수 있다. */
    record Unavailable(UnavailableCause cause) implements ProviderOutcome {}
}
