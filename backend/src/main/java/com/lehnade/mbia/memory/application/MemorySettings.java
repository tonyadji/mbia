package com.lehnade.mbia.memory.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code mbia.memory.*}: the settings of Memories (Phase 4 plan §3.5). The application refuses to
 * start with a value outside its bounds.
 *
 * @param maxPhotos the maximum number of photos of a Memory, enforced on every addition
 *     (data-model.md §14bis, OQ-042); environment variable {@code MBIA_MEMORY_MAX_PHOTOS}
 */
@Validated
@ConfigurationProperties("mbia.memory")
public record MemorySettings(@Min(1) @Max(10) int maxPhotos) {}
