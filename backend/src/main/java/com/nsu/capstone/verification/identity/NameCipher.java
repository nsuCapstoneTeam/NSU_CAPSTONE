package com.nsu.capstone.verification.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MOCK 기록의 실명을 AES-256-GCM으로 암호화한다. 연관 데이터(AAD)는 user_id라서 행을 다른 회원에 옮겨 붙이면 복호화가 실패한다.
 *
 * <p>저장 형식: {@code 0x01(형식 버전) ‖ nonce 12바이트 ‖ 암호문 ‖ tag 16바이트}.
 */
final class NameCipher {

    private static final byte VERSION = 0x01;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int TAG_BITS = TAG_BYTES * 8;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    NameCipher(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("이름 암호화 키는 32바이트여야 한다");
        }
        this.key = new SecretKeySpec(key, "AES");
    }

    /** 설정 값(Base64)에서 키를 만든다. 잘못된 값이어도 메시지와 원인에 그 값을 담지 않는다. */
    static NameCipher fromBase64(String encodedKey) {
        try {
            return new NameCipher(Base64.getDecoder().decode(encodedKey));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("이름 암호화 키는 Base64로 인코딩한 32바이트여야 한다");
        }
    }

    byte[] encrypt(IdentityName name, UUID userId) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(userId));
            byte[] sealed = cipher.doFinal(name.value().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length)
                    .put(VERSION)
                    .put(nonce)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("실명을 암호화할 수 없다");
        }
    }

    IdentityName decrypt(byte[] stored, UUID userId) {
        if (stored == null || stored.length < 1 + NONCE_BYTES + TAG_BYTES || stored[0] != VERSION) {
            throw new IllegalStateException("실명을 복호화할 수 없다");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, NONCE_BYTES));
            cipher.updateAAD(associatedData(userId));
            byte[] plain = cipher.doFinal(stored, 1 + NONCE_BYTES, stored.length - 1 - NONCE_BYTES);
            return IdentityName.of(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("실명을 복호화할 수 없다");
        }
    }

    private static byte[] associatedData(UUID userId) {
        return ByteBuffer.allocate(16)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .array();
    }
}
