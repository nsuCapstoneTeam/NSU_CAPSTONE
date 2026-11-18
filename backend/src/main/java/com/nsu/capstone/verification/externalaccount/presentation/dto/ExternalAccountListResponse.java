package com.nsu.capstone.verification.externalaccount.presentation.dto;

import com.nsu.capstone.verification.externalaccount.application.AccountView;
import java.util.List;

/** 링크 조회 응답(06 §6.1). 연결한 계정이 없으면 {@code items}는 빈 배열이다. */
public record ExternalAccountListResponse(List<ExternalAccountResponse> items) {

    public static ExternalAccountListResponse from(List<AccountView> views) {
        return new ExternalAccountListResponse(
                views.stream().map(ExternalAccountResponse::from).toList());
    }
}
