package com.nsu.capstone.verification.externalaccount.application;

/**
 * 아티스트가 고른 영상 하나(06 §6.3). 링크는 ID로 서버가 만든다. 제목·썸네일은 받지 못했으면 {@code null}이다.
 */
public record WorkView(String workId, String url, String title, String thumbnailUrl) {}
