package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class PortOneResponseTest {

    private static final String BODY_WITH_PERSONAL_DATA = """
            {
              "status": "VERIFIED",
              "id": "ignored-id",
              "verifiedCustomer": {"name": "홍길동", "birthDate": "1990-01-01", "ci": "ignored-ci"},
              "requestedCustomer": {"name": "홍길동"},
              "pgRawResponse": "ignored",
              "channel": {"type": "TEST", "pgProvider": "kcp", "pgMerchantId": "AO6R1", "id": "ch-1", "name": "ignored", "key": "k"}
            }
            """;

    @Test
    void 알_수_없는_필드가_있어도_매퍼_설정과_상관없이_status와_channel만_읽는다() {
        JsonMapper strictMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        PortOneResponse response = strictMapper.readValue(BODY_WITH_PERSONAL_DATA, PortOneResponse.class);

        assertThat(response)
                .isEqualTo(new PortOneResponse("VERIFIED", new PortOneChannel("TEST", "kcp", "AO6R1", "k")));
    }
}
