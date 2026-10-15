package com.nsu.capstone.trust.reliability.domain;

import java.time.Duration;

/**
 * Reliability 정책 버전과 파라미터. 정책값은 이 record에만 둔다.
 *
 * @param gamma 관측 1건이 쌓일 때마다 기존 누적값에 곱하는 감쇠 계수(09 ADR)
 * @param priorAlpha 이행 누적값의 사전분포 α₀
 * @param priorBeta 실패 누적값의 사전분포 β₀
 * @param maxLowConfidenceObservations 관측 수가 이 값 이하면 확신 수준 낮음
 * @param minHighConfidenceObservations 관측 수가 이 값 이상이면 확신 수준 높음
 * @param riskSignalWindowSize Risk Signal을 판단하는 최근 관측 창의 크기
 * @param lateNoticeThreshold 아티스트 귀책 취소의 통보가 이 기간보다 짧으면 "24시간 미만"
 * @param earlyNoticeThreshold 아티스트 귀책 취소의 통보가 이 기간 이상이면 "7일 이상"
 * @param repeatedCancellationThreshold 창 안의 아티스트 귀책 취소가 이 건수 이상이면 반복 취소
 */
public record ReliabilityPolicy(
    String version,
    double gamma,
    double priorAlpha,
    double priorBeta,
    int maxLowConfidenceObservations,
    int minHighConfidenceObservations,
    int riskSignalWindowSize,
    Duration lateNoticeThreshold,
    Duration earlyNoticeThreshold,
    int repeatedCancellationThreshold
) {

    public static final ReliabilityPolicy V1 = new ReliabilityPolicy(
        "reliability-v1",
        0.95,
        1,
        1,
        3,
        10,
        14,
        Duration.ofHours(24),
        Duration.ofDays(7),
        2
    );
}
