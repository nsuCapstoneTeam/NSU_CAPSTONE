package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.LoginService;
import com.nsu.capstone.identity.presentation.dto.LoginRequest;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/login")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.email(), request.password());
    }
}
