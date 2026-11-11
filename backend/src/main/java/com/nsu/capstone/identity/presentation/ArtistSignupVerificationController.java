package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.AdultConfirmationRequest;
import com.nsu.capstone.identity.presentation.dto.ConfirmSignupVerificationRequest;
import com.nsu.capstone.identity.presentation.dto.SignupSessionVerificationRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "아티스트 가입")
@SecurityRequirements
@RequestMapping("/api/v1/auth/signup/artist/session")
public class ArtistSignupVerificationController {

    private final SignupVerificationService verificationService;

    public ArtistSignupVerificationController(SignupVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/email-verification/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendEmailCode(
            @Valid @RequestBody SignupSessionVerificationRequest request, HttpServletRequest servletRequest) {
        verificationService.sendEmailCode(request.signupSessionId(), UserRole.ARTIST, servletRequest.getRemoteAddr());
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailCode(
            @Valid @RequestBody ConfirmSignupVerificationRequest request, HttpServletRequest servletRequest) {
        verificationService.confirmEmailCode(
                request.signupSessionId(), request.code(), UserRole.ARTIST, servletRequest.getRemoteAddr());
    }

    @PostMapping("/phone-verification/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendPhoneCode(
            @Valid @RequestBody SignupSessionVerificationRequest request, HttpServletRequest servletRequest) {
        verificationService.sendPhoneCode(request.signupSessionId(), UserRole.ARTIST, servletRequest.getRemoteAddr());
    }

    @PostMapping("/phone-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPhoneCode(
            @Valid @RequestBody ConfirmSignupVerificationRequest request, HttpServletRequest servletRequest) {
        verificationService.confirmPhoneCode(
                request.signupSessionId(), request.code(), UserRole.ARTIST, servletRequest.getRemoteAddr());
    }

    @PostMapping("/adult-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmAdult(@Valid @RequestBody AdultConfirmationRequest request) {
        verificationService.confirmAdult(request.signupSessionId(), request.birthDate(), UserRole.ARTIST);
    }
}
