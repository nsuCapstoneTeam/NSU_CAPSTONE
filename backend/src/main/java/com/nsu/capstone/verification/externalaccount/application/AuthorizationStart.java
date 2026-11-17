package com.nsu.capstone.verification.externalaccount.application;

import java.time.Instant;

/**
 * 연결 시작 결과(06 §3). {@code bindingNonce}는 컨트롤러가 결합 쿠키로 내려보낸다. 서비스는 HTTP를 모른다.
 *
 * @param authorizationUrl 브라우저가 같은 탭에서 이동할 Google 인가 주소
 * @param expiresAt 진행 값이 만료되는 시각
 * @param bindingNonce 시작한 브라우저를 식별하는 값. Redis에는 digest만 둔다
 */
public record AuthorizationStart(String authorizationUrl, Instant expiresAt, String bindingNonce) {}
