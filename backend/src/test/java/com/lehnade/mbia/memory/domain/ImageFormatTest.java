package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * PR-36: an upload is recognised by its first bytes, never by its declared type or its name
 * (ADR-007 §3; mvp.md §23: JPEG, PNG and WEBP only).
 */
class ImageFormatTest {

    @Test
    void jpegPngAndWebpAreRecognisedByTheirSignature() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P', '8')))
                .contains(ImageFormat.WEBP);
    }

    @Test
    void eachFormatHasItsMimeType() {
        assertThat(ImageFormat.JPEG.mimeType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.PNG.mimeType()).isEqualTo("image/png");
        assertThat(ImageFormat.WEBP.mimeType()).isEqualTo("image/webp");
    }

    @Test
    void anythingElseIsNotAnImage() {
        assertThat(ImageFormat.detect("A text renamed .jpg".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty();
        // GIF, and a RIFF file that is not a WEBP (a WAV sound).
        assertThat(ImageFormat.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'))).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
