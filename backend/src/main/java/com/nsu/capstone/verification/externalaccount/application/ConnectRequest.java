package com.nsu.capstone.verification.externalaccount.application;

/**
 * 연결 요청. 계약 §5.1이 플랫폼마다 쓰는 필드를 서로 금지하므로 타입으로 나눈다.
 */
public sealed interface ConnectRequest {

    /**
     * YouTube: 콜백이 넘겨준 {@code state}·{@code code}와 시작 때 내려준 결합 쿠키 값.
     * 교체 승인({@code replaceExternalAccountId})은 시작 때 Redis 진행 값에 고정됐으므로 여기에 없다(06 §5.1).
     *
     * @param bindingNonce 결합 쿠키 값. 쿠키가 없으면 {@code null}
     */
    record YoutubeConnect(String state, String code, String bindingNonce) implements ConnectRequest {}
}
