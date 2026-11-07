package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.identity.application.OAuthResultService;
import com.nsu.capstone.identity.oauth.OAuthBindingCookie;
import com.nsu.capstone.identity.presentation.dto.OAuthResultExchangeRequest;
import com.nsu.capstone.identity.presentation.dto.OAuthResultResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "OAuth")
@SecurityRequirements
@RequestMapping("/api/v1/auth/oauth/result")
public class OAuthResultController {

    private final OAuthResultService resultService;
    private final OAuthBindingCookie bindingCookie;

    public OAuthResultController(OAuthResultService resultService, OAuthBindingCookie bindingCookie) {
        this.resultService = resultService;
        this.bindingCookie = bindingCookie;
    }

    @PostMapping
    public OAuthResultResponse exchange(
            @Valid @RequestBody OAuthResultExchangeRequest request, HttpServletRequest httpRequest) {
        return resultService.exchange(
                request.code(), bindingCookie.nonce(httpRequest).orElse(null));
    }
}
