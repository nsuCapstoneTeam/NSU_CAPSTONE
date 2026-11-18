package com.nsu.capstone.verification.identity;

/** PortOne 본인인증 단건 조회 200 응답에서 읽는 값. status와 channel 네 값 밖의 필드는 읽지 않는다. */
record PortOneResponse(String status, PortOneChannel channel) {}
