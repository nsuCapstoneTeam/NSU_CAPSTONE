package com.nsu.capstone.identity.verification.port;

public class VerificationDeliveryException extends RuntimeException {

    public VerificationDeliveryException(String message) {
        super(message);
    }

    public VerificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
