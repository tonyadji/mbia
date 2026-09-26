package com.lehnade.mbia.shared.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * In-process scheduled tasks (ADR-007 §4). Off with {@code mbia.scheduling.enabled=false}, as in
 * tests, which run the tasks themselves.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "mbia.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfiguration {}
