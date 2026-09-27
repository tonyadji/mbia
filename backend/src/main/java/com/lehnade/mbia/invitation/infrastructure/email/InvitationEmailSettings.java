package com.lehnade.mbia.invitation.infrastructure.email;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code mbia.mail.*}: the sender of Mbia's emails. The application refuses to start without it.
 *
 * @param from the address emails are sent from, for example {@code Mbia <no-reply@mbia.example.com>};
 *     environment variable {@code MBIA_MAIL_FROM}
 */
@Validated
@ConfigurationProperties("mbia.mail")
record InvitationEmailSettings(@NotBlank String from) {}
