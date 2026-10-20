package com.nsu.capstone.event.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventRegistrationProperties.class)
public class EventConfig {
}
