package com.nsu.capstone.verification.externalaccount.presentation.dto;

import java.util.List;

/** 작업물 고르기 요청(06 §8). 0~10개, 중복·빈 문자열 검증은 서비스가 한다. 고른 순서가 표시 순서다. */
public record ChooseWorksRequest(List<String> workIds) {}
