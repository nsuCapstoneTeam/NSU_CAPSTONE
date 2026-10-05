package com.nsu.capstone.identity.signup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SignupSessionProperties.class)
public class SignupSessionConfig {
}
