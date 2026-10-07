package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.security.LoginPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtAccessTokenService accessTokenService;

    public LoginService(
        AuthenticationManager authenticationManager,
        JwtAccessTokenService accessTokenService
    ) {
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
    }

    public LoginResponse login(String email, String password) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password)
            );
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
        }

        LoginPrincipal principal = (LoginPrincipal) authentication.getPrincipal();
        IssuedAccessToken accessToken = accessTokenService.issue(principal);
        return new LoginResponse(
            accessToken.value(),
            "Bearer",
            accessToken.expiresInSeconds(),
            principal.userId(),
            principal.role(),
            principal.status()
        );
    }
}
