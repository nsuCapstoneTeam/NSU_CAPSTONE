package com.nsu.capstone.verification.externalaccount.repository;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한 아티스트의 외부 계정 잠금(ADR 8번)을 현재 트랜잭션에서 잡았다는 증표다.
 * 행을 바꾸는 저장소 메서드는 이 값을 받는다. 생성자가 패키지 안에서만 열려 있어 {@link ExternalAccountRepository#lockArtist}
 * 말고는 만들 수 없고, 그래서 잠금 없이 행을 바꾸는 호출은 컴파일되지 않는다.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class LockedArtist {

    private final UUID artistId;

    /** 이 증표가 다른 아티스트의 행을 쓰는 데 쓰이지 않게 한다. */
    void requireOwnerOf(UUID rowArtistId) {
        if (!artistId.equals(rowArtistId)) {
            throw new IllegalArgumentException("다른 아티스트의 잠금으로는 행을 바꿀 수 없다");
        }
    }
}
