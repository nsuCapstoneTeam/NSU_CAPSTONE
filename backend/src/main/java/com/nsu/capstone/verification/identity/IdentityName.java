package com.nsu.capstone.verification.identity;

import java.text.Normalizer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** 등록부 대조에 쓰는 실명. 유니코드 NFC, 앞뒤 공백 제거, 1~50자, 제어 문자 없음. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdentityName {

    private static final int MAX_LENGTH = 50;

    private final String value;

    public static IdentityName of(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_LENGTH || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return new IdentityName(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdentityName name && value.equals(name.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** 로그·예외 메시지에 실명이 실리지 않게 값을 가린다. 값은 value()로만 꺼낸다. */
    @Override
    public String toString() {
        return "IdentityName[****]";
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("실명 형식이 올바르지 않다");
    }
}
