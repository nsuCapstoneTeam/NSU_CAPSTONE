package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.PublicRequestLimitProperties;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.verification.OtpDigestService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class IdentityVerificationServiceIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final String STORE_ID = "store-1";
    private static final String CHANNEL_KEY = "channel-key-1";
    private static final byte[] NAME_KEY = new byte[32];

    @Autowired
    IdentityVerificationRepository repository;

    @Autowired
    RedisPublicRequestLimitStore limits;

    @Autowired
    StringRedisTemplate redis;

    MockIdentityVerificationProvider provider;
    IdentityVerificationService service;

    @BeforeEach
    void assembleService() {
        Set<String> keys = redis.keys("signup:limit:v2:identity-verification:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        provider = MockIdentityVerificationProvider.notCompletedUnlessScripted();
        service = serviceWith(provider, 10);
    }

    IdentityVerificationService serviceWith(IdentityVerificationProvider provider, int hourlyLimit) {
        return new IdentityVerificationService(
                repository,
                provider,
                limits,
                new NameCipher(NAME_KEY),
                clock,
                uuidV7Generator,
                new IdentityVerificationSettings(STORE_ID, CHANNEL_KEY, Duration.ofMinutes(15), hourlyLimit));
    }

    @Test
    void 제공자가_확인했으면_완료는_SANDBOX_기록을_만들고_확인_시각을_돌려준다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        clock.set(NOW.plusSeconds(120));

        IdentityStatus status = service.complete(artist.getId(), id);

        assertThat(status).isEqualTo(IdentityStatus.verifiedAt(NOW.plusSeconds(120)));
        assertThat(provider.lookupCount(id)).isEqualTo(1);
        assertThat(repository.findAttempt(id).orElseThrow().status()).isEqualTo(AttemptStatus.VERIFIED);
        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(stored.source()).isEqualTo(IdentitySource.SANDBOX);
        assertThat(stored.attemptId()).isEqualTo(id);
        assertThat(stored.mockNameCiphertext()).isNull();
    }

    @Test
    void 창이_끝나지_않았으면_시도를_열어_두고_같은_ID로_다시_완료하면_성공한다() {
        IdentityVerificationId id = start();
        provider.script(
                id, new ProviderOutcome.NotCompleted(), new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_NOT_COMPLETED);
        assertThat(repository.findAttempt(id).orElseThrow().status()).isEqualTo(AttemptStatus.PENDING);
        assertThat(repository.findVerification(artist.getId())).isEmpty();

        assertThat(service.complete(artist.getId(), id).isVerified()).isTrue();
    }

    @Test
    void 제공자가_실패로_끝냈으면_FAILED로_닫고_다시_보내도_제공자를_부르지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Failed());

        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_FAILED);
        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_FAILED);

        IdentityVerificationAttempt attempt = repository.findAttempt(id).orElseThrow();
        assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
        assertThat(provider.lookupCount(id)).isEqualTo(1);
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 받을_수_없는_채널이면_REJECTED로_이유와_함께_닫고_다시_보내도_제공자를_부르지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Rejected(RejectionReason.CHANNEL_NOT_TEST));

        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_REJECTED);
        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_REJECTED);

        IdentityVerificationAttempt attempt = repository.findAttempt(id).orElseThrow();
        assertThat(attempt.status()).isEqualTo(AttemptStatus.REJECTED);
        assertThat(attempt.rejectionReason()).isEqualTo(RejectionReason.CHANNEL_NOT_TEST);
        assertThat(provider.lookupCount(id)).isEqualTo(1);
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 제공자를_쓸_수_없으면_시도를_열어_두고_같은_ID로_다시_완료하면_성공한다() {
        IdentityVerificationId id = start();
        provider.script(
                id,
                new ProviderOutcome.Unavailable(UnavailableCause.TIMEOUT),
                new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_UNAVAILABLE);
        assertThat(repository.findAttempt(id).orElseThrow().status()).isEqualTo(AttemptStatus.PENDING);

        assertThat(service.complete(artist.getId(), id).isVerified()).isTrue();
    }

    @Test
    void 없는_시도와_다른_회원의_시도는_모두_찾을_수_없다고_답하고_제공자를_부르지_않는다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        IdentityVerificationId othersPending = IdentityVerificationId.parse(
                service.start(other.getId()).identityVerificationId());
        IdentityVerificationId othersFailed = new IdentityVerificationId(insertAttempt(other, "FAILED"));
        IdentityVerificationId unknown = new IdentityVerificationId(uuidV7Generator.generate());

        for (IdentityVerificationId id : java.util.List.of(othersPending, othersFailed, unknown)) {
            assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND);
        }

        assertThat(provider.totalLookups()).isZero();
        assertThat(repository.findAttempt(othersPending).orElseThrow().status()).isEqualTo(AttemptStatus.PENDING);
        assertThat(repository.findVerification(other.getId())).isEmpty();
    }

    @Test
    void 만료_시각에_도착한_완료는_만료됐다고_답하고_제공자를_부르지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        clock.set(NOW.plus(Duration.ofMinutes(15)));
        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_EXPIRED);
        clock.set(NOW.plus(Duration.ofMinutes(16)));
        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_EXPIRED);

        assertThat(provider.totalLookups()).isZero();
        assertThat(repository.findAttempt(id).orElseThrow().status()).isEqualTo(AttemptStatus.PENDING);
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 만료_1초_전에_도착한_완료는_제공자가_2초_걸려도_성공한다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        Instant arrivedAt = NOW.plus(Duration.ofMinutes(15)).minusSeconds(1);
        clock.set(arrivedAt);
        provider.onLookup(() -> clock.set(arrivedAt.plusSeconds(2)));

        IdentityStatus status = service.complete(artist.getId(), id);

        assertThat(status).isEqualTo(IdentityStatus.verifiedAt(arrivedAt.plusSeconds(2)));
        assertThat(repository.findAttempt(id).orElseThrow().status()).isEqualTo(AttemptStatus.VERIFIED);
    }

    @Test
    void 이미_성공한_시도를_다시_보내면_같은_결과를_주고_제공자를_다시_부르지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        IdentityStatus first = service.complete(artist.getId(), id);
        clock.set(NOW.plusSeconds(60));

        IdentityStatus again = service.complete(artist.getId(), id);

        assertThat(again).isEqualTo(first);
        assertThat(provider.lookupCount(id)).isEqualTo(1);
    }

    @Test
    void 계정이_이미_본인확인을_마쳤으면_실패한_시도와_만료된_시도의_완료도_성공으로_끝난다() {
        IdentityVerificationId done = start();
        IdentityVerificationId expired = start();
        provider.script(done, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        IdentityStatus verified = service.complete(artist.getId(), done);
        IdentityVerificationId failed = new IdentityVerificationId(insertAttempt(artist, "FAILED"));
        IdentityVerificationId rejected = new IdentityVerificationId(uuidV7Generator.generate());
        insertRawAttempt(rejected, "REJECTED", "CHANNEL_NOT_TEST");
        clock.set(NOW.plus(Duration.ofMinutes(20)));

        for (IdentityVerificationId id : java.util.List.of(failed, rejected, expired)) {
            assertThat(service.complete(artist.getId(), id)).isEqualTo(verified);
            assertThat(provider.lookupCount(id)).isZero();
        }
    }

    @Test
    void 본인확인을_마친_계정의_시작은_이미_확인됐다고_거절하고_시도를_만들지_않는다() {
        IdentityVerificationId done = start();
        provider.script(done, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        service.complete(artist.getId(), done);

        assertThatThrownBy(() -> service.start(artist.getId()))
                .isInstanceOfSatisfying(
                        IdentityVerificationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(IdentityVerificationReason.IDENTITY_ALREADY_VERIFIED));
        assertThat(attemptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void ACTIVE가_아닌_계정은_시작과_완료를_거절하고_시도도_제공자_호출도_만들지_않는다(String accountStatus) {
        IdentityVerificationId pending = start();
        provider.script(pending, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", accountStatus, artist.getId());

        assertThatThrownBy(() -> service.start(artist.getId()))
                .isInstanceOfSatisfying(
                        IdentityVerificationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(IdentityVerificationReason.ACCOUNT_NOT_ACTIVE));
        assertCompletionFails(pending, IdentityVerificationReason.ACCOUNT_NOT_ACTIVE);

        assertThat(attemptCount()).isEqualTo(1);
        assertThat(provider.totalLookups()).isZero();
        assertThat(repository.findAttempt(pending).orElseThrow().status()).isEqualTo(AttemptStatus.PENDING);
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 상태_조회는_확인_전에는_false와_null이고_확인_뒤에는_true와_시각이며_계정_상태와_상관없다() {
        assertThat(service.status(artist.getId())).isEqualTo(IdentityStatus.notVerified());

        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        clock.set(NOW.plusSeconds(30));
        service.complete(artist.getId(), id);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());

        assertThat(service.status(artist.getId())).isEqualTo(IdentityStatus.verifiedAt(NOW.plusSeconds(30)));
    }

    @Test
    void 시작과_완료를_합쳐_11번째_요청은_한도_초과이고_시도도_제공자_호출도_만들지_않는다() {
        IdentityVerificationId id = start();
        for (int count = 2; count <= 10; count++) {
            assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_NOT_COMPLETED);
        }

        assertThatThrownBy(() -> service.complete(artist.getId(), id)).isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException limited = (BusinessException) exception;
                    assertThat(limited.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(limited.getRetryAfterSeconds()).isEqualTo(3600L);
                });
        assertThatThrownBy(() -> service.start(artist.getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));

        assertThat(attemptCount()).isEqualTo(1);
        assertThat(provider.lookupCount(id)).isEqualTo(9);
    }

    @Test
    void 한도_검사는_계정_상태와_시도_조회보다_먼저다() {
        IdentityVerificationService tight = serviceWith(provider, 1);
        tight.start(artist.getId());
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());
        IdentityVerificationId unknown = new IdentityVerificationId(uuidV7Generator.generate());

        assertThatThrownBy(() -> tight.complete(artist.getId(), unknown))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void Redis를_쓸_수_없으면_RATE_LIMIT_UNAVAILABLE이고_시도도_제공자_호출도_만들지_않는다() {
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("unavailable"));
        RedisPublicRequestLimitStore brokenLimits = new RedisPublicRequestLimitStore(
                brokenRedis, mock(OtpDigestService.class), new PublicRequestLimitProperties(30, 30, 30, 10), clock);
        IdentityVerificationId id = start();
        IdentityVerificationService withBrokenRedis = new IdentityVerificationService(
                repository,
                provider,
                brokenLimits,
                new NameCipher(NAME_KEY),
                clock,
                uuidV7Generator,
                new IdentityVerificationSettings(STORE_ID, CHANNEL_KEY, Duration.ofMinutes(15), 10));

        for (Runnable request : java.util.List.<Runnable>of(
                () -> withBrokenRedis.start(artist.getId()), () -> withBrokenRedis.complete(artist.getId(), id))) {
            assertThatThrownBy(request::run)
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
                        assertThat(exception.getRetryAfterSeconds()).isNull();
                    });
        }

        assertThat(attemptCount()).isEqualTo(1);
        assertThat(provider.totalLookups()).isZero();
    }

    @Test
    void 제공자_응답을_기다리는_사이_다른_요청이_시도를_닫았으면_그_결과로_답하고_기록을_만들지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        provider.onLookup(() -> repository.closeFailed(id, clock.instant()));

        assertCompletionFails(id, IdentityVerificationReason.IDENTITY_VERIFICATION_FAILED);

        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void MOCK_성공은_이름을_암호화해_저장하고_복호화하면_돌아오며_평문은_DB에_없다() {
        IdentityVerificationId id = start();
        IdentityName name = IdentityName.of("김동엽");
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.MOCK, name));

        service.complete(artist.getId(), id);

        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(stored.source()).isEqualTo(IdentitySource.MOCK);
        assertThat(new NameCipher(NAME_KEY).decrypt(stored.mockNameCiphertext(), artist.getId()))
                .isEqualTo(name);
        assertThat(new String(stored.mockNameCiphertext(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("김동엽");
    }

    @Test
    void 한도를_높이면_같은_시도의_완료_20건이_동시에_와도_모두_성공하고_기록은_하나다() throws Exception {
        IdentityVerificationService roomy = serviceWith(provider, 100);
        IdentityVerificationId id = IdentityVerificationId.parse(
                roomy.start(artist.getId()).identityVerificationId());
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        List<Object> results = runConcurrently(20, () -> roomy.complete(artist.getId(), id));

        assertThat(results).hasSize(20).allSatisfy(result -> assertThat(result)
                .isInstanceOf(IdentityStatus.class));
        assertThat(results.stream().map(IdentityStatus.class::cast).map(IdentityStatus::verifiedAt).distinct())
                .hasSize(1);
        assertThat(verificationCount()).isEqualTo(1);
    }

    @Test
    void 기본_한도에서_시작_1건_뒤_완료_20건이_동시에_오면_9건만_성공하고_11건은_제한되며_기록은_하나다() throws Exception {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        List<Object> results = runConcurrently(20, () -> service.complete(artist.getId(), id));

        assertThat(results.stream().filter(IdentityStatus.class::isInstance)).hasSize(9);
        List<Object> limited = results.stream().filter(BusinessException.class::isInstance).toList();
        assertThat(limited).hasSize(11).allSatisfy(result -> {
            BusinessException exception = (BusinessException) result;
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
            assertThat(exception.getRetryAfterSeconds()).isEqualTo(3600L);
        });
        assertThat(verificationCount()).isEqualTo(1);
        assertThat(attemptCount()).isEqualTo(1);
        assertThat(provider.lookupCount(id)).isBetween(1, 9);
    }

    @Test
    void 한_회원의_두_시도를_동시에_완료하면_둘_다_성공하고_기록은_하나다() throws Exception {
        IdentityVerificationService roomy = serviceWith(provider, 100);
        IdentityVerificationId first = IdentityVerificationId.parse(
                roomy.start(artist.getId()).identityVerificationId());
        IdentityVerificationId second = IdentityVerificationId.parse(
                roomy.start(artist.getId()).identityVerificationId());
        provider.script(first, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        provider.script(second, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));

        AtomicInteger turn = new AtomicInteger();
        List<Object> results = runConcurrently(
                2, () -> roomy.complete(artist.getId(), turn.getAndIncrement() == 0 ? first : second));

        assertThat(results).hasSize(2).allSatisfy(result -> assertThat(result).isInstanceOf(IdentityStatus.class));
        assertThat(verificationCount()).isEqualTo(1);
    }

    /**
     * 계약 07 §3·§10의 판정 순서 조합 표. 여섯 조건의 모든 조합에서 가장 앞에 걸리는 순서의 응답이 나오고, 그 뒤 순서의 일(제공자 호출, 시도·기록 변경)은
     * 일어나지 않는다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("orderCombinations")
    void 판정_순서_조합마다_가장_앞에_걸리는_순서의_응답을_주고_뒤_순서의_일은_일어나지_않는다(Conditions conditions) {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        IdentityVerificationId target = new IdentityVerificationId(uuidV7Generator.generate());
        UUID attemptOwner = conditions.attemptIsOthers() ? other.getId() : artist.getId();
        jdbcTemplate.update(
                """
                INSERT INTO identity_verification_attempts
                    (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                VALUES (?, ?, ?, NULL, now(), now() + interval '15 minutes', CASE WHEN ? THEN now() END)
                """,
                target.value(),
                attemptOwner,
                conditions.attemptIsClosed() ? "FAILED" : "PENDING",
                conditions.attemptIsClosed());
        if (conditions.accountAlreadyVerified()) {
            UUID earlier = insertAttempt(artist, "VERIFIED");
            insertRecord(artist, earlier, "SANDBOX", null);
        }
        if (conditions.accountIsInactive()) {
            jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());
        }
        if (conditions.attemptIsExpired()) {
            clock.set(Instant.now().plus(Duration.ofDays(1)));
        }
        if (conditions.limitExceeded()) {
            for (int count = 0; count < 10; count++) {
                limits.identityVerification(artist.getId(), 10);
            }
        }
        provider.script(target, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        int attemptsBefore = attemptCount();
        int recordsBefore = verificationCount();

        Object outcome;
        try {
            outcome = service.complete(artist.getId(), target);
        } catch (RuntimeException failure) {
            outcome = failure;
        }

        if (conditions.limitExceeded()) {
            assertThat(outcome).isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                            exception.getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
        } else if (conditions.accountIsInactive()) {
            assertFailureReason(outcome, IdentityVerificationReason.ACCOUNT_NOT_ACTIVE);
        } else if (conditions.attemptIsOthers()) {
            assertFailureReason(outcome, IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND);
        } else if (conditions.accountAlreadyVerified()) {
            assertThat(outcome).isInstanceOf(IdentityStatus.class);
        } else if (conditions.attemptIsClosed()) {
            assertFailureReason(outcome, IdentityVerificationReason.IDENTITY_VERIFICATION_FAILED);
        } else if (conditions.attemptIsExpired()) {
            assertFailureReason(outcome, IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_EXPIRED);
        } else {
            assertThat(outcome).isInstanceOf(IdentityStatus.class);
        }
        boolean reachesProvider = !conditions.limitExceeded()
                && !conditions.accountIsInactive()
                && !conditions.attemptIsOthers()
                && !conditions.accountAlreadyVerified()
                && !conditions.attemptIsClosed()
                && !conditions.attemptIsExpired();
        assertThat(provider.totalLookups()).isEqualTo(reachesProvider ? 1 : 0);
        assertThat(attemptCount()).isEqualTo(attemptsBefore);
        assertThat(verificationCount()).isEqualTo(reachesProvider ? recordsBefore + 1 : recordsBefore);
    }

    private static void assertFailureReason(Object outcome, IdentityVerificationReason reason) {
        assertThat(outcome).isInstanceOfSatisfying(
                IdentityVerificationException.class,
                exception -> assertThat(exception.getReason()).isEqualTo(reason));
    }

    record Conditions(
            boolean limitExceeded,
            boolean accountIsInactive,
            boolean attemptIsOthers,
            boolean accountAlreadyVerified,
            boolean attemptIsClosed,
            boolean attemptIsExpired) {

        @Override
        public String toString() {
            return "한도초과=%s 비활성=%s 남의시도=%s 이미확인=%s 닫힘=%s 만료=%s"
                    .formatted(
                            limitExceeded,
                            accountIsInactive,
                            attemptIsOthers,
                            accountAlreadyVerified,
                            attemptIsClosed,
                            attemptIsExpired);
        }
    }

    static Stream<Conditions> orderCombinations() {
        return IntStream.range(0, 64).mapToObj(bits -> new Conditions(
                (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0));
    }

    /** 모든 작업이 문 앞에 모인 뒤 한꺼번에 출발한다. 성공은 값으로, 실패는 예외 객체로 모은다. */
    private static List<Object> runConcurrently(int count, Callable<IdentityStatus> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private int verificationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity_verifications WHERE user_id = ?", Integer.class, artist.getId());
    }

    @Test
    void 완료하면_시도_ID와_출처만_로그로_남기고_실명은_어떤_로그에도_남기지_않는다() {
        IdentityVerificationId id = start();
        provider.script(id, new ProviderOutcome.Verified(IdentitySource.MOCK, IdentityName.of("김동엽")));

        try (LogCapture logs = new LogCapture()) {
            service.complete(artist.getId(), id);

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
                        assertThat(event.getFormattedMessage())
                                .contains(id.external())
                                .contains("MOCK");
                    });
            assertThat(logs.everything()).doesNotContain("김동엽");
        }
    }

    private int attemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity_verification_attempts WHERE user_id = ?", Integer.class, artist.getId());
    }

    private void insertRawAttempt(IdentityVerificationId id, String status, String reason) {
        jdbcTemplate.update("""
                INSERT INTO identity_verification_attempts
                    (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                VALUES (?, ?, ?, ?, now(), now() + interval '15 minutes', now())
                """, id.value(), artist.getId(), status, reason);
    }

    private void assertCompletionFails(IdentityVerificationId id, IdentityVerificationReason reason) {
        assertThatThrownBy(() -> service.complete(artist.getId(), id))
                .isInstanceOfSatisfying(
                        IdentityVerificationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(reason));
    }

    private IdentityVerificationId start() {
        return IdentityVerificationId.parse(service.start(artist.getId()).identityVerificationId());
    }

    @Test
    void 시작하면_PENDING_시도를_만들고_PortOne_SDK에_넘길_값을_돌려준다() {
        StartedAttempt started = service.start(artist.getId());

        assertThat(started.identityVerificationId()).matches("[0-9a-f]{32}");
        assertThat(started.storeId()).isEqualTo(STORE_ID);
        assertThat(started.channelKey()).isEqualTo(CHANNEL_KEY);
        assertThat(started.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        IdentityVerificationAttempt attempt = repository
                .findAttempt(IdentityVerificationId.parse(started.identityVerificationId()))
                .orElseThrow();
        assertThat(attempt.status()).isEqualTo(AttemptStatus.PENDING);
        assertThat(attempt.userId()).isEqualTo(artist.getId());
        assertThat(attempt.createdAt()).isEqualTo(NOW);
        assertThat(attempt.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(provider.totalLookups()).isZero();
    }
}
