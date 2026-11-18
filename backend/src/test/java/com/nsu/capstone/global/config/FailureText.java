package com.nsu.capstone.global.config;

/** 시작 실패 때 사용자가 보게 되는 글(예외 사슬의 메시지)을 하나로 모은다. 값이 새는지 찾는 데 쓴다. */
public final class FailureText {

    private FailureText() {}

    public static String of(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            text.append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append('\n');
        }
        return text.toString();
    }
}
