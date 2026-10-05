package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.AdultConfirmationRequest;
import com.nsu.capstone.identity.presentation.dto.ConfirmSignupVerificationRequest;
import com.nsu.capstone.identity.presentation.dto.RequiredTermsAgreementRequest;
import com.nsu.capstone.identity.presentation.dto.SignupSessionVerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/signup/event-partner/session")
public class EventPartnerSignupVerificationController {

    private final SignupVerificationService verificationService;

    public EventPartnerSignupVerificationController(SignupVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/email-verification/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendEmailCode(@Valid @RequestBody SignupSessionVerificationRequest request) {
        verificationService.sendEmailCode(request.signupSessionId(), UserRole.EVENT_PARTNER);
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailCode(@Valid @RequestBody ConfirmSignupVerificationRequest request) {
        verificationService.confirmEmailCode(
            request.signupSessionId(),
            request.code(),
            UserRole.EVENT_PARTNER
        );
    }

    @PostMapping("/phone-verification/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendPhoneCode(@Valid @RequestBody SignupSessionVerificationRequest request) {
        verificationService.sendPhoneCode(request.signupSessionId(), UserRole.EVENT_PARTNER);
    }

    @PostMapping("/phone-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPhoneCode(@Valid @RequestBody ConfirmSignupVerificationRequest request) {
        verificationService.confirmPhoneCode(
            request.signupSessionId(),
            request.code(),
            UserRole.EVENT_PARTNER
        );
    }

    @PutMapping("/required-terms-agreement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agreeRequiredTerms(@Valid @RequestBody RequiredTermsAgreementRequest request) {
        verificationService.agreeRequiredTerms(
            request.signupSessionId(),
            request.agreements().stream().map(agreement -> agreement.toTermVersion()).toList(),
            UserRole.EVENT_PARTNER
        );
    }

    @PostMapping("/adult-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmAdult(@Valid @RequestBody AdultConfirmationRequest request) {
        verificationService.confirmAdult(
            request.signupSessionId(),
            request.birthDate(),
            UserRole.EVENT_PARTNER
        );
    }
}
