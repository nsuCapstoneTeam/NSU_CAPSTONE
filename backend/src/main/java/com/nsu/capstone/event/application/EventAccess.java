package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Visibility;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * id로 행사 하나를 읽는 유일한 길(ADR event_management/04).
 */
@Component
public class EventAccess {

    /**
     * 호출하는 쪽의 의도. 행 잠금, 누가 할 수 있는지, 행사 상태가 허용하는지는 의도에서 정해지고 호출하는 쪽이
     * 고르지 않는다. 03 행사 API §5.2의 표와 §9의 검사 순서를 그대로 옮겼다.
     */
    public enum Operation {

        /** 상세 조회. 볼 수 있으면 된다. */
        VIEW(Lock.NONE, Who.VIEWER, State.ANY),
        /** 수정. 취소 409 → 시작 409. 행을 잠가 동시에 온 취소·수정이 서로를 덮어쓰지 못하게 한다(03 §5.4). */
        UPDATE(Lock.WRITE, Who.OWNER, State.OPEN_AND_NOT_STARTED),
        /** 취소. 이미 취소됐으면 시작 여부와 관계없이 통과하고(200, 03 §5.5), 아니면 시작 409다. */
        CANCEL(Lock.WRITE, Who.OWNER, State.CANCEL_IS_REPEATABLE),
        /** 삭제. 상태와 시작 여부를 보지 않는다(03 §5.6). */
        DELETE(Lock.WRITE, Who.OWNER, State.ANY),
        /** 추천 입력(03 §8.1). 행사를 한 번 읽어 고정할 뿐이라 잠그지 않는다(03 §8.4). */
        RECOMMEND(Lock.NONE, Who.OWNER, State.OPEN_AND_NOT_STARTED);

        private enum Lock { NONE, WRITE }

        private enum Who { VIEWER, OWNER }

        private enum State { ANY, OPEN_AND_NOT_STARTED, CANCEL_IS_REPEATABLE }

        private final Lock lock;
        private final Who who;
        private final State state;

        Operation(Lock lock, Who who, State state) {
            this.lock = lock;
            this.who = who;
            this.state = state;
        }

        boolean locksRow() {
            return lock == Lock.WRITE;
        }

        /**
         * 이미 읽은 행사에 검사를 순서대로 적용하는 순수 함수다. ① 볼 수 없으면 404, ② 볼 수 있지만 등록자가
         * 아니면 403(등록자만 하는 일일 때), ③ 상태. 순서가 바뀌면 PRIVATE 행사의 존재가 드러나거나 취소된
         * 행사의 재취소가 409가 된다. 시작 판정은 {@link Event#hasStarted(Clock)}다.
         */
        void admit(Event event, UUID userId, CanViewPrivateEvent seat, Clock clock) {
            boolean owner = event.getEventPartnerId().equals(userId);
            boolean visible = owner || event.getVisibility() == Visibility.PUBLIC || seat.canView(event, userId);
            if (!visible) {
                throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
            }
            if (who == Who.OWNER && !owner) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            if (state == State.ANY) {
                return;
            }
            if (event.isCancelled()) {
                if (state == State.OPEN_AND_NOT_STARTED) {
                    throw new BusinessException(ErrorCode.EVENT_CANCELLED);
                }
                return;
            }
            if (event.hasStarted(clock)) {
                throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
            }
        }
    }

    private final EventRepository events;
    private final CanViewPrivateEvent seat;
    private final Clock clock;

    EventAccess(EventRepository events, CanViewPrivateEvent seat, Clock clock) {
        this.events = events;
        this.seat = seat;
        this.clock = clock;
    }

    /**
     * 행사를 읽어 {@code operation}이 허용되는지 판정하고 돌려준다. 없거나 삭제됐으면 404다. UPDATE·CANCEL·
     * DELETE는 행을 잠그고 읽는다(잠금은 트랜잭션이 필요하다). 잠금은 판정 전에 잡히므로 등록자가 아닌 사람의
     * 요청도 잠깐 잠금을 잡았다가 404·403으로 끝난다. 트랜잭션은 열지 않고 부르는 쪽의 것을 쓴다.
     */
    public Event load(UUID eventId, UUID userId, Operation operation) {
        Optional<Event> found = operation.locksRow()
            ? events.findWithLockByIdAndDeletedAtIsNull(eventId)
            : events.findByIdAndDeletedAtIsNull(eventId);
        Event event = found.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        operation.admit(event, userId, seat, clock);
        return event;
    }
}
