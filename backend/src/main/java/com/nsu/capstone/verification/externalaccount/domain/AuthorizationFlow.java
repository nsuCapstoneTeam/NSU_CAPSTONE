package com.nsu.capstone.verification.externalaccount.domain;

import java.util.UUID;

/**
 * 연결 시작에서 연결 요청까지 Redis에 두는 진행 값(06 §3). 토큰 필드가 없다(ADR 원칙 테스트 2).
 *
 * @param codeVerifier PKCE 검증값
 * @param bindingDigest 결합 쿠키 nonce의 SHA-256 digest. 원문은 저장하지 않는다
 * @param expectedExternalAccountId 교체를 승인한 지금 연결의 ID. 그 플랫폼 계정이 없었으면 {@code null}
 */
public record AuthorizationFlow(String codeVerifier, String bindingDigest, UUID expectedExternalAccountId) {}
