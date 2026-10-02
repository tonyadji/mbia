package com.lehnade.mbia.memory.infrastructure.imaging;

import static com.lehnade.mbia.memory.MediaImages.assertHasGpsAndXmp;
import static com.lehnade.mbia.memory.MediaImages.assertHasNoMetadata;
import static com.lehnade.mbia.memory.MediaImages.decode;
import static com.lehnade.mbia.memory.MediaImages.fixture;
import static com.lehnade.mbia.memory.MediaImages.isJpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.memory.MediaImages;
import com.lehnade.mbia.memory.application.InvalidImageException;
import com.lehnade.mbia.memory.application.ProcessedImage;
import com.lehnade.mbia.memory.domain.MediaFailureReason;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PR-36: an uploaded JPEG, PNG or WEBP becomes two JPEG derivatives, oriented as seen, within
 * 2048 px and 480 px, holding no metadata at all (ADR-007 §3; mvp.md §23).
 */
class ImageIoImageProcessorTest {

    private final ImageIoImageProcessor processor = new ImageIoImageProcessor();

    @ParameterizedTest
    @ValueSource(strings = {MediaImages.JPEG_ORIENTATION_6, MediaImages.PNG_WITH_GPS, MediaImages.WEBP_WITH_GPS,
            MediaImages.LARGE_WITH_GPS})
    void theDerivativesAreJpegsWithoutAnyMetadata(String name) {
        byte[] source = fixture(name);
        assertHasGpsAndXmp(source);

        ProcessedImage image = processor.process(source);

        for (byte[] derivative : new byte[][] {image.display(), image.thumbnail()}) {
            assertThat(isJpeg(derivative)).isTrue();
            assertHasNoMetadata(derivative);
        }
    }

    /** The check of the derivatives is not blind: it refuses every fixture as uploaded. */
    @ParameterizedTest
    @ValueSource(strings = {MediaImages.JPEG_ORIENTATION_6, MediaImages.PNG_WITH_GPS, MediaImages.WEBP_WITH_GPS,
            MediaImages.LARGE_WITH_GPS})
    void theMetadataCheckRefusesTheUploadedImages(String name) {
        assertThatThrownBy(() -> assertHasNoMetadata(fixture(name))).isInstanceOf(AssertionError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {MediaImages.PNG_WITH_GPS, MediaImages.WEBP_WITH_GPS})
    void aSmallImageKeepsItsSizeAndItsPixels(String name) {
        ProcessedImage image = processor.process(fixture(name));

        BufferedImage display = decode(image.display());
        assertThat(image.displayWidth()).isEqualTo(64);
        assertThat(image.displayHeight()).isEqualTo(32);
        assertThat(display.getWidth()).isEqualTo(64);
        assertThat(display.getHeight()).isEqualTo(32);
        assertThat(decode(image.thumbnail()).getWidth()).isEqualTo(64);
        assertRed(display, 8, 4);
        assertBlue(display, 48, 24);
    }

    @Test
    void theExifOrientationIsApplied() {
        ProcessedImage image = processor.process(fixture(MediaImages.JPEG_ORIENTATION_6));

        // Stored 64×32 with its top-left quadrant red; orientation 6 turns it 90° clockwise.
        BufferedImage display = decode(image.display());
        assertThat(image.displayWidth()).isEqualTo(32);
        assertThat(image.displayHeight()).isEqualTo(64);
        assertThat(display.getWidth()).isEqualTo(32);
        assertThat(display.getHeight()).isEqualTo(64);
        assertRed(display, 24, 12);
        assertBlue(display, 8, 12);
        assertBlue(display, 24, 52);
    }

    @Test
    void aLargeImageIsReducedTo2048AndItsThumbnailTo480() {
        ProcessedImage image = processor.process(fixture(MediaImages.LARGE_WITH_GPS));

        BufferedImage display = decode(image.display());
        BufferedImage thumbnail = decode(image.thumbnail());
        assertThat(display.getWidth()).isEqualTo(2048);
        assertThat(display.getHeight()).isEqualTo(1365);
        assertThat(image.displayWidth()).isEqualTo(2048);
        assertThat(image.displayHeight()).isEqualTo(1365);
        assertThat(thumbnail.getWidth()).isEqualTo(480);
        assertThat(thumbnail.getHeight()).isEqualTo(320);
        assertRed(display, 500, 300);
        assertBlue(display, 1500, 1000);
    }

    /** Where the top-left pixel of a stored 4×2 image is seen, for each EXIF orientation. */
    @ParameterizedTest
    @CsvSource({"1, 4, 2, 0, 0", "2, 4, 2, 3, 0", "3, 4, 2, 3, 1", "4, 4, 2, 0, 1",
            "5, 2, 4, 0, 0", "6, 2, 4, 1, 0", "7, 2, 4, 1, 3", "8, 2, 4, 0, 3"})
    void everyExifOrientationIsApplied(int orientation, int width, int height, int x, int y) {
        BufferedImage stored = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        stored.setRGB(0, 0, Color.RED.getRGB());

        BufferedImage seen = ImageIoImageProcessor.orient(stored, orientation);

        assertThat(seen.getWidth()).isEqualTo(width);
        assertThat(seen.getHeight()).isEqualTo(height);
        assertThat(seen.getRGB(x, y)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void transparencyBecomesWhite() throws IOException {
        BufferedImage transparent = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", png);

        BufferedImage display = decode(processor.process(png.toByteArray()).display());

        Color pixel = new Color(display.getRGB(4, 4));
        assertThat(pixel.getRed()).isGreaterThan(245);
        assertThat(pixel.getGreen()).isGreaterThan(245);
        assertThat(pixel.getBlue()).isGreaterThan(245);
    }

    @Test
    void anImageAboveFortyMegapixelsIsRefusedFromItsHeader() {
        assertRefused(fixture(MediaImages.OVER_40_MEGAPIXELS), MediaFailureReason.TOO_MANY_PIXELS);
    }

    @Test
    void contentThatIsNotAnImageIsUnreadable() {
        assertRefused(fixture(MediaImages.TEXT_RENAMED_JPG), MediaFailureReason.UNREADABLE);
        byte[] truncated = Arrays.copyOf(fixture(MediaImages.JPEG_ORIENTATION_6), 200);
        assertRefused(truncated, MediaFailureReason.UNREADABLE);
    }

    private void assertRefused(byte[] content, MediaFailureReason reason) {
        assertThatThrownBy(() -> processor.process(content))
                .isInstanceOfSatisfying(InvalidImageException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private static void assertRed(BufferedImage image, int x, int y) {
        Color pixel = new Color(image.getRGB(x, y));
        assertThat(pixel.getRed()).as("red at %d,%d", x, y).isGreaterThan(180);
        assertThat(pixel.getBlue()).as("red at %d,%d", x, y).isLessThan(80);
    }

    private static void assertBlue(BufferedImage image, int x, int y) {
        Color pixel = new Color(image.getRGB(x, y));
        assertThat(pixel.getBlue()).as("blue at %d,%d", x, y).isGreaterThan(180);
        assertThat(pixel.getRed()).as("blue at %d,%d", x, y).isLessThan(80);
    }
}
