package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.OAuthResultService;
import com.nsu.capstone.identity.presentation.dto.OAuthResultExchangeRequest;
import com.nsu.capstone.identity.presentation.dto.OAuthResultResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth/result")
public class OAuthResultController {

    private final OAuthResultService resultService;

    public OAuthResultController(OAuthResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping
    public OAuthResultResponse exchange(@Valid @RequestBody OAuthResultExchangeRequest request) {
        return resultService.exchange(request.code());
    }
}
