package com.nsu.capstone.global.logging;

import lombok.extern.slf4j.Slf4j;

/**
 * 스레드의 미처리 예외를 로거로 보낸다(ADR logging/00 Decision 4). JDK 기본 처리는 `Exception in thread "<이름>" <클래스>: <메시지>`를 평문
 * System.err로 찍어서 메시지 없는 출력기와 출력 장치 자기 검사를 모두 지나지 않는다. 외부 업체 응답에서 온 값(전화번호 등)이 든 예외를 실행기
 * 작업이 잡지 못하고 던지는 경우가 여기에 든다.
 *
 * <p>로그 문장은 정적이고, 예외는 마지막 인자로만 넘겨 스택 트레이스(메시지 없는 출력기)로 가게 한다. 스레드 이름도 문장에 넣지 않는다.
 * 스레드 이름에는 호출한 쪽이 만든 값이 들어갈 수 있다.
 */
@Slf4j
final class UncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        log.error("스레드에서 처리하지 못한 예외가 나왔다", throwable);
    }
}
