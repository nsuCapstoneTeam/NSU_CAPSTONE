package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 원칙 테스트 17: 행을 바꾸는 쓰기는 아티스트 잠금(ADR 8번)을 거친다. */
class ExternalAccountWritesHoldLockTest {

    @Test
    void 저장소의_공개_메서드는_조회와_잠금이_아니면_잠금_증표를_첫_매개변수로_받는다() {
        List<Method> writes = Arrays.stream(ExternalAccountRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.getName().startsWith("find"))
                .filter(method -> !method.getName().equals("lockArtist"))
                .toList();

        assertThat(writes).isNotEmpty();
        assertThat(writes)
                .as("잠금 증표 없이 행을 바꾸는 메서드: %s", writes)
                .allSatisfy(method -> assertThat(method.getParameterTypes()).startsWith(LockedArtist.class));
    }
}
