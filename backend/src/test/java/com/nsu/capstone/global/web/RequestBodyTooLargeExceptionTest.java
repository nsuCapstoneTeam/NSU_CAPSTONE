package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("본문 초과 판정")
class RequestBodyTooLargeExceptionTest {

    @Test
    void 원인_사슬_깊숙이_있는_초과_예외도_찾는다() {
        Throwable wrapped = new IllegalStateException(
                "맨 바깥", new ServletException("가운데", new RuntimeException("안쪽", new RequestBodyTooLargeException())));

        assertThat(RequestBodyTooLargeException.isBodyTooLarge(wrapped)).isTrue();
    }

    @Test
    void Tomcat_폼_파싱의_413도_초과로_본다() {
        Throwable wrapped = new ServletException("Request processing failed", new InvalidParameterException("초과", 413));

        assertThat(RequestBodyTooLargeException.isBodyTooLarge(wrapped)).isTrue();
    }

    @Test
    void Tomcat_폼_파싱의_413이_아닌_오류는_초과가_아니다() {
        assertThat(RequestBodyTooLargeException.isBodyTooLarge(new InvalidParameterException("잘못된 인코딩", 400)))
                .isFalse();
    }

    @Test
    void 초과와_상관없는_예외는_초과가_아니다() {
        assertThat(RequestBodyTooLargeException.isBodyTooLarge(
                        new ServletException("바깥", new IllegalStateException("안쪽"))))
                .isFalse();
    }
}
