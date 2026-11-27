package com.nsu.capstone.verification.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 본인확인 상태 응답(계약 08 §2.1). 두 필드는 항상 나가고, isVerified = false ⇔ verifiedAt = null이다. */
record IdentityStatusResponse(@JsonProperty("isVerified") boolean isVerified, String verifiedAt) {

    static IdentityStatusResponse from(IdentityStatus status) {
        return new IdentityStatusResponse(status.isVerified(), ResponseTimestamp.of(status.verifiedAt()));
    }
}
