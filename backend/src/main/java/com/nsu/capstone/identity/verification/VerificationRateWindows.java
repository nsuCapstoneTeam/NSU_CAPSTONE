package com.nsu.capstone.identity.verification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

record VerificationRateWindows(Instant dayEnd, Instant hourEnd, Instant channelDayEnd) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    Instant dayStart() {
        return dayEnd.minusSeconds(86400);
    }

    Instant hourStart() {
        return hourEnd.minusSeconds(3600);
    }

    Instant channelDayStart() {
        return channelDayEnd.minusSeconds(86400);
    }

    static VerificationRateWindows at(Instant now, VerificationChannel channel) {
        var seoul = now.atZone(SEOUL);
        Instant dayEnd = seoul.toLocalDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
        Instant hourEnd = seoul.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
        Instant channelDayEnd = channel == VerificationChannel.EMAIL
                ? now.atOffset(ZoneOffset.UTC)
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                : dayEnd;
        return new VerificationRateWindows(dayEnd, hourEnd, channelDayEnd);
    }
}
