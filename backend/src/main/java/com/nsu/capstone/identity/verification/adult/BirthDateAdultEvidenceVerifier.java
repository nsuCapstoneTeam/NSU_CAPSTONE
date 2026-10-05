package com.nsu.capstone.identity.verification.adult;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class BirthDateAdultEvidenceVerifier implements AdultEvidenceVerifier {

    private static final int ADULT_AGE = 18;

    private final Clock clock;

    public BirthDateAdultEvidenceVerifier(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isAdult(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        return !birthDate.isAfter(today) && !birthDate.plusYears(ADULT_AGE).isAfter(today);
    }
}
