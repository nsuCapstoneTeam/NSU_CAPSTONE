package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.ArtistSignupService;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupRequest;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.CreateArtistSignupSessionRequest;
import com.nsu.capstone.identity.presentation.dto.CreateArtistSignupSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/signup/artist")
public class ArtistSignupController {

    private final ArtistSignupService artistSignupService;

    public ArtistSignupController(ArtistSignupService artistSignupService) {
        this.artistSignupService = artistSignupService;
    }

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateArtistSignupSessionResponse createSession(
        @Valid @RequestBody CreateArtistSignupSessionRequest request
    ) {
        return artistSignupService.createSession(request.email(), request.phone());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArtistSignupResponse signup(@Valid @RequestBody ArtistSignupRequest request) {
        return artistSignupService.signup(request.signupSessionId(), request.password());
    }
}
