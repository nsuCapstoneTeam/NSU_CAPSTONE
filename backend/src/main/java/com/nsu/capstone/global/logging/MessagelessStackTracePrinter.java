package com.nsu.capstone.global.logging;

import java.io.IOException;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.StandardStackTracePrinter;

/**
 * 예외 메시지를 뺀 스택 트레이스 출력기(ADR logging/00 Decision 7-4).
 * 예외 클래스 이름, 원인과 suppressed 체인의 클래스 이름, 스택 프레임만 남긴다. 예외 메시지에는 요청 입력이 섞이기 쉬워서,
 * 종류를 하나씩 막지 않고 예외를 남기는 모든 곳에 한 번에 적용한다. 길이 제한 등 나머지 설정은 Boot가 넘겨 주는 출력기를 따른다.
 */
class MessagelessStackTracePrinter implements StackTracePrinter {

    private final StandardStackTracePrinter delegate;

    MessagelessStackTracePrinter(StandardStackTracePrinter standardPrinter) {
        this.delegate =
                standardPrinter.withFormatter(throwable -> throwable.getClass().getName());
    }

    @Override
    public void printStackTrace(Throwable throwable, Appendable out) throws IOException {
        delegate.printStackTrace(throwable, out);
    }
}
