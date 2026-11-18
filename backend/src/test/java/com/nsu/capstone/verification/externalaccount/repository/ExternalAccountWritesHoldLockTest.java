package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 원칙 테스트 17: 행을 바꾸는 쓰기는 아티스트 잠금(ADR 8번)을 거친다. */
class ExternalAccountWritesHoldLockTest {

    @Test
    void 저장소의_공개_메서드는_조회와_잠금이_아니면_잠금_증표를_첫_매개변수로_받는다() {
        List<Method> writes = Arrays.stream(ExternalAccountRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.getName().startsWith("find")
                        && !method.getName().startsWith("count"))
                .filter(method -> !method.getName().equals("lockArtist"))
                .toList();

        assertThat(writes).isNotEmpty();
        assertThat(writes)
                .as("잠금 증표 없이 행을 바꾸는 메서드: %s", writes)
                .allSatisfy(method -> assertThat(method.getParameterTypes()).startsWith(LockedArtist.class));
    }

    /**
     * PR #232 사람 리뷰(5956360956) 1번: ID 확인 작업은 잠금 밖에서 읽은 행으로 판단하므로, 지우는 쪽이 읽을 때의 확인 시각을
     * 조건으로 받아야 그사이 다시 고르거나 다시 연결한 행을 지우지 않는다.
     */
    @Test
    void ID_확인_작업이_쓰는_삭제_메서드는_읽을_때의_확인_시각을_받는다() {
        List<Method> deletes = Arrays.stream(ExternalAccountRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("deleteWork")
                        || method.getName().equals("deleteAccountOfChannel"))
                .toList();

        assertThat(deletes).hasSize(2);
        assertThat(deletes)
                .as("읽을 때의 확인 시각 없이 지우는 메서드: %s", deletes)
                .allSatisfy(method -> assertThat(method.getParameterTypes()).contains(Instant.class));
    }
}
