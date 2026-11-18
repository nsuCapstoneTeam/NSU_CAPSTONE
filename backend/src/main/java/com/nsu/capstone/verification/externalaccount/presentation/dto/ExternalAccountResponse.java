package com.nsu.capstone.verification.externalaccount.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nsu.capstone.verification.externalaccount.application.AccountView;
import com.nsu.capstone.verification.externalaccount.application.WorkView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** 연결한 계정 하나(06 §6.2). 시각은 {@code Asia/Seoul}의 {@code +09:00} 초 단위 문자열이다(00 §시각). */
public record ExternalAccountResponse(
        UUID externalAccountId,
        String provider,
        @JsonProperty("isControlVerified") boolean isControlVerified,
        String profileUrl,
        String displayName,
        String thumbnailUrl,
        String connectedAt,
        List<WorkResponse> works) {

    public record WorkResponse(String workId, String url, String title, String thumbnailUrl) {

        static WorkResponse from(WorkView work) {
            return new WorkResponse(work.workId(), work.url(), work.title(), work.thumbnailUrl());
        }
    }

    public static ExternalAccountResponse from(AccountView view) {
        return new ExternalAccountResponse(
                view.externalAccountId(),
                view.provider().name(),
                view.isControlVerified(),
                view.profileUrl(),
                view.displayName(),
                view.thumbnailUrl(),
                time(view.connectedAt()),
                view.works().stream().map(WorkResponse::from).toList());
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    /** 이 모듈의 응답 DTO가 함께 쓴다. 초 단위, 소수 초 없음, 오프셋 {@code +09:00}. */
    static String time(Instant instant) {
        // 플랫폼이 시각을 주지 않은 값은 null로 내보낸다(후보의 게시 시각)
        return instant == null ? null : TIMESTAMP.format(instant.atZone(ZONE));
    }
}
