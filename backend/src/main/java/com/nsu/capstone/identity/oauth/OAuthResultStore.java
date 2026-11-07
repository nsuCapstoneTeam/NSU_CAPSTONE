package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;

/**
 * 결과 코드는 결합이 확인된 흐름에만 만든다. 모든 save 메서드는 그 흐름의 결합 digest를
 * 결과와 함께 저장하고, 교환하는 쪽이 {@link OAuthResult#bindingDigest()}를 쿠키와 비교한다.
 */
public interface OAuthResultStore {

    String saveLogin(UUID userId, String bindingDigest);

    String saveSignupRequired(String oauthSignupSessionId, String bindingDigest);

    String saveError(ErrorCode errorCode, String bindingDigest);

    Optional<OAuthResult> consume(String code);
}
