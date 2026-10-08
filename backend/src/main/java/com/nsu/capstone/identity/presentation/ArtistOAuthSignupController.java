package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.ArtistOAuthSignupService;
import com.nsu.capstone.identity.presentation.dto.ArtistOAuthSignupRequest;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.CreateArtistOAuthSignupSessionRequest;
import com.nsu.capstone.identity.presentation.dto.CreateArtistOAuthSignupSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/signup/artist/oauth")
public class ArtistOAuthSignupController {
    private final ArtistOAuthSignupService service;

    public ArtistOAuthSignupController(ArtistOAuthSignupService service) {
        this.service = service;
    }

    @PostMapping("/session")
    public ResponseEntity<CreateArtistOAuthSignupSessionResponse> prepare(
        @Valid @RequestBody CreateArtistOAuthSignupSessionRequest request) {
        var prepared = service.prepare(request.oauthSignupSessionId(), request.phone(), request.email());
        return ResponseEntity.status(prepared.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(new CreateArtistOAuthSignupSessionResponse(prepared.signupSessionId(),
                prepared.email(), prepared.emailVerified()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArtistSignupResponse signup(@Valid @RequestBody ArtistOAuthSignupRequest request) {
        return service.signup(request.oauthSignupSessionId());
    }
}
