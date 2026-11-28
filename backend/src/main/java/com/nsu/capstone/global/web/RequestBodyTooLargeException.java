package com.nsu.capstone.global.web;

import java.io.IOException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.http.HttpStatus;

/** 요청 본문이 상한을 넘었다. 메시지는 고정 문구이고 요청 값을 담지 않는다. */
public final class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException() {
        super("요청 본문이 허용 크기를 넘었다");
    }

    /**
     * 원인 사슬에서 이 예외, 또는 errorCode가 413인 Tomcat 폼 파싱 예외를 찾는다. 폼 파싱은 앱의 래퍼를 거치지 않고 Tomcat이 자기 상한
     * (max-http-form-post-size)으로 거절하기 때문이다.
     */
    public static boolean isBodyTooLarge(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestBodyTooLargeException
                    || (cause instanceof InvalidParameterException invalid
                            && invalid.getErrorCode() == HttpStatus.CONTENT_TOO_LARGE.value())) {
                return true;
            }
        }
        return false;
    }
}
