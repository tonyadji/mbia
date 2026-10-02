package com.lehnade.mbia.memory.application;

/**
 * Turns an uploaded image into the two JPEG derivatives served to the browser (ADR-007 §3): the
 * EXIF orientation applied, no metadata at all, {@code display} with a long edge of at most
 * 2048 px and {@code thumbnail} of at most 480 px. An image is never enlarged.
 */
public interface ImageProcessor {

    /**
     * @param content a JPEG, PNG or WEBP image, whose type was checked by its first bytes
     * @throws InvalidImageException {@code TOO_MANY_PIXELS} above 40 megapixels, checked before
     *     the image is decoded; {@code UNREADABLE} when it cannot be decoded
     */
    ProcessedImage process(byte[] content);
}
