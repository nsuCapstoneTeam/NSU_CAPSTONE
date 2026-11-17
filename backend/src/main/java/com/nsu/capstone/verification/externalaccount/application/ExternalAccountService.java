package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.identity.application.ArtistReadAccess;
import com.nsu.capstone.identity.application.UserIdGenerator;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.AuditActor;
import com.nsu.capstone.verification.externalaccount.domain.AuditEvent;
import com.nsu.capstone.verification.externalaccount.domain.AuditEventType;
import com.nsu.capstone.verification.externalaccount.domain.AuthorizationFlow;
import com.nsu.capstone.verification.externalaccount.domain.DisplayText;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.ProviderLink;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.domain.SpotifyArtistUrl;
import com.nsu.capstone.verification.externalaccount.domain.ThumbnailPolicy;
import com.nsu.capstone.verification.externalaccount.provider.FetchedAccount;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import com.nsu.capstone.verification.externalaccount.redis.AuthorizationStore;
import com.nsu.capstone.verification.externalaccount.redis.CandidateCache;
import com.nsu.capstone.verification.externalaccount.redis.ProfileCache;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import com.nsu.capstone.verification.externalaccount.repository.LockedArtist;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 외부 계정 연결의 단일 소유자(ADR "모듈과 패키지"). 컨트롤러는 이 서비스만 부른다.
 */
@Service
@RequiredArgsConstructor
public class ExternalAccountService {

    private static final String YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    /** 작업물 후보 한 번의 YouTube 비용(업로드 재생목록 조회 + 영상 상세 조회 등, 06 §11.2). */
    private static final int CANDIDATE_UNITS = 3;

    /** 계정마다 고를 수 있는 영상의 최대 개수(06 §8). */
    private static final int MAX_WORKS = 10;

    private final SingleFlight candidateFlight = new SingleFlight();

    private final ArtistReadAccess artistReadAccess;
    private final DisplayInfoLoader displayInfoLoader;
    private final ExternalAccountRepository repository;
    private final AuthorizationStore authorizationStore;
    private final YoutubeGateway youtubeGateway;
    private final SpotifyGateway spotifyGateway;
    private final ProfileCache profileCache;
    private final CandidateCache candidateCache;
    private final YoutubeQuota youtubeQuota;
    private final ExternalAccountProperties properties;
    private final OAuthOpaqueValueService opaqueValues;
    private final UserIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * YouTube 연결을 시작한다(06 §3). 지금 연결이 {@code replaceExternalAccountId}와 다르면 외부 호출 전에
     * EXTERNAL_ACCOUNT_CONNECTION_CHANGED로 거절하고 Redis 키를 만들지 않는다.
     *
     * @param replaceExternalAccountId 교체를 승인한 지금 연결의 ID. 그 플랫폼 계정이 없으면 {@code null}
     */
    public AuthorizationStart startAuthorization(UUID artistId, UUID replaceExternalAccountId) {
        requireEnabled(ExternalAccountProvider.YOUTUBE);
        UUID currentId = repository
                .findByArtistAndProvider(artistId, ExternalAccountProvider.YOUTUBE)
                .map(ExternalAccount::id)
                .orElse(null);
        if (!Objects.equals(currentId, replaceExternalAccountId)) {
            throw connectionChanged();
        }
        Instant now = clock.instant();
        String state = opaqueValues.generate();
        String codeVerifier = opaqueValues.generate();
        String bindingNonce = opaqueValues.generate();
        authorizationStore.save(
                opaqueValues.digest(state),
                artistId,
                new AuthorizationFlow(codeVerifier, opaqueValues.digest(bindingNonce), replaceExternalAccountId));
        ExternalAccountProperties.Youtube youtube = properties.youtube();
        String authorizationUrl = UriComponentsBuilder.fromUriString(youtube.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", youtube.clientId())
                .queryParam("redirect_uri", youtube.redirectUri())
                .queryParam("scope", YOUTUBE_SCOPE)
                .queryParam("state", state)
                .queryParam("code_challenge", pkceChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        return new AuthorizationStart(authorizationUrl, now.plus(AuthorizationStore.TTL), bindingNonce);
    }

    /**
     * 아티스트가 연결한 계정과 고른 영상을 링크로 돌려준다(06 §6). 호출 주체 규칙은 Trust Profile과 같다.
     */
    public List<AccountView> linksOf(UUID callerId, UserRole callerRole, UUID artistId) {
        artistReadAccess.require(callerId, callerRole, artistId);
        return viewsOf(callerId, repository.findByArtist(artistId));
    }

    private List<AccountView> viewsOf(UUID callerId, List<ExternalAccount> unsorted) {
        List<ExternalAccount> accounts = unsorted.stream()
                .sorted(Comparator.comparing(ExternalAccount::provider))
                .toList();
        Map<UUID, List<SelectedWork>> works = new HashMap<>();
        accounts.forEach(account -> works.put(account.id(), repository.findWorks(account.id())));
        Map<UUID, AccountDisplay> displays = displayInfoLoader.load(callerId, accounts, works);
        return accounts.stream()
                .map(account -> viewOf(account, works.get(account.id()), displays.get(account.id())))
                .toList();
    }

    private AccountView viewOf(ExternalAccount account, List<SelectedWork> works, AccountDisplay display) {
        // 플랫폼이 응답했는데 없거나 공개가 아닌 영상은 뺀다. 응답을 못 받았으면 모두 남긴다(06 §6.4의 5·6)
        List<WorkView> workViews = works.stream()
                .filter(work -> display.videos() == null || display.videos().containsKey(work.providerWorkId()))
                .map(work -> {
                    ProviderProfile video =
                            display.videos() == null ? null : display.videos().get(work.providerWorkId());
                    return new WorkView(
                            work.providerWorkId(),
                            ProviderLink.youtubeVideo(work.providerWorkId()).value(),
                            video == null ? null : DisplayText.clean(video.displayName(), DisplayText.TITLE_MAX),
                            video == null ? null : ThumbnailPolicy.allowedOrNull(video.thumbnailUrl()));
                })
                .toList();
        ProviderLink profileLink = account.provider() == ExternalAccountProvider.YOUTUBE
                ? ProviderLink.youtubeChannel(account.providerAccountId())
                : ProviderLink.spotifyArtist(account.providerAccountId());
        ProviderProfile profile = display.account();
        return new AccountView(
                account.id(),
                account.provider(),
                account.provider() == ExternalAccountProvider.YOUTUBE,
                profileLink.value(),
                profile == null ? null : DisplayText.clean(profile.displayName(), DisplayText.DISPLAY_NAME_MAX),
                profile == null ? null : ThumbnailPolicy.allowedOrNull(profile.thumbnailUrl()),
                account.connectedAt(),
                workViews);
    }

    /**
     * 아티스트가 고를 수 있는 공개 영상 후보(06 §7). 최신순 최대 50개이고, 지금 고른 영상에 표시한다.
     */
    public List<CandidateView> workCandidates(UUID artistId, UUID externalAccountId) {
        ExternalAccount account = ownedYoutubeAccount(artistId, externalAccountId);
        List<YoutubeCandidate> candidates = candidatesOf(artistId, account);
        Set<String> selected = repository.findWorks(account.id()).stream()
                .map(SelectedWork::providerWorkId)
                .collect(Collectors.toSet());
        return candidates.stream()
                .map(candidate -> new CandidateView(
                        candidate.videoId(),
                        ProviderLink.youtubeVideo(candidate.videoId()).value(),
                        DisplayText.clean(candidate.title(), DisplayText.TITLE_MAX),
                        ThumbnailPolicy.allowedOrNull(candidate.thumbnailUrl()),
                        candidate.publishedAt(),
                        selected.contains(candidate.videoId())))
                .toList();
    }

    /**
     * 고른 영상 전체를 바꾼다(06 §8). 표시 순서는 {@code workIds}의 순서다. 0~10개, 중복 없음.
     * 각 ID는 지금 후보에 있거나 이미 고른 영상이어야 한다.
     */
    public AccountView chooseWorks(UUID artistId, UUID externalAccountId, List<String> workIds) {
        validateWorkIds(workIds);
        ExternalAccount account = ownedYoutubeAccount(artistId, externalAccountId);

        // 1단계(잠금 밖): 지금 고른 영상을 읽어 새 ID가 있는지만 본다. 이 값은 "후보를 받을지"를 고르는 데에만 쓴다.
        Set<String> selectedOutsideLock = repository.findWorks(account.id()).stream()
                .map(SelectedWork::providerWorkId)
                .collect(Collectors.toSet());
        boolean hasNewWork = workIds.stream().anyMatch(id -> !selectedOutsideLock.contains(id));
        Set<String> candidateIds = hasNewWork
                ? candidatesOf(artistId, account).stream()
                        .map(YoutubeCandidate::videoId)
                        .collect(Collectors.toSet())
                : Set.of();

        // 2단계(잠금 안): 계정이 아직 있는지와 지금 고른 영상을 다시 읽고, 그 값으로만 판단한다(ADR 8번 일반 규칙).
        transactionTemplate.executeWithoutResult(status -> {
            LockedArtist locked = repository.lockArtist(artistId);
            ExternalAccount current = ownedAccount(artistId, externalAccountId);
            Map<String, SelectedWork> existing = repository.findWorks(current.id()).stream()
                    .collect(Collectors.toMap(SelectedWork::providerWorkId, work -> work));
            Instant now = clock.instant();
            List<SelectedWork> chosen = workIds.stream()
                    .map(id -> {
                        SelectedWork kept = existing.get(id);
                        if (kept != null) {
                            return kept;
                        }
                        if (!candidateIds.contains(id)) {
                            throw new ExternalAccountException(
                                    ExternalAccountFailure.EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE, "후보에도 기존 선택에도 없는 영상이다");
                        }
                        return new SelectedWork(id, now);
                    })
                    .toList();
            repository.replaceWorks(locked, current.id(), chosen);
        });
        return viewsOf(artistId, List.of(ownedAccount(artistId, externalAccountId)))
                .get(0);
    }

    private static void validateWorkIds(List<String> workIds) {
        boolean invalid = workIds == null
                || workIds.size() > MAX_WORKS
                || workIds.stream().anyMatch(id -> id == null || id.isBlank())
                || workIds.stream().distinct().count() != workIds.size();
        if (invalid) {
            throw new ExternalAccountException(ExternalAccountFailure.VALIDATION_ERROR, "영상은 중복 없이 10개까지 고를 수 있다");
        }
    }

    /** 내 계정이 아니면 없는 것과 같다. 다른 아티스트 계정의 존재를 드러내지 않는다(06 §2). */
    private ExternalAccount ownedAccount(UUID artistId, UUID externalAccountId) {
        return repository
                .findById(externalAccountId)
                .filter(account -> account.artistId().equals(artistId))
                .orElseThrow(() ->
                        new ExternalAccountException(ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND, "연결한 계정이 없다"));
    }

    private ExternalAccount ownedYoutubeAccount(UUID artistId, UUID externalAccountId) {
        ExternalAccount account = ownedAccount(artistId, externalAccountId);
        if (account.provider() != ExternalAccountProvider.YOUTUBE) {
            throw new ExternalAccountException(
                    ExternalAccountFailure.EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED, "이 플랫폼은 작업물을 고를 수 없다");
        }
        return account;
    }

    /** 10분 캐시. 비면 YouTube 비용 3 units를 호출자에게 매기고 받는다. 동시 요청은 한 번으로 합친다. */
    private List<YoutubeCandidate> candidatesOf(UUID callerId, ExternalAccount account) {
        Optional<List<YoutubeCandidate>> cached = candidateCache.get(account.id());
        if (cached.isPresent()) {
            return cached.get();
        }
        requireEnabled(ExternalAccountProvider.YOUTUBE);
        return candidateFlight.run("candidates:" + account.id(), () -> {
            Optional<List<YoutubeCandidate>> filledMeanwhile = candidateCache.get(account.id());
            if (filledMeanwhile.isPresent()) {
                return filledMeanwhile.get();
            }
            if (!youtubeQuota.tryReserve(callerId, CANDIDATE_UNITS)) {
                throw new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 하루 예산을 확보하지 못했다");
            }
            List<YoutubeCandidate> fetched = youtubeGateway.fetchUploads(account.providerAccountId());
            candidateCache.put(account.id(), fetched);
            return fetched;
        });
    }

    public ConnectResult connect(UUID artistId, ConnectRequest request) {
        return switch (request) {
            case ConnectRequest.YoutubeConnect youtube -> connectYoutube(artistId, youtube);
            case ConnectRequest.SpotifyConnect spotify -> connectSpotify(artistId, spotify);
        };
    }

    private ConnectResult connectSpotify(UUID artistId, ConnectRequest.SpotifyConnect request) {
        requireEnabled(ExternalAccountProvider.SPOTIFY);
        String spotifyArtistId = SpotifyArtistUrl.parseArtistId(request.url());
        // 1시간 안에 확인한 결과가 있으면 Spotify를 다시 부르지 않는다(06 §5.3)
        if (profileCache
                .get(ExternalAccountProvider.SPOTIFY, "artist", spotifyArtistId)
                .isEmpty()) {
            ProviderProfile profile = spotifyGateway
                    .findArtist(spotifyArtistId)
                    .orElseThrow(() -> new ExternalAccountException(
                            ExternalAccountFailure.SPOTIFY_ARTIST_NOT_FOUND, "Spotify에 그 아티스트가 없다"));
            profileCache.put(ExternalAccountProvider.SPOTIFY, "artist", spotifyArtistId, profile);
        }
        return transactionTemplate.execute(status ->
                save(artistId, ExternalAccountProvider.SPOTIFY, spotifyArtistId, request.replaceExternalAccountId()));
    }

    private ConnectResult connectYoutube(UUID artistId, ConnectRequest.YoutubeConnect request) {
        requireEnabled(ExternalAccountProvider.YOUTUBE);
        AuthorizationFlow flow = authorizationStore
                .consume(opaqueValues.digest(request.state()), artistId)
                .orElseThrow(ExternalAccountService::authorizationInvalid);
        // 꺼낸 뒤에 비교하므로 불일치도 진행 값을 지운다. 키에 사용자 ID가 있어 같은 아티스트만 지울 수 있다(ADR 11번).
        if (!bindingMatches(flow, request.bindingNonce())) {
            throw authorizationInvalid();
        }
        // 외부 호출은 트랜잭션 밖에서 한다. DB 커넥션을 잡고 플랫폼 응답을 기다리지 않는다.
        FetchedAccount fetched = youtubeGateway.redeem(artistId, request.code(), flow.codeVerifier());
        return transactionTemplate.execute(status -> save(
                artistId,
                ExternalAccountProvider.YOUTUBE,
                fetched.providerAccountId(),
                flow.expectedExternalAccountId()));
    }

    /**
     * 짧은 트랜잭션: 아티스트 잠금 → 지금 연결을 다시 읽음 → 판단 → 감사 기록(06 §5.2의 6).
     * 행을 바꾸는 판단은 잠금을 잡은 뒤 다시 읽은 값으로만 한다(ADR 8번 일반 규칙).
     */
    private ConnectResult save(
            UUID artistId, ExternalAccountProvider provider, String providerAccountId, UUID expectedExternalAccountId) {
        LockedArtist locked = repository.lockArtist(artistId);
        Instant now = clock.instant();
        Optional<ExternalAccount> current = repository.findByArtistAndProvider(artistId, provider);
        // 1. 지금 연결이 새로 확인한 계정과 같다: 아무것도 바꾸지 않는다
        if (current.isPresent() && current.get().providerAccountId().equals(providerAccountId)) {
            return new ConnectResult(current.get(), false);
        }
        // 2. 지금 연결 ID(없으면 null)가 시작 때 승인한 값과 다르다: 시작 뒤 다른 기기·탭에서 바뀌었다
        UUID currentId = current.map(ExternalAccount::id).orElse(null);
        if (!Objects.equals(currentId, expectedExternalAccountId)) {
            throw connectionChanged();
        }
        // 5. 승인한 기존 연결을 바꾼다: 조건부로 지우고 지운 행이 1개가 아니면 아무것도 저장하지 않는다
        if (current.isPresent()) {
            if (!repository.deleteById(locked, currentId)) {
                throw connectionChanged();
            }
            appendAudit(locked, artistId, currentId, provider, AuditEventType.DISCONNECTED, now);
        }
        // 3·4. 다른 아티스트가 같은 YouTube 채널을 가졌으면 저장소가 ALREADY_LINKED로 거절한다
        ExternalAccount created =
                new ExternalAccount(idGenerator.generate(), artistId, provider, providerAccountId, now, now);
        repository.insert(locked, created);
        appendAudit(locked, artistId, created.id(), provider, AuditEventType.CONNECTED, now);
        return new ConnectResult(created, true);
    }

    private void appendAudit(
            LockedArtist locked,
            UUID artistId,
            UUID accountId,
            ExternalAccountProvider provider,
            AuditEventType type,
            Instant now) {
        repository.appendAudit(
                locked,
                new AuditEvent(
                        idGenerator.generate(), artistId, accountId, provider, type, AuditActor.ARTIST, null, now));
    }

    private static ExternalAccountException connectionChanged() {
        return new ExternalAccountException(
                ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED, "다른 곳에서 연결이 바뀌었다");
    }

    /** 결합 쿠키 nonce의 digest를 저장된 digest와 상수 시간으로 비교한다. 쿠키가 없으면 불일치다. */
    private boolean bindingMatches(AuthorizationFlow flow, String bindingNonce) {
        if (bindingNonce == null || bindingNonce.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                flow.bindingDigest().getBytes(StandardCharsets.UTF_8),
                opaqueValues.digest(bindingNonce).getBytes(StandardCharsets.UTF_8));
    }

    private static ExternalAccountException authorizationInvalid() {
        return new ExternalAccountException(
                ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID, "연결 진행 정보가 없거나 맞지 않다");
    }

    private void requireEnabled(ExternalAccountProvider provider) {
        boolean enabled = switch (provider) {
            case YOUTUBE -> properties.youtube().enabled();
            case SPOTIFY -> properties.spotify().enabled();
        };
        if (!enabled) {
            throw new ExternalAccountException(
                    ExternalAccountFailure.EXTERNAL_PROVIDER_DISABLED, provider + " 연결이 설정으로 꺼져 있다");
        }
    }

    /** PKCE S256: BASE64URL(SHA-256(verifier)) (RFC 7636 §4.2). */
    private static String pkceChallenge(String codeVerifier) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
        }
    }
}
