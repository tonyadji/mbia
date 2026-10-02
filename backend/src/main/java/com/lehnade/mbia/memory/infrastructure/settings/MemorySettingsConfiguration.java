package com.lehnade.mbia.memory.infrastructure.settings;

import com.lehnade.mbia.family.application.MemoryLimitsPort;
import com.lehnade.mbia.memory.application.MemorySettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds and validates {@code mbia.memory.*}, and gives its limits to the family module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemorySettings.class)
class MemorySettingsConfiguration {

    @Bean
    MemoryLimitsPort memoryLimits(MemorySettings settings) {
        return settings::maxPhotos;
    }
}
