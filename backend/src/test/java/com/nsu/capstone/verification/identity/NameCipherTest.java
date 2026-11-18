package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameCipherTest {

    private static final UUID USER_ID = UUID.fromString("01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a");

    private final NameCipher cipher = new NameCipher(new byte[32]);

    @Test
    void 암호화한_이름은_같은_회원으로_복호화하면_돌아온다() {
        IdentityName name = IdentityName.of("김동엽");

        byte[] stored = cipher.encrypt(name, USER_ID);

        assertThat(cipher.decrypt(stored, USER_ID)).isEqualTo(name);
    }

    @Test
    void 저장_형식은_버전_1바이트_nonce_12바이트_암호문_태그_16바이트이고_평문이_보이지_않는다() {
        IdentityName name = IdentityName.of("김동엽");
        byte[] plain = name.value().getBytes(StandardCharsets.UTF_8);

        byte[] stored = cipher.encrypt(name, USER_ID);

        assertThat(stored).hasSize(1 + 12 + plain.length + 16);
        assertThat(stored[0]).isEqualTo((byte) 0x01);
        assertThat(indexOf(stored, plain)).isEqualTo(-1);
    }

    @Test
    void 같은_이름도_암호화할_때마다_nonce가_달라_암호문이_다르다() {
        IdentityName name = IdentityName.of("김동엽");

        byte[] first = cipher.encrypt(name, USER_ID);
        byte[] second = cipher.encrypt(name, USER_ID);

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(first, 1, 13)).isNotEqualTo(Arrays.copyOfRange(second, 1, 13));
    }

    @Test
    void 다른_회원의_ID로는_복호화하지_못한다() {
        byte[] stored = cipher.encrypt(IdentityName.of("김동엽"), USER_ID);

        assertDecryptionFails(stored, UUID.fromString("01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6b"));
    }

    @Test
    void 다른_키로는_복호화하지_못한다() {
        byte[] stored = cipher.encrypt(IdentityName.of("김동엽"), USER_ID);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;

        assertThatThrownBy(() -> new NameCipher(otherKey).decrypt(stored, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("실명을 복호화할 수 없다");
    }

    @Test
    void 바이트가_바뀐_암호문은_복호화하지_못한다() {
        byte[] stored = cipher.encrypt(IdentityName.of("김동엽"), USER_ID);
        stored[stored.length - 1] ^= 1;

        assertDecryptionFails(stored, USER_ID);
    }

    @Test
    void 알_수_없는_형식_버전은_복호화하지_못한다() {
        byte[] stored = cipher.encrypt(IdentityName.of("김동엽"), USER_ID);
        stored[0] = 0x02;

        assertDecryptionFails(stored, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 12, 28})
    void 최소_길이_29바이트보다_짧은_값은_복호화하지_못한다(int length) {
        assertDecryptionFails(new byte[length], USER_ID);
    }

    @Test
    void 키가_32바이트가_아니면_거절하고_메시지에_키를_담지_않는다() {
        assertThatThrownBy(() -> new NameCipher(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름 암호화 키는 32바이트여야 한다");
        assertThatThrownBy(() -> new NameCipher(new byte[33])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Base64_키를_읽고_잘못된_값은_값을_드러내지_않고_거절한다() {
        String valid = Base64.getEncoder().encodeToString(new byte[32]);
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThat(NameCipher.fromBase64(valid)).isNotNull();
        for (String invalid : new String[] {null, "", "   ", "not-base64-@@@", shortKey}) {
            assertThatThrownBy(() -> NameCipher.fromBase64(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이름 암호화 키는 Base64로 인코딩한 32바이트여야 한다");
        }
    }

    private void assertDecryptionFails(byte[] stored, UUID userId) {
        assertThatThrownBy(() -> cipher.decrypt(stored, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("실명을 복호화할 수 없다");
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }
}
