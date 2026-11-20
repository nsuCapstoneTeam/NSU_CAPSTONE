package com.nsu.capstone.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class AppEnvironmentConfig {

    @Bean
    AppEnvironment appEnvironment(AppProperties properties) {
        return AppEnvironment.DEMO;
    }
}
