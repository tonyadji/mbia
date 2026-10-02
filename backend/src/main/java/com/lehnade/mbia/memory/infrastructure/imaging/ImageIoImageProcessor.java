package com.lehnade.mbia.memory.infrastructure.imaging;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.lehnade.mbia.memory.application.ImageProcessor;
import com.lehnade.mbia.memory.application.InvalidImageException;
import com.lehnade.mbia.memory.application.ProcessedImage;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaFailureReason;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * {@link ImageProcessor} with {@code javax.imageio} (ADR-007): TwelveMonkeys reads WEBP and phone
 * JPEGs, metadata-extractor reads the EXIF orientation, and the JDK JPEG writer writes the
 * derivatives from pixels only, so that no metadata of the upload can reach them.
 */
@Component
class ImageIoImageProcessor implements ImageProcessor {

    static final int DISPLAY_LONG_EDGE = 2048;
    static final int THUMBNAIL_LONG_EDGE = 480;
    private static final float JPEG_QUALITY = 0.85f;

    ImageIoImageProcessor() {
        // The plugins of a Spring Boot jar are not on the class path ImageIO scans by itself.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    @Override
    public ProcessedImage process(byte[] content) {
        BufferedImage display = scaleDown(orient(decode(content), orientation(content)), DISPLAY_LONG_EDGE);
        BufferedImage thumbnail = scaleDown(display, THUMBNAIL_LONG_EDGE);
        return new ProcessedImage(jpeg(display), display.getWidth(), display.getHeight(), jpeg(thumbnail));
    }

    /**
     * Refuses an image above 40 megapixels from its header, before decoding it. Large images are
     * decoded subsampled, only as large as the display derivative needs, to bound the memory used.
     */
    private static BufferedImage decode(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException(MediaFailureReason.UNREADABLE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > MediaAsset.MAX_PIXELS) {
                    throw new InvalidImageException(MediaFailureReason.TOO_MANY_PIXELS);
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = (int) Math.max(1, Math.max(width, height) / DISPLAY_LONG_EDGE);
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Readers fail on corrupt content in many ways: all mean the upload is not an image.
            throw new InvalidImageException(MediaFailureReason.UNREADABLE);
        }
    }

    /** @return the EXIF orientation, 1 (as stored) when there is none */
    private static int orientation(byte[] content) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(content));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = exif == null ? null : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation != null && orientation >= 1 && orientation <= 8 ? orientation : 1;
        } catch (Exception e) {
            // Unreadable metadata: the pixels are shown as stored.
            return 1;
        }
    }

    /**
     * Draws the image as it must be seen (EXIF orientation 1 to 8), on a white background for
     * transparent images, in RGB: the only pixel layout written to JPEG.
     */
    static BufferedImage orient(BufferedImage source, int orientation) {
        int w = source.getWidth();
        int h = source.getHeight();
        // (x, y) of the stored image → (x', y') of the image as seen.
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
        boolean turned = orientation >= 5 && orientation <= 8;
        BufferedImage oriented = new BufferedImage(turned ? h : w, turned ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = oriented.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, oriented.getWidth(), oriented.getHeight());
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return oriented;
    }

    /**
     * Reduces the long edge to {@code maxLongEdge}, never enlarging, by successive halvings: a
     * single bilinear step would skip pixels and alias.
     */
    static BufferedImage scaleDown(BufferedImage source, int maxLongEdge) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        if (longEdge <= maxLongEdge) {
            return source;
        }
        double ratio = (double) maxLongEdge / longEdge;
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage current = source;
        while (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            int width = Math.max(targetWidth, current.getWidth() / 2);
            int height = Math.max(targetHeight, current.getHeight() / 2);
            BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = next.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(current, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            current = next;
        }
        return current;
    }

    /** A baseline JPEG of the pixels alone: no EXIF, GPS, XMP, IPTC nor ICC profile. */
    private static byte[] jpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("A derivative could not be encoded.", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
