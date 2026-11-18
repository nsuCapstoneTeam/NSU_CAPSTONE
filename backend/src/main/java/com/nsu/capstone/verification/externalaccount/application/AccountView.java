package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 연결한 계정 하나(06 §6.2). 토큰과 플랫폼 식별자 외의 계정 정보(이메일 등)는 담지 않는다.
 *
 * @param isControlVerified YouTube는 {@code true}(OAuth로 제어권 확인), Spotify는 {@code false}
 * @param profileUrl 계정 페이지. ID로 만들어 항상 있다
 * @param displayName 계정 이름. 받지 못했으면 {@code null}
 * @param thumbnailUrl 플랫폼 이미지 주소. 받지 못했거나 허용 호스트가 아니면 {@code null}
 * @param works 고른 YouTube 영상(고른 순서). Spotify는 항상 빈 목록이다
 */
public record AccountView(
        UUID externalAccountId,
        ExternalAccountProvider provider,
        boolean isControlVerified,
        String profileUrl,
        String displayName,
        String thumbnailUrl,
        Instant connectedAt,
        List<WorkView> works) {}
