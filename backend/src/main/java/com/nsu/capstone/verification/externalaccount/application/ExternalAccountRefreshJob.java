package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.identity.application.UserIdGenerator;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.AuditActor;
import com.nsu.capstone.verification.externalaccount.domain.AuditEvent;
import com.nsu.capstone.verification.externalaccount.domain.AuditEventType;
import com.nsu.capstone.verification.externalaccount.domain.AuditReason;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.StaleWork;
import com.nsu.capstone.verification.externalaccount.provider.PublicIdCheck;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import com.nsu.capstone.verification.externalaccount.redis.CandidateCache;
import com.nsu.capstone.verification.externalaccount.redis.DisplayCache;
import com.nsu.capstone.verification.externalaccount.redis.RefreshJobState;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import com.nsu.capstone.verification.externalaccount.repository.LockedArtist;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * YouTube 채널·영상 ID 확인 작업(06 §11.1, ADR 14번). YouTube 정책이 ID도 30일 안에 갱신하거나 삭제하게 하므로,
 * 하루 한 번 확인한 지 7일 넘은 ID를 API 키로 50개씩 다시 조회한다.
 *
 * <p>"없음"은 YouTube가 온전한 2xx로 답했는데 그 ID가 응답에 없거나 {@code public}이 아닐 때만이다. 403, 4xx, 5xx, 시간 초과는
 * 확인 실패이고 행을 지우지 않는다. 온전하지 않은 2xx 응답(게이트웨이가 판정한다)에서는 {@code public}으로 온 ID만 갱신하고
 * 나머지는 지우지 않는다. 30일을 넘긴 행은 공개로 확인하지 못했으면 지운다. 행을 바꿀 때는 아티스트마다 트랜잭션을 따로 열고 그 아티스트의 잠금을 잡은 뒤, 같은 채널·영상일 때만 바꾼다.
 * 잠금을 3초 안에 잡지 못한 아티스트는 이번 실행에서 확인 실패로 두고 다음으로 넘어간다(06 §11.3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalAccountRefreshJob {

    private static final int BATCH_SIZE = 50;
    private static final Duration RECHECK_AFTER = Duration.ofDays(7);
    private static final Duration DELETE_AFTER = Duration.ofDays(30);
    /** 마지막 성공이 이보다 오래되면 알린다. 30일 삭제보다 10일 이상 먼저 알아채게 하는 값이다. */
    private static final Duration SUCCESS_STALE_AFTER = Duration.ofDays(3);
    /** 마지막 성공 기록이 없을 때, 가장 오래된 확인이 이보다 오래되면 알린다(확인 주기 7일 + 3일). */
    private static final Duration NO_RECORD_STALE_AFTER = Duration.ofDays(10);
    /** 가장 오래된 확인이 이보다 오래되면 알린다. */
    private static final Duration OLDEST_STALE_AFTER = Duration.ofDays(20);

    private final ExternalAccountProperties properties;
    private final ExternalAccountRepository repository;
    private final YoutubeGateway youtubeGateway;
    private final YoutubeQuota youtubeQuota;
    private final RefreshJobState jobState;
    private final UserIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final DisplayCache displayCache;
    private final CandidateCache candidateCache;
    private final Clock clock;

    /** 하루 한 번 돈다(한국 시간 새벽). 여러 서버가 같이 떠 있어도 실행 잠금이 한 서버만 돌게 한다. */
    @Scheduled(cron = "${external-account.refresh-job.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void runDaily() {
        run();
    }

    /**
     * 한 번 돈다. 다른 서버가 실행 잠금을 쥐고 있으면 아무것도 하지 않는다. 확인 실패 없이 끝까지 돈 실행만 마지막 성공을
     * 기록한다. 그래야 YouTube 장애나 키 문제가 이어질 때 3일 뒤부터 알아챌 수 있다.
     */
    public void run() {
        Optional<String> token = jobState.tryAcquire();
        if (token.isEmpty()) {
            log.info("다른 서버가 YouTube ID 확인 작업을 돌리고 있어 이번 실행은 건너뛴다");
            return;
        }
        try {
            Instant now = clock.instant();
            if (!properties.youtube().enabled()) {
                log.info("YouTube가 꺼져 있어 ID 확인 호출은 하지 않고, 30일 넘게 확인하지 못한 행만 정리한다");
            }
            warnIfStalled(now);
            boolean channelsVerified = checkChannels(now);
            boolean videosVerified = checkVideos(now);
            if (channelsVerified && videosVerified) {
                jobState.markSuccess(now);
            }
        } finally {
            jobState.release(token.get());
        }
    }

    /**
     * 작업이 멈춘 것을 30일 삭제보다 10일 이상 먼저 알아채게 한다. 알림 수단이 아직 없어 ERROR 로그 한 줄로 남긴다
     * (NSU-146 구조화 로그 이후 알림으로 잇는다).
     */
    private void warnIfStalled(Instant now) {
        Optional<Instant> lastSuccess = jobState.lastSuccess();
        Optional<Instant> oldest = repository.findOldestYoutubeRefreshedAt();
        boolean noRecentSuccess = lastSuccess.isPresent() && lastSuccess.get().isBefore(now.minus(SUCCESS_STALE_AFTER));
        // Redis를 비우면 마지막 성공 기록이 사라져 3일 검사가 꺼진다. 그때는 확인 주기(7일) + 3일을 넘긴 행으로 대신 잡는다
        boolean noRecordAndOverdue =
                lastSuccess.isEmpty() && oldest.isPresent() && oldest.get().isBefore(now.minus(NO_RECORD_STALE_AFTER));
        boolean oldestTooOld = oldest.isPresent() && oldest.get().isBefore(now.minus(OLDEST_STALE_AFTER));
        if (noRecentSuccess || noRecordAndOverdue || oldestTooOld) {
            log.error(
                    "ExternalAccountRefreshJob이 멈춘 것으로 보인다: 마지막 성공 {}, 가장 오래된 refreshed_at {}, 대상 행 수 {}",
                    lastSuccess.map(Instant::toString).orElse("없음"),
                    oldest.map(Instant::toString).orElse("없음"),
                    repository.countYoutubeRowsRefreshedBefore(now.minus(RECHECK_AFTER)));
        }
    }

    /** 확인 실패나 예산 부족 없이 모든 배치를 확인했으면 {@code true}다. */
    private boolean checkChannels(Instant now) {
        boolean allVerified = true;
        List<ExternalAccount> stale = repository.findYoutubeAccountsRefreshedBefore(now.minus(RECHECK_AFTER));
        for (List<ExternalAccount> batch : batches(stale)) {
            List<String> channelIds =
                    batch.stream().map(ExternalAccount::providerAccountId).toList();
            PublicIdCheck check = verified(youtubeGateway::findPublicChannelIds, channelIds);
            allVerified &= check != null && check.absenceTrusted();
            for (ExternalAccount account : batch) {
                if (check != null && check.publicIds().contains(account.providerAccountId())) {
                    allVerified &= refreshChannel(account, now);
                } else if (check != null && check.absenceTrusted()) {
                    allVerified &= deleteChannel(account, AuditReason.CHANNEL_NOT_PUBLIC, now);
                } else if (account.refreshedAt().isBefore(now.minus(DELETE_AFTER))) {
                    deleteChannel(account, AuditReason.UNVERIFIED_30_DAYS, now);
                }
            }
        }
        return allVerified;
    }

    private boolean checkVideos(Instant now) {
        boolean allVerified = true;
        List<StaleWork> stale = repository.findWorksRefreshedBefore(now.minus(RECHECK_AFTER));
        List<String> videoIds = new ArrayList<>(new LinkedHashSet<>(
                stale.stream().map(StaleWork::providerWorkId).toList()));
        for (List<String> batch : batches(videoIds)) {
            PublicIdCheck check = verified(youtubeGateway::findPublicVideoIds, batch);
            allVerified &= check != null && check.absenceTrusted();
            for (StaleWork work : stale) {
                if (!batch.contains(work.providerWorkId())) {
                    continue;
                }
                if (check != null && check.publicIds().contains(work.providerWorkId())) {
                    allVerified &= refreshWork(work, now);
                } else if (check != null && check.absenceTrusted()) {
                    allVerified &= deleteWork(work);
                } else if (work.refreshedAt().isBefore(now.minus(DELETE_AFTER))) {
                    deleteWork(work);
                }
            }
        }
        return allVerified;
    }

    /**
     * 응답을 받았으면 그 결과를, 실패(403, 4xx, 5xx, 시간 초과)하거나 하루 예산을 확보하지 못하면 {@code null}을 돌려준다.
     * 응답이 온전하지 않으면 공개 ID는 그대로 쓰되 "없음"은 믿지 않으므로, 운영자가 알아채도록 ERROR 한 줄을 남긴다
     * (ID와 응답 본문은 넣지 않는다). 작업은 호출 한 번(ID 50개 이하)에 1 unit를 쓰고 호출자 상한 없이 10,000까지 쓸 수 있다.
     */
    private PublicIdCheck verified(Function<List<String>, PublicIdCheck> call, List<String> ids) {
        // 꺼진 기능은 확인 호출을 하지 않는다. 30일 미확인 삭제는 확인 실패와 같은 길로 그대로 한다(06 §11.1)
        if (!properties.youtube().enabled()) {
            return null;
        }
        if (!youtubeQuota.tryReserveForJob(1)) {
            log.warn("YouTube 하루 예산이 차서 ID 확인을 건너뛴다: {}개", ids.size());
            return null;
        }
        try {
            PublicIdCheck check = call.apply(ids);
            if (!check.absenceTrusted()) {
                log.error(
                        "YouTube ID 확인 응답이 온전하지 않아 \"없음\" 판정을 보류한다: 작업 {}, 요청한 ID {}개, 사유 {}",
                        ExternalAccountRefreshJob.class.getSimpleName(),
                        ids.size(),
                        check.incompleteReason());
            }
            return check;
        } catch (ExternalAccountException e) {
            log.warn("YouTube ID 확인에 실패했다: {}개, {}", ids.size(), e.getFailure());
            return null;
        }
    }

    /**
     * 아래 네 메서드는 아티스트 잠금을 잡아 처리했으면 {@code true}, 잠금 대기를 넘겨 건너뛰었거나 DB 반영 오류가 났으면
     * {@code false}를 돌려준다. {@code false}가 하나라도 있는 실행은 마지막 성공으로 기록하지 않는다.
     */
    private boolean refreshChannel(ExternalAccount account, Instant now) {
        return handled(inLockOf(
                account.artistId(),
                locked -> repository.refreshAccount(locked, account.id(), account.providerAccountId(), now)));
    }

    private boolean deleteChannel(ExternalAccount account, AuditReason reason, Instant now) {
        LockOutcome outcome = inLockOf(account.artistId(), locked -> {
            if (!repository.deleteAccountOfChannel(locked, account.id(), account.providerAccountId())) {
                return false;
            }
            repository.appendAudit(
                    locked,
                    new AuditEvent(
                            idGenerator.generate(),
                            account.artistId(),
                            account.id(),
                            ExternalAccountProvider.YOUTUBE,
                            AuditEventType.DISCONNECTED,
                            AuditActor.SYSTEM,
                            reason,
                            now));
            return true;
        });
        if (outcome == LockOutcome.CHANGED) {
            displayCache.evict(account.id());
            candidateCache.evict(account.id());
        }
        return handled(outcome);
    }

    private boolean refreshWork(StaleWork work, Instant now) {
        return handled(inLockOf(
                work.artistId(),
                locked -> repository.refreshWork(locked, work.externalAccountId(), work.providerWorkId(), now)));
    }

    private boolean deleteWork(StaleWork work) {
        LockOutcome outcome = inLockOf(
                work.artistId(),
                locked -> repository.deleteWork(locked, work.externalAccountId(), work.providerWorkId()));
        if (outcome == LockOutcome.CHANGED) {
            displayCache.evict(work.externalAccountId());
        }
        return handled(outcome);
    }

    private static boolean handled(LockOutcome outcome) {
        return outcome == LockOutcome.CHANGED || outcome == LockOutcome.UNCHANGED;
    }

    /**
     * 잠금 안에서 한 일의 결과. {@code UNCHANGED}는 그사이 행이 바뀌어 건드리지 않은 경우다. 잠금 대기를 넘긴 {@code BUSY}와
     * DB 반영 오류인 {@code FAILED}는 이번 실행에서 확인하지 못한 것이다(ADR 14번 "반영 오류도 실패로 센다").
     */
    private enum LockOutcome {
        CHANGED,
        UNCHANGED,
        BUSY,
        FAILED
    }

    /**
     * 아티스트마다 트랜잭션을 따로 열고 그 아티스트의 잠금을 잡은 뒤 실행한다. 한 행의 오류가 다른 행을 막지 않는다.
     * 잠금을 3초 안에 잡지 못하면(06 §11.3) 그 아티스트는 이번 실행에서 확인하지 못한 것이라 {@code BUSY}를 돌려준다.
     */
    private LockOutcome inLockOf(java.util.UUID artistId, Function<LockedArtist, Boolean> work) {
        try {
            boolean changed = Boolean.TRUE.equals(
                    transactionTemplate.execute(status -> work.apply(repository.lockArtist(artistId))));
            return changed ? LockOutcome.CHANGED : LockOutcome.UNCHANGED;
        } catch (RuntimeException e) {
            if (e instanceof ExternalAccountException failure
                    && failure.getFailure() == ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY) {
                log.warn("아티스트 잠금을 잡지 못해 이번 실행에서는 건너뛴다: 아티스트 {}", artistId);
                return LockOutcome.BUSY;
            }
            // 예외 메시지에는 연결 정보나 SQL이 들 수 있고 예외 객체는 스택까지 찍으므로, 종류와 SQL state만 남긴다
            log.error(
                    "YouTube ID 확인 결과를 반영하지 못했다: 아티스트 {}, 예외 {}, SQL state {}",
                    artistId,
                    e.getClass().getSimpleName(),
                    sqlStateOf(e));
            return LockOutcome.FAILED;
        }
    }

    private static String sqlStateOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "없음";
    }

    private static <T> List<List<T>> batches(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < items.size(); from += BATCH_SIZE) {
            batches.add(items.subList(from, Math.min(from + BATCH_SIZE, items.size())));
        }
        return batches;
    }
}
