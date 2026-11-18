package com.nsu.capstone.verification.externalaccount.provider;

import java.util.Objects;
import java.util.Set;

/**
 * YouTube ID 확인 호출 한 번의 결과(06 §11.1, ADR 14번). {@code publicIds}는 {@code id}와 {@code privacyStatus: public}이
 * 함께 온 ID라 응답이 온전하든 아니든 믿어도 된다. 응답에 없는 ID를 "없음"으로 볼 수 있는지는 {@link #absenceTrusted()}가
 * 알려 준다.
 *
 * @param publicIds 응답에서 공개로 확인된 ID
 * @param incompleteReason 응답이 온전하지 않은 사유. 온전하면 {@code null}
 */
public record PublicIdCheck(Set<String> publicIds, IncompleteReason incompleteReason) {

    public PublicIdCheck {
        publicIds = Set.copyOf(publicIds);
    }

    public static PublicIdCheck complete(Set<String> publicIds) {
        return new PublicIdCheck(publicIds, null);
    }

    public static PublicIdCheck incomplete(Set<String> publicIds, IncompleteReason reason) {
        return new PublicIdCheck(publicIds, Objects.requireNonNull(reason));
    }

    /** {@code true}면 응답에 없거나 공개가 아닌 ID를 "없음"으로 판정해도 된다. */
    public boolean absenceTrusted() {
        return incompleteReason == null;
    }

    /** 온전하지 않은 응답의 사유 코드. 로그에 그대로 남긴다. */
    public enum IncompleteReason {
        EMPTY_RESPONSE,
        PARTIAL_RESPONSE,
        ITEM_INCOMPLETE,
        TOO_MANY_MISSING
    }
}
