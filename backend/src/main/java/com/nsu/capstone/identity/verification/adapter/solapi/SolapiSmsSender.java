package com.nsu.capstone.identity.verification.adapter.solapi;

import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.exception.SolapiApiKeyException;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import java.time.Duration;

public class SolapiSmsSender implements SmsSender {

    private final DefaultMessageService messageService;
    private final String fromNumber;

    public SolapiSmsSender(DefaultMessageService messageService, String fromNumber) {
        this.messageService = messageService;
        this.fromNumber = fromNumber;
    }

    @Override
    public void sendVerificationCode(String phone, String code, Duration validity) {
        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(phone);
        message.setText(messageText(code, validity));

        try {
            MultipleDetailMessageSentResponse response = send(message);
            if (deliveryFailed(response)) {
                throw deliveryException();
            }
        } catch (VerificationDeliveryException exception) {
            throw exception;
        } catch (
            SolapiMessageNotReceivedException
                | SolapiEmptyResponseException
                | SolapiUnknownException
                | SolapiApiKeyException exception
        ) {
            throw deliveryException();
        }
    }

    private MultipleDetailMessageSentResponse send(Message message)
        throws SolapiMessageNotReceivedException,
        SolapiEmptyResponseException,
        SolapiUnknownException,
        SolapiApiKeyException {
        return messageService.send(message);
    }

    private boolean deliveryFailed(MultipleDetailMessageSentResponse response) {
        return response == null
            || response.getFailedMessageList() == null
            || !response.getFailedMessageList().isEmpty();
    }

    private String messageText(String code, Duration validity) {
        return "[NSU-EventMatch] 인증번호 " + code + " (유효시간 " + validity.toMinutes() + "분)";
    }

    private VerificationDeliveryException deliveryException() {
        return new VerificationDeliveryException("SMS delivery failed");
    }
}
