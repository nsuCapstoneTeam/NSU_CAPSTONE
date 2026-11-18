package com.nsu.capstone.global.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 앱 준비 완료 때 출력 장치를 한 번 더 확인한다(ADR logging/00 Decision 4). 컨텍스트를 만드는 중에 어펜더가 늘거나 설정이 바뀌는 경우를 잡는다.
 * 검사를 통과하면 확인 줄을 INFO로 한 번 남긴다. 배포 확인(NSU-129)은 경고가 없음이 아니라 이 줄이 있음을 본다. 필터와 레벨은 줄을 지울 수만
 * 있고 만들 수는 없어서, 줄이 있으면 로컬 모드가 아니고 검사를 통과했다는 뜻이다.
 */
@Slf4j
class LogDeviceReadyListener implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (LogSafetyListener.isLocalMode(event.getApplicationContext().getEnvironment())) {
            return;
        }
        LogDeviceCheck.failIfUnsafe();
        // 배포 확인이 이 줄의 앞부분을 찾는다. 문구를 바꿔도 앞부분 `로그 보호 장치 확인 완료`는 그대로 둔다
        log.info("로그 보호 장치 확인 완료. 로그는 JSON 콘솔 출력 하나로만 나간다");
    }
}
