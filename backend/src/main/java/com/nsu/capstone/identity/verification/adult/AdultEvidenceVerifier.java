package com.nsu.capstone.identity.verification.adult;

import java.time.LocalDate;

public interface AdultEvidenceVerifier {

    boolean isAdult(LocalDate birthDate);
}
