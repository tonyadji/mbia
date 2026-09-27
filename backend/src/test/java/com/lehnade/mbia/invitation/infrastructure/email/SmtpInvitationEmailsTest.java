package com.lehnade.mbia.invitation.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.invitation.application.InvitationEmail;
import com.lehnade.mbia.invitation.domain.InvitationRole;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * PR-51, OQ-055: a mail provider that cannot be reached fails the sending, within the SMTP
 * timeouts, so that the invitation's email is marked FAILED.
 */
class SmtpInvitationEmailsTest {

    @Test
    void aProviderThatCannotBeReachedFailsTheSending() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(closedPort);
        Properties properties = new Properties();
        properties.put("mail.smtp.connectiontimeout", "2000");
        sender.setJavaMailProperties(properties);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/emails");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        SmtpInvitationEmails emails = new SmtpInvitationEmails(sender, messages,
                new InvitationEmailSettings("Mbia <no-reply@mbia.test>"));

        assertThatThrownBy(() -> emails.send(new InvitationEmail("awa@example.com", "en", "ADJI", "Tony Adji",
                InvitationRole.VIEWER, URI.create("http://localhost:5173/invitations/abc"), Instant.now())))
                .isInstanceOf(MailException.class);
    }
}
