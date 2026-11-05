package com.nsu.capstone.recommendation.eligibility.domain;

import java.util.List;
import java.util.UUID;

public record EligibleMusic(
    UUID musicId,
    long audioRevision,
    UUID artistId,
    List<EligibilityCondition> passedConditions
) {
}
