package com.nsu.capstone.identity.verification.port;

public class VerificationDeliveryException extends RuntimeException {

    public enum Outcome {
        REJECTED,
        NOT_SENT,
        UNKNOWN,
        PREPARATION_FAILED
    }

    private final Outcome outcome;
    private final String failureType;
    private final StackTraceElement failureLocation;

    public VerificationDeliveryException(Outcome outcome) {
        this(outcome, null);
    }

    /** Retains diagnostic metadata only, never the provider exception or its message. */
    public VerificationDeliveryException(Outcome outcome, RuntimeException failure) {
        super("인증 코드 발송 결과: " + outcome);
        this.outcome = outcome;
        this.failureType = failure == null ? null : failure.getClass().getName();
        this.failureLocation =
                failure == null || failure.getStackTrace().length == 0 ? null : failure.getStackTrace()[0];
    }

    public String failureType() {
        return failureType;
    }

    public StackTraceElement failureLocation() {
        return failureLocation;
    }

    public Outcome outcome() {
        return outcome;
    }
}
