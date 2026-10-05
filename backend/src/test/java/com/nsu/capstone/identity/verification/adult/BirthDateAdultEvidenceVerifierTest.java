package com.nsu.capstone.identity.verification.adult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BirthDateAdultEvidenceVerifierTest {

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-10-05T00:00:00Z"),
        ZoneOffset.UTC
    );
    private final BirthDateAdultEvidenceVerifier verifier =
        new BirthDateAdultEvidenceVerifier(clock);

    @Test
    void acceptsExactlyOnEighteenthBirthday() {
        assertTrue(verifier.isAdult(LocalDate.of(2008, 10, 5)));
    }

    @Test
    void rejectsOneDayBeforeEighteenthBirthday() {
        assertFalse(verifier.isAdult(LocalDate.of(2008, 10, 6)));
    }
}
