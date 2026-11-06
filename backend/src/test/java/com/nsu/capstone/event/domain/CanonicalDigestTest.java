package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정규 문자열 형식(ADR event_management/03)을 고정하는 순수 단위 테스트. Spring도 DB도 쓰지 않는다.
 * 여기서 깨지면 저장된 request_hash가 달라진다는 뜻이다.
 */
@DisplayName("CanonicalDigest: 길이 접두 정규 문자열 + SHA-256")
class CanonicalDigestTest {

    private static CanonicalDigest digest() {
        return CanonicalDigest.start();
    }

    @Test
    @DisplayName("값마다 <길이>:<값>; 이고, 길이는 UTF-16 code unit 수다")
    void valuesAreLengthPrefixedInUtf16CodeUnits() {
        assertThat(digest().text("ab").canonical()).isEqualTo("2:ab;");
        assertThat(digest().text("가나").canonical()).isEqualTo("2:가나;");
        assertThat(digest().text("😀").canonical()).isEqualTo("2:😀;"); // 이모지 1개 = 2
    }

    @Test
    @DisplayName("null은 -1; 이고 빈 문자열(0:;)과 다르다")
    void nullAndEmptyStringDoNotCollide() {
        assertThat(digest().text(null).canonical()).isEqualTo("-1;");
        assertThat(digest().text("").canonical()).isEqualTo("0:;");
        assertThat(digest().text(null).hex()).isNotEqualTo(digest().text("").hex());
    }

    @Test
    @DisplayName("값 안에 구분자가 있어도 서로 다른 조합이 같아지지 않는다")
    void valueSplitsAcrossPartsDoNotCollide() {
        assertThat(digest().text("a;b").text("c").hex()).isNotEqualTo(digest().text("a").text("b;c").hex());
        assertThat(digest().text("1:a;").hex()).isNotEqualTo(digest().text("1:a").text("").hex());
    }

    @Test
    @DisplayName("타입별 표기: enum은 name(), 숫자는 십진수, 불리언은 true/false, 시각은 LocalDateTime.toString()")
    void typedParts() {
        assertThat(digest().code(EventType.CONCERT).canonical()).isEqualTo("7:CONCERT;");
        assertThat(digest().code(null).canonical()).isEqualTo("-1;");
        assertThat(digest().number(40).canonical()).isEqualTo("2:40;");
        assertThat(digest().number(300000L).canonical()).isEqualTo("6:300000;");
        assertThat(digest().flag(true).flag(false).flag(null).canonical()).isEqualTo("4:true;5:false;-1;");
        // 초가 0이면 toString이 초를 생략한다. 이 사실이 저장된 해시에 들어 있다.
        assertThat(digest().time(LocalDateTime.of(2026, 11, 6, 18, 0)).canonical())
            .isEqualTo("16:2026-11-06T18:00;");
        assertThat(digest().time(LocalDateTime.of(2026, 11, 6, 18, 0, 30)).canonical())
            .isEqualTo("19:2026-11-06T18:00:30;");
    }

    @Test
    @DisplayName("enum 집합(장르)은 입력 순서와 관계없이 선언 순서로 정렬하고 이름마다 앞에 쉼표를 붙인다")
    void enumSetsAreOrderedByDeclarationOrderWithLeadingComma() {
        assertThat(digest().codeSet(List.of(Genre.ROCK, Genre.POP)).canonical()).isEqualTo("9:,POP,ROCK;");
        assertThat(digest().codeSet(List.of(Genre.POP, Genre.ROCK)).canonical()).isEqualTo("9:,POP,ROCK;");
        assertThat(digest().codeSet(EnumSet.of(Genre.TROT, Genre.POP)).canonical()).isEqualTo("9:,POP,TROT;");
        assertThat(digest().codeSet(List.of(Genre.JAZZ)).canonical()).isEqualTo("5:,JAZZ;");
        assertThat(digest().codeSet(null).canonical()).isEqualTo("-1;");
    }

    @Test
    @DisplayName("hex는 정규 문자열 UTF-8 바이트의 SHA-256 소문자 64자다")
    void hexIsLowercaseSha256OfUtf8Canonical() throws Exception {
        assertThat(digest().text("").hex())
            .isEqualTo("5578a44007e3068dbfa7e9b12b3945764ebf2160b21e7360a47ecfa3dc4133fa"); // sha256("0:;")
        assertThat(digest().text("ab").hex())
            .isEqualTo("37c224db7de2de79b51fac09f637b48133749e43d8a865f6f6ddb3c694d976e4"); // sha256("2:ab;")

        CanonicalDigest korean = digest().text("가나다").text("7");
        byte[] expected = MessageDigest.getInstance("SHA-256")
            .digest(korean.canonical().getBytes(StandardCharsets.UTF_8));
        assertThat(korean.hex()).isEqualTo(HexFormat.of().formatHex(expected)).matches("[0-9a-f]{64}");
    }
}
