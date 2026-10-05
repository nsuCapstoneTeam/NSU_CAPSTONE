package com.nsu.capstone.identity.verification.port;

import java.time.Duration;

public interface SmsSender {

    void sendVerificationCode(String phone, String code, Duration validity);
}
