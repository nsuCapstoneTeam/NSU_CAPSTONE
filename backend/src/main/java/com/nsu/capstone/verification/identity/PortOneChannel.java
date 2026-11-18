package com.nsu.capstone.verification.identity;

/** PortOne 재조회 응답의 channel에서 읽는 네 값. 이 밖의 필드는 Java 값이 되지 않는다. */
record PortOneChannel(String type, String pgProvider, String pgMerchantId, String key) {}
