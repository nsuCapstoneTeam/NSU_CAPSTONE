package com.nsu.capstone;

import java.util.UUID;

/**
 * API 01 §6의 응답 예시.
 */
final class TrustProfileExamples {

    private TrustProfileExamples() {
    }

    /**
     * §6.1 MVP 응답: 공연 결과가 없고 Verification도 없는 아티스트.
     */
    static String insufficientEvidenceProfile(UUID artistId) {
        return """
            {
              "artistId": "%s",
              "policyVersion": "reliability-v1",
              "verifications": [],
              "reliability": {
                "confidenceLevel": "LOW",
                "observationCount": 0,
                "ratePercent": null
              },
              "evidence": {
                "observations": {
                  "completedCount": 0,
                  "artistCancellations": {
                    "noticeAtLeast7DaysCount": 0,
                    "notice24HoursTo7DaysCount": 0,
                    "noticeUnder24HoursCount": 0
                  },
                  "noShowCount": 0
                },
                "exclusions": []
              },
              "riskSignals": [],
              "activityFreshness": {
                "lastPerformedMonth": null,
                "completedCountLast12Months": 0
              }
            }
            """.formatted(artistId);
    }
}
