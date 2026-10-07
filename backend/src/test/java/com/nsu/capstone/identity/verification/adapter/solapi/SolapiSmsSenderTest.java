package com.nsu.capstone.identity.verification.adapter.solapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.exception.SolapiApiKeyException;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.FailedMessage;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolapiSmsSenderTest {

    private static final String FROM = "0212345678";
    private static final String TO = "01012345678";
    private static final String CODE = "123456";
    private static final String SECRET = "provider-secret";

    @Mock DefaultMessageService messageService;

    private SolapiSmsSender sender;

    @BeforeEach
    void setUp() {
        sender = new SolapiSmsSender(messageService, FROM);
    }

    @Test
    void sendsConfiguredMessageWithOtpAndValidity() throws Exception {
        when(messageService.send(any(Message.class))).thenReturn(successfulResponse());

        sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageService).send(captor.capture());
        Message message = captor.getValue();
        assertEquals(FROM, message.getFrom());
        assertEquals(TO, message.getTo());
        assertTrue(message.getText().contains(CODE));
        assertTrue(message.getText().contains("5분"));
    }

    @Test
    void convertsMessageNotReceivedExceptionWithoutExposingSensitiveValues() throws Exception {
        when(messageService.send(any(Message.class))).thenThrow(
            new SolapiMessageNotReceivedException(SECRET + " " + CODE)
        );

        assertSanitizedDeliveryFailure();
    }

    @Test
    void convertsApiKeyExceptionWithoutExposingSensitiveValues() throws Exception {
        when(messageService.send(any(Message.class))).thenAnswer(invocation -> {
            throw new SolapiApiKeyException(SECRET + " " + CODE);
        });

        assertSanitizedDeliveryFailure();
    }

    @Test
    void convertsUnknownProviderExceptionWithoutExposingSensitiveValues() throws Exception {
        when(messageService.send(any(Message.class))).thenThrow(
            new SolapiUnknownException("raw-provider-response " + SECRET + " " + CODE)
        );

        assertSanitizedDeliveryFailure();
    }

    @Test
    void convertsEmptyProviderResponseExceptionWithoutExposingSensitiveValues() throws Exception {
        when(messageService.send(any(Message.class))).thenThrow(
            new SolapiEmptyResponseException(SECRET + " " + CODE)
        );

        assertSanitizedDeliveryFailure();
    }

    @Test
    void doesNotHideUnexpectedProgrammingException() throws Exception {
        IllegalStateException unexpected = new IllegalStateException("unexpected");
        when(messageService.send(any(Message.class))).thenThrow(unexpected);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5))
        );

        assertEquals(unexpected, thrown);
    }

    @Test
    void acceptsSuccessfulResponseWithoutMessageList() throws Exception {
        when(messageService.send(any(Message.class)))
            .thenReturn(new MultipleDetailMessageSentResponse());

        sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5));
    }

    @Test
    void convertsFailedProviderResult() throws Exception {
        MultipleDetailMessageSentResponse response = new MultipleDetailMessageSentResponse();
        response.setFailedMessageList(List.of(new FailedMessage()));
        when(messageService.send(any(Message.class))).thenReturn(response);

        assertSanitizedDeliveryFailure();
    }

    private void assertSanitizedDeliveryFailure() {
        VerificationDeliveryException exception = assertThrows(
            VerificationDeliveryException.class,
            () -> sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5))
        );

        assertFalse(exception.getMessage().contains(SECRET));
        assertFalse(exception.getMessage().contains(CODE));
        assertFalse(exception.getMessage().contains(TO));
    }

    private MultipleDetailMessageSentResponse successfulResponse() {
        MultipleDetailMessageSentResponse response = new MultipleDetailMessageSentResponse();
        response.setFailedMessageList(List.of());
        return response;
    }
}
