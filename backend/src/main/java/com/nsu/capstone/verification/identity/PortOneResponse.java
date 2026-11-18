package com.nsu.capstone.verification.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PortOne 본인인증 단건 조회 200 응답에서 읽는 값. status와 channel 네 값 밖의 필드는 읽지 않는다(파서가 건너뛰어 Java 값이 되지 않는다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PortOneResponse(String status, PortOneChannel channel) {}
