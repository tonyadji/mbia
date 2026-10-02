package com.lehnade.mbia.memory.infrastructure.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.family.application.MemoryLimitsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PR-40: {@code mbia.memory.max-photos} is between 1 and 10, otherwise the application refuses to
 * start (Phase 4 plan §3.5); the family module reads it through its port (§3.4).
 */
class MemorySettingsTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MemorySettingsConfiguration.class);

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 10})
    void aLimitBetween1And10IsGivenToTheFamilyModule(int maxPhotos) {
        context.withPropertyValues("mbia.memory.max-photos=" + maxPhotos)
                .run(started -> {
                    assertThat(started).hasNotFailed();
                    assertThat(started.getBean(MemoryLimitsPort.class).maxPhotosPerMemory()).isEqualTo(maxPhotos);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "-1", "trois"})
    void aLimitOutside1To10FailsTheStart(String maxPhotos) {
        context.withPropertyValues("mbia.memory.max-photos=" + maxPhotos)
                .run(started -> assertThat(started).hasFailed());
    }

    @Test
    void aMissingLimitFailsTheStart() {
        context.run(started -> assertThat(started).hasFailed());
    }
}
