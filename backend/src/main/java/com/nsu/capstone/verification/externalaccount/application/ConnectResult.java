package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;

/**
 * 연결 결과.
 *
 * @param created 새로 만들었으면 {@code true}(HTTP 201), 같은 계정을 다시 연결해 아무것도 바꾸지 않았으면 {@code false}(200)
 */
public record ConnectResult(ExternalAccount account, boolean created) {}
