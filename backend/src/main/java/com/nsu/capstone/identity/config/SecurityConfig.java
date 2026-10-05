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

    private static final RequestMatcher ARTIST_SIGNUP_ENDPOINTS = new OrRequestMatcher(
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/signup/artist"),
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/signup/artist/session")
    );

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(ARTIST_SIGNUP_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(ARTIST_SIGNUP_ENDPOINTS));

        return http.build();
    }
}
