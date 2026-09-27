package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.invitation.domain.InvitationToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code mbia.invitations.*}: the settings of invitations. The application refuses to start
 * without a valid base URL.
 *
 * @param baseUrl the address of the frontend, where an invitation link opens (SCREEN-010);
 *     environment variable {@code MBIA_APP_BASE_URL}
 */
@Validated
@ConfigurationProperties("mbia.invitations")
public record InvitationSettings(@NotBlank @Pattern(regexp = "https?://[^\\s/?#]+(/[^\\s?#]*)?") String baseUrl) {

    /** The link of an invitation: {@code <baseUrl>/invitations/<raw token>}. */
    public URI inviteUrl(InvitationToken token) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + "/invitations/" + token.value());
    }
}
