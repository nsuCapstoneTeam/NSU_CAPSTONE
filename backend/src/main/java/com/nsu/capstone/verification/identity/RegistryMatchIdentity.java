package com.nsu.capstone.verification.identity;

/** 등록부 대조(04 §3.2)에 넘길 본인확인. 이름이 있는 것은 MOCK뿐이다. */
public sealed interface RegistryMatchIdentity {

    /** 본인확인 기록이 없다. */
    record NotVerified() implements RegistryMatchIdentity {}

    /** SANDBOX 기록이라 이름이 없다. 다시 본인확인을 해도 풀리지 않는다. */
    record NameUnavailable(IdentityVerificationRef ref) implements RegistryMatchIdentity {}

    /** MOCK 기록. 복호화한 이름과 근거 기록을 준다. */
    record Named(IdentityName name, IdentityVerificationRef ref) implements RegistryMatchIdentity {}
}
