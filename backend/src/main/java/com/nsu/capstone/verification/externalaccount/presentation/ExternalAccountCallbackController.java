package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google이 브라우저를 돌려보내는 OAuth 콜백(06 §4). 받은 값을 프론트로 넘기기만 한다.
 * Redis·DB·외부 호출을 하지 않는다. 의존성에 그런 것이 없다.
 */
@Tag(name = "외부 계정")
@RestController
@RequestMapping("/api/v1/external-accounts/oauth/callback")
@RequiredArgsConstructor
public class ExternalAccountCallbackController {

    private static final String DENIED = "EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED";
    private static final String FAILED = "EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED";
    /** 연결 요청의 {@code oauth.state}·{@code oauth.code} 상한(06 §5.1). 넘는 값은 어차피 연결에 쓸 수 없다. */
    private static final int MAX_VALUE_LENGTH = 2048;

    private final ExternalAccountProperties properties;

    @GetMapping("/youtube")
    public ResponseEntity<Void> youtube(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .headers(headers(location(code, state, error)))
                .build();
    }

    private HttpHeaders headers(URI location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        headers.set("Referrer-Policy", "no-referrer");
        headers.setCacheControl(CacheControl.noStore());
        return headers;
    }

    /** 프론트 주소는 설정에서만 만든다. 요청에 든 주소는 읽지 않는다(열린 리다이렉트 방지). */
    private URI location(String code, String state, String error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(properties.frontendBaseUri())
                .replacePath("/mypage")
                .replaceQuery(null)
                .queryParam("tab", "works");
        if (error != null && !error.isBlank()) {
            // error 값은 화면에 쓰이지 않게 우리가 정한 두 값 중 하나로 바꾼다
            return "access_denied".equals(error)
                    ? build(target.queryParam("linkProvider", "YOUTUBE").queryParam("linkError", DENIED))
                    : build(target.queryParam("linkError", FAILED));
        }
        if (usable(code) && usable(state)) {
            // 값은 템플릿 변수로 넘겨 엄격하게 인코딩한다. +를 그대로 두면 프론트의 URLSearchParams가 공백으로 읽는다
            return target.queryParam("linkProvider", "YOUTUBE")
                    .queryParam("linkState", "{state}")
                    .queryParam("linkCode", "{code}")
                    .encode()
                    .buildAndExpand(Map.of("state", state, "code", code))
                    .toUri();
        }
        return build(target.queryParam("linkError", FAILED));
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_VALUE_LENGTH;
    }

    private static URI build(UriComponentsBuilder target) {
        return target.encode().build().toUri();
    }
}
