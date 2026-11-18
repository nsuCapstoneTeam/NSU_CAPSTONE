package com.nsu.capstone.verification.identity;

/** 제공자 결과를 알 수 없는 이유. 로그 등급을 가르는 데만 쓰고 제공자 응답 내용을 담지 않는다. */
enum UnavailableCause {
    /** 401·403. 서버의 PortOne 비밀 값이나 상점 설정이 틀린 것으로 보인다. */
    CREDENTIAL_REJECTED,
    /** 그 밖의 200·404 아닌 상태 코드(400, 429, 5xx 등). */
    UNEXPECTED_STATUS,
    /** 200이지만 status가 없거나 모르는 값이거나 본문을 읽을 수 없다. */
    UNREADABLE_RESPONSE,
    CONNECTION_FAILED,
    TIMEOUT
}
