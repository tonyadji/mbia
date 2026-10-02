package com.lehnade.mbia.identity.api;

import com.lehnade.mbia.identity.application.provisionuser.ProvisionCurrentUserCommand;
import com.lehnade.mbia.identity.application.provisionuser.ProvisionCurrentUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Before any API handler, maps the validated access token to a Mbia User, creating it on the first
 * call (ADR-005). A rejection, such as {@code EMAIL_NOT_VERIFIED}, is rendered as a problem by the
 * global exception handler.
 */
class CurrentUserInterceptor implements HandlerInterceptor {

    private final ProvisionCurrentUserUseCase provisionCurrentUser;

    CurrentUserInterceptor(ProvisionCurrentUserUseCase provisionCurrentUser) {
        this.provisionCurrentUser = provisionCurrentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            Jwt token = authentication.getToken();
            var command = new ProvisionCurrentUserCommand(
                    token.getSubject(),
                    token.getClaimAsString("email"),
                    Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")),
                    token.getClaimAsString("name"),
                    token.getClaimAsString("locale"));
            RequestCurrentUserAccessor.store(request, provisionCurrentUser.provision(command));
        }
        return true;
    }
}
