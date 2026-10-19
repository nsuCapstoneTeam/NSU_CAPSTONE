package com.nsu.capstone.event.application;

import com.nsu.capstone.event.config.EventRegistrationProperties;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.region.RegionRepository;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.support.UuidV7Generator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 행사 등록과 멱등 키 처리(03 행사 API §5.3.1, ADR event_management/03).
 *
 * <p>① 키로 먼저 조회해 다시 온 요청에 빨리 답하고, ② 없으면 INSERT로 키를 선점한다. 중복은
 * (등록자, 키) 유니크 인덱스가 막는다. 유니크 위반 뒤의 트랜잭션은 중단되므로 롤백하고 새
 * 트랜잭션에서 키로 다시 조회해 답한다. 이 메서드 자체는 트랜잭션 밖이며 단계마다 새 트랜잭션을 연다.
 */
@Service
public class EventRegistrationService {

    private static final String KEY_INDEX = "uk_events_partner_idempotency_key";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final Set<String> TIMEOUT_STATES = Set.of(
        "55P03", // lock_not_available: lock_timeout 초과
        "57014" // query_canceled: statement_timeout 초과
    );

    private final EventRepository eventRepository;
    private final RegionRepository regionRepository;
    private final UuidV7Generator idGenerator;
    private final Clock clock;
    private final EventRegistrationProperties properties;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    @PersistenceContext
    private EntityManager entityManager;

    EventRegistrationService(
        EventRepository eventRepository,
        RegionRepository regionRepository,
        UuidV7Generator idGenerator,
        Clock clock,
        EventRegistrationProperties properties,
        PlatformTransactionManager transactionManager
    ) {
        this.eventRepository = eventRepository;
        this.regionRepository = regionRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.properties = properties;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public Event register(UUID eventPartnerId, UUID idempotencyKey, EventContent content) {
        try {
            requireKnownRegion(content);
            return registerWithKey(eventPartnerId, idempotencyKey, content);
        } catch (RuntimeException exception) {
            if (hasSqlState(exception, TIMEOUT_STATES)) {
                throw new BusinessException(ErrorCode.EVENT_REGISTRATION_UNAVAILABLE);
            }
            throw exception;
        }
    }

    private Event registerWithKey(UUID eventPartnerId, UUID idempotencyKey, EventContent content) {
        String hash = EventContentHash.of(content);

        Optional<Event> existing = find(eventPartnerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        try {
            return writeTransaction.execute(status ->
                create(eventPartnerId, idempotencyKey, content, hash));
        } catch (RuntimeException exception) {
            if (!isKeyConflict(exception)) {
                throw exception;
            }
        }
        // 같은 키로 먼저 저장한 요청이 있다. 롤백한 뒤 새 트랜잭션에서 그 결과를 읽어 답한다.
        Event winner = find(eventPartnerId, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("유니크 위반 뒤 키로 행사를 찾을 수 없다"));
        return replay(winner, hash);
    }

    private Optional<Event> find(UUID eventPartnerId, UUID idempotencyKey) {
        return readTransaction.execute(status -> {
            applyTimeLimit();
            return eventRepository.findByEventPartnerIdAndIdempotencyKey(eventPartnerId, idempotencyKey);
        });
    }

    /** 삭제했으면 본문을 비교하지 않고 404, 본문이 다르면 422, 같으면 지금의 행사다. */
    private Event replay(Event event, String hash) {
        if (event.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        if (!hash.equals(event.getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return event;
    }

    /** 키를 INSERT로 선점한 뒤 같은 트랜잭션에서 시작 시각을 검사한다. 지났으면 롤백한다. */
    private Event create(UUID eventPartnerId, UUID idempotencyKey, EventContent content, String hash) {
        applyTimeLimit();
        Event event = Event.register(
            idGenerator.generate(), eventPartnerId, content, clock, idempotencyKey, hash);
        entityManager.persist(event);
        entityManager.flush();
        if (!content.start().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.EVENT_START_IN_PAST);
        }
        return event;
    }

    /**
     * 이 트랜잭션의 SQL 문장마다 잠금 대기와 실행 시간의 상한을 둔다(트랜잭션이 끝나면 풀린다).
     * 키 조회도 events의 DDL 잠금 뒤에 줄을 서면 연결을 붙잡으므로 같은 상한을 건다.
     * SET LOCAL은 바인드 변수를 받지 못해 set_config(..., is_local = true)를 쓴다.
     */
    private void applyTimeLimit() {
        String millis = properties.dbTimeout().toMillis() + "ms";
        entityManager.createNativeQuery(
                "select set_config('lock_timeout', ?1, true), set_config('statement_timeout', ?1, true)")
            .setParameter(1, millis)
            .getSingleResult();
    }

    private void requireKnownRegion(EventContent content) {
        boolean known = readTransaction.execute(status -> {
            applyTimeLimit();
            return regionRepository.existsById(content.regionCode());
        });
        if (!known) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static boolean isKeyConflict(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState())
                && sql.getMessage() != null
                && sql.getMessage().contains(KEY_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSqlState(Throwable throwable, Set<String> states) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && states.contains(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
