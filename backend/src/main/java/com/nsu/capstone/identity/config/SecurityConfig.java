package com.nsu.capstone.identity.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher LOGIN_ENDPOINT =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login");

    // Access Token이 만료된 뒤에도 부르는 경로라 공개한다. 본문의 Refresh Token이 자격 증명이다.
    // 기본 LogoutFilter가 아니라 인증 필터 뒤의 컨트롤러로 만든다(ADR authentication/00 Decision 4).
    // Authorization 헤더는 읽지 않는다. 만료된 Bearer가 붙어 401이 나면 로그아웃이 토큰을 지우지 못한다(07 §4·§5)
    private static final RequestMatcher TOKEN_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"));

    private static final RequestMatcher OAUTH_AUTHORIZATION_ENDPOINT =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/oauth/authorization/{provider}");

    private static final RequestMatcher OAUTH_CALLBACK_ENDPOINT =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/oauth/callback/{registrationId}");

    private static final RequestMatcher OAUTH_RESULT_ENDPOINT =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/oauth/result");

    private static final RequestMatcher OAUTH_PUBLIC_ENDPOINTS =
            new OrRequestMatcher(OAUTH_AUTHORIZATION_ENDPOINT, OAUTH_CALLBACK_ENDPOINT, OAUTH_RESULT_ENDPOINT);

    // 외부 계정 연결의 Google OAuth 콜백(API 06 §4). Google이 로그인 없이 브라우저를 돌려보내는 공개 경로이고,
    // 컨트롤러는 값을 프론트 주소로 넘기기만 한다. 로그인용 콜백과 달리 이 경로는 저장·외부 호출을 하지 않는다
    private static final RequestMatcher EXTERNAL_ACCOUNT_CALLBACK_ENDPOINT =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/external-accounts/oauth/callback/youtube");

    // 역할 검사를 본문 파싱보다 먼저 한다. @PreAuthorize만 두면 깨진 JSON을 보낸 ARTIST가 403이 아니라 400을 받는다(05 §4 검사 순서)
    private static final RequestMatcher EVENT_PARTNER_PROFILE_ENDPOINT =
            PathPatternRequestMatcher.pathPattern("/api/v1/me/event-partner-profile");

    // 실명 본인확인 세 경로는 ARTIST 전용이다. 역할 검사를 경로 값 검사와 요청 제한보다 먼저 한다(08 §3 2번)
    private static final RequestMatcher IDENTITY_VERIFICATION_ENDPOINTS =
            PathPatternRequestMatcher.pathPattern("/api/v1/me/identity-verification/**");

    // shortcut: API 문서 경로를 로그인 없이 연다(배포 서버 없음), 배포 설계(ADR-006) 때 막거나 끈다
    private static final RequestMatcher API_DOCS_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/v3/api-docs/**"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/scalar/**"));

    private static final RequestMatcher PRE_LOGIN_SIGNUP_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/signup/artist/oauth/session"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/signup/artist/oauth"),
            signupEndpoints("artist"),
            signupEndpoints("event-partner"));

    private static RequestMatcher signupEndpoints(String rolePath) {
        String basePath = "/api/v1/auth/signup/" + rolePath;
        return new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/session"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/session/email-verification/send"),
                PathPatternRequestMatcher.pathPattern(
                        HttpMethod.POST, basePath + "/session/email-verification/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/session/phone-verification/send"),
                PathPatternRequestMatcher.pathPattern(
                        HttpMethod.POST, basePath + "/session/phone-verification/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/session/adult-confirmation"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<OAuthSecurityConfigurer> oauthSecurityConfigurerProvider)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                PRE_LOGIN_SIGNUP_ENDPOINTS,
                                LOGIN_ENDPOINT,
                                TOKEN_ENDPOINTS,
                                OAUTH_PUBLIC_ENDPOINTS,
                                EXTERNAL_ACCOUNT_CALLBACK_ENDPOINT,
                                API_DOCS_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(EVENT_PARTNER_PROFILE_ENDPOINT)
                        .hasRole("EVENT_PARTNER")
                        .requestMatchers(IDENTITY_VERIFICATION_ENDPOINTS)
                        .hasRole("ARTIST")
                        .anyRequest()
                        .authenticated())
                // CSRF는 브라우저가 쿠키·세션 같은 자격 증명을 자동으로 붙일 때만 의미가 있다.
                // 이 서버는 Authorization 헤더의 Bearer 토큰으로만 인증한다.
                // 쿠키·세션·폼 로그인·HTTP Basic을 도입하면 다시 판단한다(ADR authentication/00).
                // 인가 검사보다 먼저 쓰기 요청에 응답하는 필터(요청 제한 등)를 추가할 때도 다시 판단한다.
                .csrf(AbstractHttpConfigurer::disable)
                // 세션이 없어 지울 것이 없다. CSRF를 끄면 기본 logout이 모든 메서드의 /logout을 302로 받는다(ADR Decision 4)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(tokenEndpointIgnoringResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        OAuthSecurityConfigurer oauthSecurityConfigurer = oauthSecurityConfigurerProvider.getIfAvailable();
        if (oauthSecurityConfigurer != null) {
            oauthSecurityConfigurer.configure(http);
        }

        return http.build();
    }

    private static BearerTokenResolver tokenEndpointIgnoringResolver() {
        BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> TOKEN_ENDPOINTS.matches(request) ? null : defaultResolver.resolve(request);
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }
}
