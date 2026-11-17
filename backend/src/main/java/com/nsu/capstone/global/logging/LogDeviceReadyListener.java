package com.nsu.capstone.global.logging;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 앱 준비 완료 때 출력 장치를 한 번 더 확인한다(ADR logging/00 Decision 4). 컨텍스트를 만드는 중에 어펜더가 늘거나 설정이 바뀌는 경우를 잡는다.
 */
class LogDeviceReadyListener implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (LogSafetyListener.isLocalMode(event.getApplicationContext().getEnvironment())) {
            return;
        }
        LogDeviceCheck.failIfUnsafe();
    }
}
