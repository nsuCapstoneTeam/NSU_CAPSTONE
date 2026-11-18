package com.nsu.capstone.verification.externalaccount.provider;

/**
 * 플랫폼이 알려 준 계정의 표시 정보. 믿지 않는 입력이다. 정리(제어 문자 제거, 길이 제한, 허용 호스트)는
 * 응답을 만들 때 한다(06 §6.4의 9).
 *
 * @param displayName 계정 이름. 받지 못했으면 {@code null}
 * @param thumbnailUrl 플랫폼 이미지 주소. 없으면 {@code null}
 */
public record ProviderProfile(String displayName, String thumbnailUrl) {}
