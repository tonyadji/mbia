package com.lehnade.mbia.shared.api.security;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code mbia.security.*}: the identity provider trusted by the API (ADR-005) and the browser
 * origins allowed to call it.
 *
 * @param issuerUri the Keycloak realm, e.g. {@code http://localhost:8081/realms/mbia}
 * @param audience the audience every access token must carry
 */
@Validated
@ConfigurationProperties("mbia.security")
public record SecurityProperties(@NotBlank String issuerUri, @NotBlank String audience, @DefaultValue Cors cors) {

    public record Cors(@DefaultValue List<String> allowedOrigins) {}
}
