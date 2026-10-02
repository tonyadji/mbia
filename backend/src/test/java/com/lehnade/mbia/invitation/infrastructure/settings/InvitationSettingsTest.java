package com.lehnade.mbia.invitation.infrastructure.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.invitation.application.InvitationSettings;
import com.lehnade.mbia.invitation.domain.InvitationToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PR-47: {@code mbia.invitations.base-url} is the frontend address the invitation links start with
 * (Phase 5 plan PR-47); the application refuses to start without a valid one.
 */
class InvitationSettingsTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(InvitationSettingsConfiguration.class);

    @ParameterizedTest
    @ValueSource(strings = {"https://app.mbia.example.com", "https://app.mbia.example.com/", "http://localhost:5173"})
    void theLinkIsTheBaseUrlFollowedByTheInvitationPath(String baseUrl) {
        context.withPropertyValues("mbia.invitations.base-url=" + baseUrl)
                .run(started -> {
                    assertThat(started).hasNotFailed();
                    InvitationToken token = InvitationToken.generate();
                    String expected = baseUrl.replaceAll("/$", "") + "/invitations/" + token.value();
                    assertThat(started.getBean(InvitationSettings.class).inviteUrl(token)).hasToString(expected);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "app.mbia.example.com", "ftp://app.mbia.example.com", "https://"})
    void anInvalidBaseUrlFailsTheStart(String baseUrl) {
        context.withPropertyValues("mbia.invitations.base-url=" + baseUrl)
                .run(started -> assertThat(started).hasFailed());
    }
}
