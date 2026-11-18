package com.nsu.capstone.verification.externalaccount.provider;

import java.util.Optional;

/** Spotify와 주고받는 호출. 서비스는 이 포트만 안다. */
public interface SpotifyGateway {

    /**
     * 아티스트가 있으면 표시 정보를, 없으면 빈 값을 돌려준다. 시간 초과·429·5xx 등은
     * {@code ExternalAccountException}(EXTERNAL_PROVIDER_UNAVAILABLE)이다.
     */
    Optional<ProviderProfile> findArtist(String artistId);
}
