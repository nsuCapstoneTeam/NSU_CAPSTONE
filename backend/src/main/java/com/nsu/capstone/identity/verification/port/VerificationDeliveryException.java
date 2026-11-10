package com.nsu.capstone.identity.verification.port;

public class VerificationDeliveryException extends RuntimeException {

    public enum Outcome {
        REJECTED,
        NOT_SENT,
        UNKNOWN
    }

    private final Outcome outcome;

    public VerificationDeliveryException(Outcome outcome) {
        super("인증 코드 발송 결과: " + outcome);
        this.outcome = outcome;
    }

    public Outcome outcome() {
        return outcome;
    }
}
