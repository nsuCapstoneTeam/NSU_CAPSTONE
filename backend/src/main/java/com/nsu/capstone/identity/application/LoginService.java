package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.security.AuthTokenIssuer;
import com.nsu.capstone.identity.security.IssuedTokens;
import com.nsu.capstone.identity.security.LoginPrincipal;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final RedisPublicRequestLimitStore limits;
    private final AuthenticationManager authenticationManager;
    private final AuthTokenIssuer tokenIssuer;

    public LoginService(
            AuthenticationManager authenticationManager,
            AuthTokenIssuer tokenIssuer,
            RedisPublicRequestLimitStore limits) {
        this.limits = limits;
        this.authenticationManager = authenticationManager;
        this.tokenIssuer = tokenIssuer;
    }

    public LoginResponse login(String email, String password, String remoteAddress) {
        email = SignupIdentityNormalizer.email(email);
        String reservation = limits.login(email, remoteAddress);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (org.springframework.security.authentication.InternalAuthenticationServiceException exception) {
            limits.loginSucceeded(reservation);
            throw exception;
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
        }

        limits.loginSucceeded(reservation);
        LoginPrincipal principal = (LoginPrincipal) authentication.getPrincipal();
        return LoginResponse.of(
                issueTokens(principal, reservation != null), principal.userId(), principal.role(), principal.status());
    }

    /**
     * Redis를 쓸 수 없어도 로그인은 열어 둔다(signup-terms-and-send-limits §1.6 fail-open).
     * 그때는 Refresh Token 없이 Access Token만 준다. 한도 검사에서 이미 장애를 봤으면 다시 기다리지 않는다.
     */
    private IssuedTokens issueTokens(LoginPrincipal principal, boolean redisAvailable) {
        if (redisAvailable) {
            try {
                return tokenIssuer.issue(principal.userId(), principal.role());
            } catch (DataAccessException exception) {
                log.warn(
                        "Redis에 Refresh Token을 저장하지 못해 Access Token만 발급합니다. 원인 타입: {}",
                        exception.getClass().getName());
            }
        }
        return tokenIssuer.issueAccessOnly(principal.userId(), principal.role());
    }
}
