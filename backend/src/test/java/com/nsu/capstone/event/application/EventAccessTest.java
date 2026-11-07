package com.nsu.capstone.event.application;

import static com.nsu.capstone.event.EventContentFixtures.with;
import static com.nsu.capstone.event.application.EventAccess.Operation.CANCEL;
import static com.nsu.capstone.event.application.EventAccess.Operation.DELETE;
import static com.nsu.capstone.event.application.EventAccess.Operation.RECOMMEND;
import static com.nsu.capstone.event.application.EventAccess.Operation.UPDATE;
import static com.nsu.capstone.event.application.EventAccess.Operation.VIEW;
import static com.nsu.capstone.event.domain.Visibility.PRIVATE;
import static com.nsu.capstone.event.domain.Visibility.PUBLIC;
import static com.nsu.capstone.global.exception.ErrorCode.ACCESS_DENIED;
import static com.nsu.capstone.global.exception.ErrorCode.EVENT_ALREADY_STARTED;
import static com.nsu.capstone.global.exception.ErrorCode.EVENT_CANCELLED;
import static com.nsu.capstone.global.exception.ErrorCode.EVENT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nsu.capstone.event.application.EventAccess.Operation;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Visibility;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 404 → 403 → 409(취소) → 409(시작) 검사 순서(03 §5.2, §9, §8.1)를 고정하는 순수 단위 테스트. Spring도 DB도
 * 쓰지 않는다. 표의 한 줄이 계약의 한 줄이다. 행사는 2026-11-06 18:00(KST)에 시작한다. "시작함"은 시계를 그
 * 순간으로 놓은 것이다.
 */
@DisplayName("EventAccess: 검사 순서")
class EventAccessTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final Clock BEFORE_START = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), SEOUL);
    static final Clock AT_START = Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"), SEOUL);
    static final UUID OWNER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    static Event event(Visibility visibility, boolean cancelled) {
        Event event = Event.register(UUID.randomUUID(), OWNER, with(b -> b.visibility = visibility), BEFORE_START);
        if (cancelled) {
            event.cancel(BEFORE_START);
        }
        return event;
    }

    /** null이면 통과다. */
    static ErrorCode outcome(Operation operation, UUID viewer, Visibility visibility, boolean seat,
                             boolean cancelled, boolean started) {
        try {
            operation.admit(event(visibility, cancelled), viewer, (e, v) -> seat, started ? AT_START : BEFORE_START);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    // 연산, 호출자, 공개 범위, PRIVATE 열람 판정(NSU-127 자리)이 허용하는가, 취소됐는가, 시작했는가 -> 기대 결과(null = 통과)
    static Stream<Arguments> table() {
        return Stream.of(
            // 수정: 열람(404) -> 등록자(403) -> 취소(409) -> 시작(409)
            Arguments.of(UPDATE, OWNER, PUBLIC, false, false, false, null),
            Arguments.of(UPDATE, OWNER, PUBLIC, false, true, false, EVENT_CANCELLED),
            Arguments.of(UPDATE, OWNER, PUBLIC, false, false, true, EVENT_ALREADY_STARTED),
            Arguments.of(UPDATE, OWNER, PUBLIC, false, true, true, EVENT_CANCELLED),
            Arguments.of(UPDATE, OWNER, PRIVATE, false, false, false, null),
            Arguments.of(UPDATE, OTHER, PUBLIC, false, true, true, ACCESS_DENIED),
            Arguments.of(UPDATE, OTHER, PRIVATE, false, true, true, EVENT_NOT_FOUND),
            Arguments.of(UPDATE, OTHER, PRIVATE, true, false, false, ACCESS_DENIED),
            // 취소: 이미 취소됐으면 시작 여부와 관계없이 통과(200), 아니면 시작 409
            Arguments.of(CANCEL, OWNER, PUBLIC, false, false, false, null),
            Arguments.of(CANCEL, OWNER, PUBLIC, false, false, true, EVENT_ALREADY_STARTED),
            Arguments.of(CANCEL, OWNER, PUBLIC, false, true, false, null),
            Arguments.of(CANCEL, OWNER, PUBLIC, false, true, true, null),
            Arguments.of(CANCEL, OTHER, PUBLIC, false, false, false, ACCESS_DENIED),
            Arguments.of(CANCEL, OTHER, PRIVATE, false, false, false, EVENT_NOT_FOUND),
            // 삭제: 상태와 시작 여부를 보지 않는다
            Arguments.of(DELETE, OWNER, PUBLIC, false, true, true, null),
            Arguments.of(DELETE, OWNER, PUBLIC, false, false, true, null),
            Arguments.of(DELETE, OTHER, PUBLIC, false, false, false, ACCESS_DENIED),
            Arguments.of(DELETE, OTHER, PRIVATE, false, false, false, EVENT_NOT_FOUND),
            // 추천 입력: 수정과 같은 순서(03 §8.1). 등록자만 한다.
            Arguments.of(RECOMMEND, OWNER, PUBLIC, false, false, false, null),
            Arguments.of(RECOMMEND, OWNER, PUBLIC, false, true, true, EVENT_CANCELLED),
            Arguments.of(RECOMMEND, OWNER, PUBLIC, false, false, true, EVENT_ALREADY_STARTED),
            Arguments.of(RECOMMEND, OTHER, PUBLIC, false, false, false, ACCESS_DENIED),
            Arguments.of(RECOMMEND, OTHER, PRIVATE, false, false, false, EVENT_NOT_FOUND),
            // 상세: 볼 수 있으면 된다. 상태는 보지 않는다.
            Arguments.of(VIEW, OTHER, PUBLIC, false, true, true, null),
            Arguments.of(VIEW, OTHER, PRIVATE, false, false, false, EVENT_NOT_FOUND),
            Arguments.of(VIEW, OTHER, PRIVATE, true, false, false, null),
            Arguments.of(VIEW, OWNER, PRIVATE, false, true, true, null)
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("table")
    @DisplayName("연산마다 계약의 검사 순서를 따른다")
    void followsTheContractOrder(Operation operation, UUID viewer, Visibility visibility, boolean seat,
                                 boolean cancelled, boolean started, ErrorCode expected) {
        assertThat(outcome(operation, viewer, visibility, seat, cancelled, started)).isEqualTo(expected);
    }

    @Test
    @DisplayName("표가 모든 연산을 다룬다. 연산을 더하면 이 표에도 줄을 더해야 한다")
    void tableCoversEveryOperation() {
        Set<Operation> covered = new HashSet<>();
        table().forEach(arguments -> covered.add((Operation) arguments.get()[0]));

        assertThat(covered).isEqualTo(EnumSet.allOf(Operation.class));
    }

    @Test
    @DisplayName("PRIVATE 열람 판정은 등록자가 아닌 사람이 PRIVATE 행사를 볼 때만 부른다")
    void privateSeatIsConsultedOnlyForANonOwnerOfAPrivateEvent() {
        AtomicInteger consulted = new AtomicInteger();

        for (Operation operation : Operation.values()) {
            for (UUID viewer : new UUID[] {OWNER, OTHER}) {
                for (Visibility visibility : Visibility.values()) {
                    try {
                        operation.admit(event(visibility, false), viewer, (e, v) -> {
                            consulted.incrementAndGet();
                            return false;
                        }, BEFORE_START);
                    } catch (BusinessException expected) {
                        // 거절은 표가 본다
                    }
                }
            }
        }

        assertThat(consulted.get()).isEqualTo(Operation.values().length); // 연산마다 (OTHER, PRIVATE) 한 번
    }

    @Test
    @DisplayName("load는 수정·취소·삭제에만 잠금 조회를 쓴다")
    void loadChoosesTheLockingQueryOnlyForMutatingOperations() {
        EventRepository repository = mock(EventRepository.class);
        EventAccess access = new EventAccess(repository, (e, v) -> false, BEFORE_START);
        Event event = event(PUBLIC, false);
        when(repository.findByIdAndDeletedAtIsNull(event.getId())).thenReturn(Optional.of(event));
        when(repository.findWithLockByIdAndDeletedAtIsNull(event.getId())).thenReturn(Optional.of(event));

        for (Operation operation : Operation.values()) {
            assertThat(access.load(event.getId(), OWNER, operation)).isSameAs(event);
        }

        verify(repository, times(2)).findByIdAndDeletedAtIsNull(event.getId()); // VIEW, RECOMMEND
        verify(repository, times(3)).findWithLockByIdAndDeletedAtIsNull(event.getId()); // UPDATE, CANCEL, DELETE
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("없거나 삭제한 행사는 어느 연산이든 404다")
    void missingEventIsNotFoundForEveryOperation() {
        EventRepository repository = mock(EventRepository.class);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(repository.findWithLockByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        EventAccess access = new EventAccess(repository, (e, v) -> true, BEFORE_START);

        for (Operation operation : Operation.values()) {
            assertThatThrownBy(() -> access.load(UUID.randomUUID(), OWNER, operation))
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(EVENT_NOT_FOUND));
        }
    }
}
