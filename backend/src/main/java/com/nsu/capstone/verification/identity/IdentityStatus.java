package com.nsu.capstone.verification.identity;

import java.time.Instant;

/** 본인확인 상태(계약 08 §2.1). isVerified = false ⇔ verifiedAt = null. 출처·이름·시도 ID는 담지 않는다. */
record IdentityStatus(boolean isVerified, Instant verifiedAt) {

    static IdentityStatus notVerified() {
        return new IdentityStatus(false, null);
    }

    static IdentityStatus verifiedAt(Instant verifiedAt) {
        return new IdentityStatus(true, verifiedAt);
    }
}
