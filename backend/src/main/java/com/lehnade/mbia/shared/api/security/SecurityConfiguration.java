package com.lehnade.mbia.shared.api.security;

import static com.lehnade.mbia.shared.api.web.ApiPathPrefixConfiguration.API_PREFIX;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The API is an OAuth2 resource server trusting the Keycloak realm (ADR-005).
 *
 * <ul>
 *   <li>{@code /api/v1/**} needs a bearer token signed by the issuer, from that issuer, for the
 *       {@code mbia-api} audience; {@code /actuator/health} is public. No other path is served.
 *   <li>Stateless, bearer tokens only: no session, no CSRF, no form or basic login.
 *   <li>A missing or rejected token is a 401 {@code AUTHENTICATION_REQUIRED} problem, rendered by
 *       the global exception handler like every other error.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            exceptionResolver.resolveException(request, response, null,
                    new DomainException(ErrorCode.AUTHENTICATION_REQUIRED, "A valid access token is required."));
        };
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(API_PREFIX + "/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Nothing else is served: unknown paths get a 404 problem.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties.cors().allowedOrigins())))
                .build();
    }

    /** Discovers the issuer's keys on first use, so the API starts even when Keycloak is not up yet. */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        OAuth2TokenValidator<Jwt> validator = jwtValidator(properties.issuerUri(), properties.audience());
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();
            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    /** Expiry, issuer and audience checks applied to every access token. */
    public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience) {
        return JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(issuer), new JwtAudienceValidator(audience));
    }

    private static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_MATCH, "X-Request-Id"));
        cors.setExposedHeaders(List.of(HttpHeaders.ETAG, "X-Request-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(API_PREFIX + "/**", cors);
        return source;
    }
}
