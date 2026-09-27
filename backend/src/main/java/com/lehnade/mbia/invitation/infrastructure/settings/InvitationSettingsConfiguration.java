package com.lehnade.mbia.invitation.infrastructure.settings;

import com.lehnade.mbia.invitation.application.InvitationSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds and validates {@code mbia.invitations.*}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvitationSettings.class)
class InvitationSettingsConfiguration {}
