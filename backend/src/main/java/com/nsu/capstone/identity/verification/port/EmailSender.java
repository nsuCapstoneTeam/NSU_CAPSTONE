package com.nsu.capstone.identity.verification.port;

import java.time.Duration;

public interface EmailSender {

    void sendVerificationCode(String email, String code, Duration validity);
}
