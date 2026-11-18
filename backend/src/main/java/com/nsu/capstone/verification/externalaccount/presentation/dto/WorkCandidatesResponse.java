package com.nsu.capstone.verification.externalaccount.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nsu.capstone.verification.externalaccount.application.CandidateView;
import java.util.List;

/** 작업물 후보 응답(06 §7). */
public record WorkCandidatesResponse(List<CandidateResponse> items) {

    public record CandidateResponse(
            String workId,
            String url,
            String title,
            String thumbnailUrl,
            String publishedAt,
            @JsonProperty("isSelected") boolean isSelected) {

        static CandidateResponse from(CandidateView candidate) {
            return new CandidateResponse(
                    candidate.workId(),
                    candidate.url(),
                    candidate.title(),
                    candidate.thumbnailUrl(),
                    ExternalAccountResponse.time(candidate.publishedAt()),
                    candidate.isSelected());
        }
    }

    public static WorkCandidatesResponse from(List<CandidateView> candidates) {
        return new WorkCandidatesResponse(
                candidates.stream().map(CandidateResponse::from).toList());
    }
}
