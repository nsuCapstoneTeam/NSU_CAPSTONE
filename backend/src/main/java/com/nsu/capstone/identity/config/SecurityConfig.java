package com.nsu.capstone.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private static final RequestMatcher PRE_LOGIN_SIGNUP_ENDPOINTS = new OrRequestMatcher(
        signupEndpoints("artist"),
        signupEndpoints("event-partner")
    );

    private static RequestMatcher signupEndpoints(String rolePath) {
        String basePath = "/api/v1/auth/signup/" + rolePath;
        return new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/session"),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                basePath + "/session/email-verification/send"
            ),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                basePath + "/session/email-verification/confirm"
            ),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                basePath + "/session/phone-verification/send"
            ),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                basePath + "/session/phone-verification/confirm"
            ),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.PUT,
                basePath + "/session/required-terms-agreement"
            ),
            PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                basePath + "/session/adult-confirmation"
            )
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PRE_LOGIN_SIGNUP_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(PRE_LOGIN_SIGNUP_ENDPOINTS));

        return http.build();
    }
}
