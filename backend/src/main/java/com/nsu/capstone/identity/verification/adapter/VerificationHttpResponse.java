package com.nsu.capstone.identity.verification.adapter;

import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

public final class VerificationHttpResponse {

    private VerificationHttpResponse() {}

    public static <T> T read(RestClient.RequestHeadersSpec<?> request, Class<T> bodyType) {
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
                    throw new VerificationDeliveryException(Outcome.UNKNOWN);
                }
            });
        } catch (ResourceAccessException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException
                        || cause instanceof HttpConnectTimeoutException
                        || cause instanceof UnknownHostException
                        || cause instanceof UnresolvedAddressException) {
                    throw new VerificationDeliveryException(Outcome.NOT_SENT);
                }
            }
            throw new VerificationDeliveryException(Outcome.UNKNOWN);
        }
    }
}
