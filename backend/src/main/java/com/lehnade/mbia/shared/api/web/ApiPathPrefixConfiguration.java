package com.lehnade.mbia.shared.api.web;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the HTTP contract under {@code /api/v1} ({@code technical-specification.md} §11).
 *
 * <p>Only controllers implementing an interface generated from {@code openapi.yaml} get the prefix,
 * so the paths of the contract stay those of the document and Actuator stays at {@code /actuator}.
 */
@Configuration(proxyBeanMethods = false)
public class ApiPathPrefixConfiguration implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    static final String GENERATED_API_PACKAGE = "com.lehnade.mbia.api.generated";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, ApiPathPrefixConfiguration::implementsGeneratedApi);
    }

    static boolean implementsGeneratedApi(Class<?> handlerType) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(handlerType))
                .anyMatch(type -> type.getPackageName().equals(GENERATED_API_PACKAGE));
    }
}
