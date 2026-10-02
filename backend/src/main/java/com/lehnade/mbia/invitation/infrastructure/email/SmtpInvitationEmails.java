package com.lehnade.mbia.invitation.infrastructure.email;

import com.lehnade.mbia.invitation.application.InvitationEmail;
import com.lehnade.mbia.invitation.application.InvitationEmails;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The invitation email through SMTP (stack.md; Mailpit locally and in tests), in French or English
 * from the {@code i18n/emails} message bundles (technical-specification.md §16bis): a plain-text
 * part and the same text as simple HTML, every value escaped. No template engine.
 */
@Component
@EnableConfigurationProperties(InvitationEmailSettings.class)
class SmtpInvitationEmails implements InvitationEmails {

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final InvitationEmailSettings settings;

    SmtpInvitationEmails(JavaMailSender mailSender, MessageSource messages, InvitationEmailSettings settings) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public void send(InvitationEmail email) {
        Locale locale = Locale.forLanguageTag(email.locale());
        String role = text(locale, "invitation.role." + email.role().name());
        // The expiry date as the API gives it, in UTC.
        String expiry = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
                .format(email.expiresAt().atZone(ZoneOffset.UTC));
        String link = email.inviteUrl().toString();
        String intro = text(locale, "invitation.intro", email.inviterName(), email.familyName());
        String permission = text(locale, "invitation.permission", role);
        String action = text(locale, "invitation.action");
        String validity = text(locale, "invitation.validity", expiry);
        String unexpected = text(locale, "invitation.unexpected");

        String plain = String.join("\n\n", intro, permission, action + "\n" + link, validity, unexpected);
        String html = "<!doctype html><html lang=\"" + locale.getLanguage() + "\"><body"
                + " style=\"font-family:sans-serif;font-size:16px;line-height:1.5;color:#1a1f1b\">"
                + paragraph(intro) + paragraph(permission)
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:12px 20px;"
                + "background:#2f5c4e;color:#ffffff;border-radius:8px;text-decoration:none;font-weight:600\">"
                + HtmlUtils.htmlEscape(action) + "</a></p>"
                + paragraph(validity) + "<p style=\"color:#62675f;font-size:14px\">" + HtmlUtils.htmlEscape(unexpected)
                + "</p></body></html>";

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(email.to());
            helper.setSubject(text(locale, "invitation.subject", email.inviterName(), email.familyName()));
            helper.setText(plain, html);
        } catch (MessagingException invalid) {
            throw new MailPreparationException(invalid);
        }
        mailSender.send(message);
    }

    private String text(Locale locale, String key, Object... arguments) {
        return messages.getMessage(key, arguments, locale);
    }

    private static String paragraph(String text) {
        return "<p>" + HtmlUtils.htmlEscape(text) + "</p>";
    }
}
