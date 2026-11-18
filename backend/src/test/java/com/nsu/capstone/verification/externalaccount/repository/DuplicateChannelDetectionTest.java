package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;

/**
 * 중복 YouTube 채널은 PostgreSQL 오류 메시지가 아니라 제약 이름으로 구분한다(NSU-146 규칙: 메시지를 파싱하지 않는다).
 * 구조화 로그 도입 뒤에는 드라이버가 오류 DETAIL을 빼므로 메시지 모양에 기대면 안 된다.
 */
class DuplicateChannelDetectionTest {

    @Test
    void 메시지에_제약_이름이_없어도_제약_이름이_YouTube_채널_인덱스면_중복_채널이다() {
        DuplicateKeyException e = duplicate("uk_external_accounts_youtube_channel", "메시지에는 아무 이름도 없다");

        assertThat(ExternalAccountRepository.isYoutubeChannelDuplicate(e)).isTrue();
    }

    @Test
    void 다른_제약의_위반은_메시지에_채널_인덱스_이름이_들어_있어도_중복_채널이_아니다() {
        DuplicateKeyException e =
                duplicate("uk_external_accounts_artist_provider", "uk_external_accounts_youtube_channel 라고 적힌 메시지");

        assertThat(ExternalAccountRepository.isYoutubeChannelDuplicate(e)).isFalse();
    }

    @Test
    void 원인이_PostgreSQL_오류가_아니면_중복_채널이_아니다() {
        assertThat(ExternalAccountRepository.isYoutubeChannelDuplicate(new DuplicateKeyException("x")))
                .isFalse();
        assertThat(ExternalAccountRepository.isYoutubeChannelDuplicate(
                        new DuplicateKeyException("x", new IllegalStateException())))
                .isFalse();
    }

    /** 서버 오류 메시지는 '필드 종류 한 글자 + 값 + NUL'의 나열이다. n은 제약 이름이다. */
    private static DuplicateKeyException duplicate(String constraint, String message) {
        ServerErrorMessage server =
                new ServerErrorMessage("SERROR\u0000C23505\u0000Mduplicate key value violates unique constraint\u0000n"
                        + constraint + "\u0000");
        return new DuplicateKeyException(message, new PSQLException(server));
    }
}
