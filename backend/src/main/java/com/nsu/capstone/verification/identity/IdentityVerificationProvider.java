package com.nsu.capstone.verification.identity;

/** 본인확인 제공자 포트. 운영 코드에는 PortOne 구현만 있고, Mock 구현은 seed 소스 세트에만 있다. */
interface IdentityVerificationProvider {

    /** 제공자에 시도 결과를 다시 묻는다. 어떤 실패도 예외가 아니라 결과 값으로 돌려준다. */
    ProviderOutcome lookup(IdentityVerificationId id);
}
