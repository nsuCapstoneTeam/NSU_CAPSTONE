package com.nsu.capstone.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켜는 유일한 곳이다. 서버는 {@code app.scheduling.enabled}가 없으면 켜지고, 값이 {@code false}(대소문자 무시)일 때만 꺼진다. 오타나 빈 값은
 * 켜 둔다. 꺼진 줄 모르고 서버가 예약 작업(예: 30일 넘은 외부 계정 링크 정리)을 잃지 않게 하려는 것이다. 서버 전체 컨텍스트를 띄우는 시드 jar만
 * {@code --app.scheduling.enabled=false}를 명령행 인수로 고정해 끈다. 작업 하나씩 끄지 않고 스케줄링 자체를 꺼서, 앞으로 생길 작업도 시드 안에서 돌지 않게 한다. 다른 곳에
 * {@code @EnableScheduling}을 두면 이 스위치가 듣지 않으므로 두지 않는다(시드 jar 시험이 막는다).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {}
