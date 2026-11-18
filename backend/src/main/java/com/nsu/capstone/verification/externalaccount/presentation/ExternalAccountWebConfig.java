package com.nsu.capstone.verification.externalaccount.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@code /api/v1/me/external-accounts/**}에만 역할·요청 제한 인터셉터를 건다. 조회 API와 콜백은 건너뛴다(06 §1). */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ExternalAccountWebConfig implements WebMvcConfigurer {

    private final MyExternalAccountsInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/me/external-accounts/**");
    }
}
