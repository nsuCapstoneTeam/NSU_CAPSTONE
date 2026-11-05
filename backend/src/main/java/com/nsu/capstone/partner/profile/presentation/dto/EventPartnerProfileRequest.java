package com.nsu.capstone.partner.profile.presentation.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.partner.profile.application.ProfileContent;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 수정 본문(05 EVENT_PARTNER 프로필 API §2). enum과 코드는 문자열로 받아 직접 검사한다.
 * JSON 변환 단계에서 실패해 Spring 표준 400이 나가지 않게 하기 위해서다(ADR-007 §4.4).
 * 그래서 API 문서의 필수 여부와 허용 값은 @Schema로 보정한다(00 공통 규칙 §10.1).
 */
public record EventPartnerProfileRequest(
    @Schema(requiredMode = REQUIRED) String name,
    String organization,
    String bio,
    @ArraySchema(
        arraySchema = @Schema(requiredMode = REQUIRED),
        schema = @Schema(implementation = EventType.class)
    ) List<String> eventTypes,
    @ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED)) List<String> regionCodes
) {

    public ProfileContent toContent() {
        return new ProfileContent(name, organization, bio, eventTypes, regionCodes);
    }
}
