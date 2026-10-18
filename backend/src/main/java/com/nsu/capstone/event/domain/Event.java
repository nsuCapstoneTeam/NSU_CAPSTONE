package com.nsu.capstone.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "events")
public class Event {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Id
    private UUID id;

    @Column(name = "event_partner_id", nullable = false, updatable = false)
    private UUID eventPartnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EventStatus status;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "performance_minutes", nullable = false)
    private int performanceMinutes;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "region_code", nullable = false, length = 5)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "artist_type", length = 32)
    private ArtistType artistType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private List<Genre> genres;

    @Column(name = "budget_min", nullable = false)
    private long budgetMin;

    @Column(name = "budget_max", nullable = false)
    private long budgetMax;

    @Column(name = "expected_audience", nullable = false)
    private int expectedAudience;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Visibility visibility;

    @Embedded
    private EventDetails details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Event() {
    }

    public static Event register(UUID id, UUID eventPartnerId, EventContent content, Clock clock) {
        Event event = new Event();
        event.id = id;
        event.eventPartnerId = eventPartnerId;
        event.status = EventStatus.OPEN;
        event.apply(content);
        event.createdAt = clock.instant();
        event.updatedAt = event.createdAt;
        return event;
    }

    /** 전체 교체다(03 §5.4). 본문에 없는 선택정보는 지운다. */
    public void replace(EventContent content, Clock clock) {
        apply(content);
        this.updatedAt = clock.instant();
    }

    /** 이미 취소한 행사에는 아무것도 바꾸지 않는다(멱등, 03 §5.5). */
    public void cancel(Clock clock) {
        if (status == EventStatus.CANCELLED) {
            return;
        }
        this.status = EventStatus.CANCELLED;
        this.updatedAt = clock.instant();
    }

    /** 소프트 삭제(ADR event_management/02). */
    public void delete(Clock clock) {
        this.deletedAt = clock.instant();
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    /** 시작 ≤ 지금이면 시작한 행사다(03 §5.2). */
    public boolean hasStarted(Clock clock) {
        return !startAt.isAfter(clock.instant());
    }

    private void apply(EventContent content) {
        this.name = content.name();
        this.eventType = content.eventType();
        this.startAt = content.start().atZone(ZONE).toInstant();
        this.endAt = content.end().atZone(ZONE).toInstant();
        this.performanceMinutes = content.performanceMinutes();
        this.venue = content.venue();
        this.regionCode = content.regionCode();
        this.artistType = content.artistType();
        this.genres = List.copyOf(content.genres());
        this.budgetMin = content.budgetMin();
        this.budgetMax = content.budgetMax();
        this.expectedAudience = content.expectedAudience();
        this.description = content.description();
        this.visibility = content.visibility();
        this.details = content.details();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventPartnerId() {
        return eventPartnerId;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public int getPerformanceMinutes() {
        return performanceMinutes;
    }

    public String getVenue() {
        return venue;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public ArtistType getArtistType() {
        return artistType;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public long getBudgetMin() {
        return budgetMin;
    }

    public long getBudgetMax() {
        return budgetMax;
    }

    public int getExpectedAudience() {
        return expectedAudience;
    }

    public String getDescription() {
        return description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    /** 선택정보가 모두 비면 JPA가 null로 읽으므로, 항상 객체로 돌려준다(03 §3.6 불변식 5). */
    public EventDetails getDetails() {
        return details == null ? EventDetails.empty() : details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
