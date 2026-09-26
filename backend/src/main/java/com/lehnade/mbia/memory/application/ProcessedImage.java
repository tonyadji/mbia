package com.lehnade.mbia.memory.application;

/**
 * The JPEG derivatives of an uploaded image, without metadata (ADR-007 §3).
 *
 * @param displayWidth the width of {@code display}, in pixels
 * @param displayHeight the height of {@code display}, in pixels
 */
public record ProcessedImage(byte[] display, int displayWidth, int displayHeight, byte[] thumbnail) {}
