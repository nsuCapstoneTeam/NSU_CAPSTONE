package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.EventPartnerSignupService;
import com.nsu.capstone.identity.presentation.dto.CreateEventPartnerSignupSessionRequest;
import com.nsu.capstone.identity.presentation.dto.CreateEventPartnerSignupSessionResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupRequest;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/signup/event-partner")
public class EventPartnerSignupController {

    private final EventPartnerSignupService eventPartnerSignupService;

    public EventPartnerSignupController(EventPartnerSignupService eventPartnerSignupService) {
        this.eventPartnerSignupService = eventPartnerSignupService;
    }

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventPartnerSignupSessionResponse createSession(
        @Valid @RequestBody CreateEventPartnerSignupSessionRequest request
    ) {
        return eventPartnerSignupService.createSession(request.email(), request.phone());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventPartnerSignupResponse signup(
        @Valid @RequestBody EventPartnerSignupRequest request
    ) {
        return eventPartnerSignupService.signup(request.signupSessionId(), request.password());
    }
}
