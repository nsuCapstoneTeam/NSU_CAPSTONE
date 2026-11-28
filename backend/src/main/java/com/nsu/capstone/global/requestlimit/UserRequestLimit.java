package com.nsu.capstone.global.requestlimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 사용자 쓰기 API의 계정별 요청 한도(ADR user_request_limit/00). 컨트롤러가 가져다 쓰는 유일한 것이다.
 *
 * <p>{@code @PreAuthorize} 검사 바로 다음, 메서드 본문 전에 그 계정의 한도에서 1회를 쓴다. 클래스에 달면 {@code GET}까지 세므로 메서드에만 단다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRequestLimit {

    /** 이 핸들러가 쓰는 한도. 숫자는 {@code config/user-request-limit.properties}에 있다. */
    RequestBudget value();
}
