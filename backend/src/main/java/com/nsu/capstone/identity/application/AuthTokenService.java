package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.AuthTokenIssuer;
import com.nsu.capstone.identity.security.RefreshTokenStore;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final AuthTokenIssuer tokenIssuer;

    public AuthTokenService(
            RefreshTokenStore refreshTokenStore, UserRepository userRepository, AuthTokenIssuer tokenIssuer) {
        this.refreshTokenStore = refreshTokenStore;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Refresh Token은 일회용이다. 꺼내면서 지우고 새 토큰 쌍을 준다.
     * 사용자는 DB에서 다시 읽어 ACTIVE인지 확인하고, role도 토큰이 아니라 DB의 현재 값을 쓴다.
     */
    public LoginResponse refresh(String refreshToken) {
        User user = refreshTokenStore
                .consume(refreshToken)
                .flatMap(userRepository::findById)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        return LoginResponse.of(
                tokenIssuer.issue(user.getId(), user.getRole()), user.getId(), user.getRole(), user.getStatus());
    }

    /** 없거나 이미 지운 토큰도 같은 결과다. 이미 발급한 Access Token은 만료까지 유효하다. */
    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }
}
