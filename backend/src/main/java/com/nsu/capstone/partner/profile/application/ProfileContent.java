package com.nsu.capstone.partner.profile.application;

import java.util.List;

/** 검증을 마친 수정 내용(05 §2). 서비스가 지역을 정규화한 뒤 저장한다. */
public record ProfileContent(
    String name,
    String organization,
    String bio,
    List<String> eventTypes,
    List<String> regionCodes
) {
}
