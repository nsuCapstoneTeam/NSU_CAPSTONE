package com.nsu.capstone.identity.verification.adapter;

import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

public final class VerificationHttpResponse {

    private static final JsonMapper JSON = new JsonMapper();

    private VerificationHttpResponse() {}

    /** Serialize inside preparation so serialization failures are known to precede HTTP execution. */
    public static byte[] jsonBody(Object body) {
        return JSON.writeValueAsBytes(body);
    }

    public static <T> T read(Supplier<RestClient.RequestHeadersSpec<?>> preparation, Class<T> bodyType) {
        RestClient.RequestHeadersSpec<?> request;
        try {
            request = preparation.get();
        } catch (RuntimeException exception) {
            throw new VerificationDeliveryException(Outcome.PREPARATION_FAILED, exception);
        }
        try {
            return request.exchange((ignored, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.is4xxClientError()) {
                    throw new VerificationDeliveryException(Outcome.REJECTED);
                }
                if (!status.is2xxSuccessful()) {
                    throw new VerificationDeliveryException(Outcome.UNKNOWN);
                }
                try {
                    return response.bodyTo(bodyType);
                } catch (RuntimeException exception) {
                    throw new VerificationDeliveryException(Outcome.UNKNOWN, exception);
                }
            });
        } catch (VerificationDeliveryException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new VerificationDeliveryException(
                    exception.getStatusCode().is4xxClientError() ? Outcome.REJECTED : Outcome.UNKNOWN, exception);
        } catch (ResourceAccessException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException
                        || cause instanceof HttpConnectTimeoutException
                        || cause instanceof UnknownHostException
                        || cause instanceof UnresolvedAddressException) {
                    throw new VerificationDeliveryException(Outcome.NOT_SENT, exception);
                }
            }
            throw new VerificationDeliveryException(Outcome.UNKNOWN, exception);
        } catch (RuntimeException exception) {
            // Execution has begun; without evidence of non-delivery, keep the outcome unknown.
            throw new VerificationDeliveryException(Outcome.UNKNOWN, exception);
        }
    }
}
