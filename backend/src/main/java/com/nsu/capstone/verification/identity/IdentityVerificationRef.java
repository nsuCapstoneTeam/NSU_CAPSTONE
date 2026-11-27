package com.nsu.capstone.verification.identity;

import java.util.UUID;

/** 어떤 본인확인 기록으로 대조했는지 가리키는 값. 등록정보 일치 기록이 근거 FK로 남긴다. */
public record IdentityVerificationRef(UUID verificationId, IdentitySource source) {}
