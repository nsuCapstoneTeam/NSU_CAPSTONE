package com.nsu.capstone.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher LOGIN_ENDPOINT =
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login");

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
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter,
        JsonAuthenticationEntryPoint authenticationEntryPoint,
        JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PRE_LOGIN_SIGNUP_ENDPOINTS, LOGIN_ENDPOINT).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                PRE_LOGIN_SIGNUP_ENDPOINTS,
                LOGIN_ENDPOINT
            ))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .oauth2ResourceServer(resourceServer -> resourceServer
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );

        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }
}
