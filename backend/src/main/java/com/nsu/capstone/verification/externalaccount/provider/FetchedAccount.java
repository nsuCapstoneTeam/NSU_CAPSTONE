package com.nsu.capstone.verification.externalaccount.provider;

/**
 * 플랫폼에서 확인한 계정. 토큰은 담지 않는다. 토큰은 게이트웨이 안에서만 쓰고 폐기한다(ADR 14번).
 *
 * @param providerAccountId YouTube 채널 ID, Spotify 아티스트 ID
 */
public record FetchedAccount(String providerAccountId) {}
