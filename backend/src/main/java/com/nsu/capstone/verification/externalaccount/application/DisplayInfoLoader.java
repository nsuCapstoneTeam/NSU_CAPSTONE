package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountConfig;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import com.nsu.capstone.verification.externalaccount.redis.DisplayCache;
import com.nsu.capstone.verification.externalaccount.redis.ProfileCache;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 조회 때 플랫폼에서 받는 표시 정보(06 §6.5). 계정마다 병렬로 받고 응답 전체에 상한을 둔다. 실패하거나 상한을 넘은 계정은
 * 모름({@link AccountDisplay#UNKNOWN})으로 두어 링크만 보이게 한다. 여기서는 어떤 예외도 밖으로 내보내지 않는다.
 */
@Slf4j
@Component
class DisplayInfoLoader {

    private static final String SPOTIFY_KIND = "artist";

    private final ExternalAccountProperties properties;
    private final YoutubeGateway youtubeGateway;
    private final SpotifyGateway spotifyGateway;
    private final YoutubeQuota youtubeQuota;
    private final ProfileCache profileCache;
    private final DisplayCache displayCache;
    /** 계정마다 병렬로 받는 실행기. 모듈 설정이 만든다(ExternalAccountConfig). 닫는 것도 Spring이 한다. */
    private final ExecutorService executor;

    private final SingleFlight singleFlight = new SingleFlight();

    // 실행기 bean이 기본 후보가 아니라서 이름을 지정해야 한다. Lombok은 매개변수의 @Qualifier를 생성자로 옮기지 않아 손으로 쓴다.
    DisplayInfoLoader(
            ExternalAccountProperties properties,
            YoutubeGateway youtubeGateway,
            SpotifyGateway spotifyGateway,
            YoutubeQuota youtubeQuota,
            ProfileCache profileCache,
            DisplayCache displayCache,
            @Qualifier(ExternalAccountConfig.DISPLAY_EXECUTOR) ExecutorService executor) {
        this.properties = properties;
        this.youtubeGateway = youtubeGateway;
        this.spotifyGateway = spotifyGateway;
        this.youtubeQuota = youtubeQuota;
        this.profileCache = profileCache;
        this.displayCache = displayCache;
        this.executor = executor;
    }

    Map<UUID, AccountDisplay> load(
            UUID callerId, List<ExternalAccount> accounts, Map<UUID, List<SelectedWork>> worksByAccount) {
        Map<UUID, CompletableFuture<AccountDisplay>> futures = new HashMap<>();
        for (ExternalAccount account : accounts) {
            List<SelectedWork> works = worksByAccount.getOrDefault(account.id(), List.of());
            futures.put(account.id(), CompletableFuture.supplyAsync(() -> loadOne(callerId, account, works), executor));
        }
        try {
            CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new))
                    .get(properties.http().readTotalTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("표시 정보를 받는 데 걸리는 시간이 상한을 넘어, 받은 것만 쓰고 나머지는 링크만 보인다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException ignored) {
            // loadOne이 예외를 삼키므로 오지 않는다
        }
        Map<UUID, AccountDisplay> result = new HashMap<>();
        futures.forEach((accountId, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                result.put(accountId, future.join());
            } else {
                future.cancel(true);
                result.put(accountId, AccountDisplay.UNKNOWN);
            }
        });
        return result;
    }

    private AccountDisplay loadOne(UUID callerId, ExternalAccount account, List<SelectedWork> works) {
        try {
            return switch (account.provider()) {
                case SPOTIFY -> loadSpotify(account);
                case YOUTUBE -> loadYoutube(callerId, account, works);
            };
        } catch (RuntimeException e) {
            // 예외 객체는 메시지와 스택을 함께 찍는다. 종류만 남긴다
            log.warn(
                    "표시 정보를 받지 못했다: 연결 {}, 플랫폼 {}, 예외 {}",
                    account.id(),
                    account.provider(),
                    e.getClass().getSimpleName());
            return AccountDisplay.UNKNOWN;
        }
    }

    /** 계정 하나의 표시 정보 비용(채널 1 + 영상 1). 조회한 사람에게 매긴다(06 §11.2). */
    private static final int YOUTUBE_DISPLAY_UNITS = 2;

    private AccountDisplay loadYoutube(UUID callerId, ExternalAccount account, List<SelectedWork> works) {
        if (!properties.youtube().enabled()) {
            return AccountDisplay.UNKNOWN;
        }
        List<String> videoIds = works.stream().map(SelectedWork::providerWorkId).toList();
        Optional<YoutubeDisplay> cached = displayCache.get(account.id(), videoIds);
        if (cached.isPresent()) {
            return toAccountDisplay(cached.get());
        }
        if (displayCache.recentlyFailed(account.id())) {
            return AccountDisplay.UNKNOWN;
        }
        // 캐시가 빈 동시 조회는 서버 안에서 한 번만 부르고 결과를 나눠 쓴다. 고른 영상이 다른 조회는 결과가 달라 키에 영상 목록을 넣는다
        return singleFlight.run("youtube-display:" + account.id() + ":" + String.join(",", videoIds), () -> {
            Optional<YoutubeDisplay> filledMeanwhile = displayCache.get(account.id(), videoIds);
            if (filledMeanwhile.isPresent()) {
                return toAccountDisplay(filledMeanwhile.get());
            }
            // 사용자 한도나 호출자 상한을 넘었으면 YouTube를 부르지 않고 링크만 남긴다
            if (!youtubeQuota.tryReserve(callerId, YOUTUBE_DISPLAY_UNITS)) {
                return AccountDisplay.UNKNOWN;
            }
            try {
                YoutubeDisplay display = youtubeGateway.fetchDisplay(account.providerAccountId(), videoIds);
                displayCache.put(account.id(), videoIds, display);
                return toAccountDisplay(display);
            } catch (ExternalAccountException e) {
                // 이름과 제목만 비고 링크는 남는 장애다. 원인(사유)은 로그에 남겨야 API 키 만료 같은 문제를 알아챈다
                log.warn("YouTube 표시 정보를 받지 못했다: 연결 {}, 사유 {}", account.id(), e.getFailure());
                displayCache.markFailed(account.id());
                return AccountDisplay.UNKNOWN;
            }
        });
    }

    private static AccountDisplay toAccountDisplay(YoutubeDisplay display) {
        return new AccountDisplay(display.channel(), display.publicVideos());
    }

    private AccountDisplay loadSpotify(ExternalAccount account) {
        if (!properties.spotify().enabled()) {
            return AccountDisplay.UNKNOWN;
        }
        String artistId = account.providerAccountId();
        Optional<ProviderProfile> cached = profileCache.get(ExternalAccountProvider.SPOTIFY, SPOTIFY_KIND, artistId);
        if (cached.isPresent()) {
            return new AccountDisplay(cached.get(), Map.of());
        }
        if (displayCache.recentlyFailed(account.id())) {
            return AccountDisplay.UNKNOWN;
        }
        // 캐시가 빈 동시 조회는 서버 안에서 한 번만 부르고 결과를 나눠 쓴다(06 §6.5)
        return singleFlight.run("spotify-display:" + artistId, () -> {
            Optional<ProviderProfile> filledMeanwhile =
                    profileCache.get(ExternalAccountProvider.SPOTIFY, SPOTIFY_KIND, artistId);
            if (filledMeanwhile.isPresent()) {
                return new AccountDisplay(filledMeanwhile.get(), Map.of());
            }
            try {
                Optional<ProviderProfile> found = spotifyGateway.findArtist(artistId);
                if (found.isPresent()) {
                    profileCache.put(ExternalAccountProvider.SPOTIFY, SPOTIFY_KIND, artistId, found.get());
                    return new AccountDisplay(found.get(), Map.of());
                }
                // 이제 Spotify에 없는 아티스트다. 링크만 남기고, 장애 때처럼 짧게 기억해 반복 호출을 막는다
                displayCache.markFailed(account.id());
                return AccountDisplay.UNKNOWN;
            } catch (ExternalAccountException e) {
                log.warn("Spotify 표시 정보를 받지 못했다: 연결 {}, 사유 {}", account.id(), e.getFailure());
                displayCache.markFailed(account.id());
                return AccountDisplay.UNKNOWN;
            }
        });
    }
}
