package com.nsu.capstone.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 선택정보 10개(03 §3.4). 화면에 보여 주는 정보이며 모두 null일 수 있다. */
@Embeddable
public record EventDetails(
    @Column(name = "rehearsal", length = 100) String rehearsal,
    @Column(name = "stage_size", length = 100) String stageSize,
    @Column(name = "pa_provided") Boolean paProvided,
    @Column(name = "microphones", length = 100) String microphones,
    @Column(name = "instruments", length = 100) String instruments,
    @Column(name = "waiting_room_provided") Boolean waitingRoomProvided,
    @Column(name = "meal_provided") Boolean mealProvided,
    @Column(name = "transportation_fee_provided") Boolean transportationFeeProvided,
    @Column(name = "accommodation_provided") Boolean accommodationProvided,
    @Column(name = "other_conditions", length = 500) String otherConditions
) {

    public static EventDetails empty() {
        return new EventDetails(null, null, null, null, null, null, null, null, null, null);
    }
}
