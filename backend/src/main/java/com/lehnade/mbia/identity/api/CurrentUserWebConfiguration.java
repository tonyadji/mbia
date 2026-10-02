package com.lehnade.mbia.identity.api;

import com.lehnade.mbia.identity.application.provisionuser.ProvisionCurrentUserUseCase;
import com.lehnade.mbia.shared.api.security.SecurityConfiguration;
import com.lehnade.mbia.shared.api.web.ApiPathPrefixConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Every API request is provisioned, whatever its endpoint (ADR-005), except the public preview of
 * an invitation, which needs no User.
 */
@Configuration(proxyBeanMethods = false)
public class CurrentUserWebConfiguration implements WebMvcConfigurer {

    private final CurrentUserInterceptor currentUserInterceptor;

    CurrentUserWebConfiguration(ProvisionCurrentUserUseCase provisionCurrentUser) {
        this.currentUserInterceptor = new CurrentUserInterceptor(provisionCurrentUser);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentUserInterceptor)
                .addPathPatterns(ApiPathPrefixConfiguration.API_PREFIX + "/**")
                .excludePathPatterns(SecurityConfiguration.INVITATION_PREVIEW);
    }
}
