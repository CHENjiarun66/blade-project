package com.blade.file.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stateless image derivative generator.
 * <p>
 * Handles JPEG, PNG, and WebP decoding (ImageIO plugin at runtime); EXIF orientation correction with
 * proper translation; transparency compositing onto white background;
 * aspect-ratio-preserving scaling to a target long edge; and JPEG encoding.
 * <p>
 * A source dimension guard runs after ImageIO decode but before oriented,
 * composited, or scaled output allocation — the original decoded pixels are
 * already in memory at that point; the guard prevents the subsequent
 * intermediate and output allocations from amplifying memory pressure.
 */
@Component
public class ImageDerivativeGenerator {

    private static final float JPEG_QUALITY = 0.85f;
    /** Maximum pixel count per dimension before failing (limit for safe memory use). */
    static final int MAX_SOURCE_DIMENSION = 8000;

    /**
     * Generate a scaled JPEG derivative from raw image bytes.
     *
     * @param imageBytes     original image bytes
     * @param targetLongEdge max long edge in pixels
     * @return derivative result containing JPEG bytes, width, and height
     * @throws IOException if the image cannot be decoded or encoded
     */
    public DerivativeResult generate(byte[] imageBytes, int targetLongEdge) throws IOException {
        BufferedImage original = decode(imageBytes);

        // Dimension guard: reject images with a side > MAX_SOURCE_DIMENSION
        if (original.getWidth() > MAX_SOURCE_DIMENSION || original.getHeight() > MAX_SOURCE_DIMENSION) {
            throw new IOException("图片尺寸过大 ("
                    + original.getWidth() + "x" + original.getHeight()
                    + ")，超过最大支持尺寸 " + MAX_SOURCE_DIMENSION + "px");
        }

        int orientation = readExifOrientation(imageBytes);
        BufferedImage oriented = applyOrientation(original, orientation);
        // Composite alpha onto white background for clean JPEG output
        BufferedImage opaque = compositeOntoWhite(oriented);
        BufferedImage scaled = scaleToLongEdge(opaque, targetLongEdge);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeJpeg(scaled, out);

        return new DerivativeResult(out.toByteArray(), scaled.getWidth(), scaled.getHeight());
    }

    // === internal ===

    private BufferedImage decode(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new IOException("无法解码图片（不支持的格式或损坏的文件）");
            }
            return img;
        }
    }

    private int readExifOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // No EXIF data or unreadable — default to normal orientation
        }
        return 1;
    }

    /**
     * Apply EXIF orientation with correct translation.
     * <p>
     * Package-private for testing. Each case produces an AffineTransform that
     * maps from the oriented output coordinate space back to the original image.
     * The drawImage call then renders the original pixels at the correct offset
     * so no pixels are clipped.
     */
    BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation == 1) {
            return image; // Normal — no transform needed
        }

        int w = image.getWidth();
        int h = image.getHeight();
        boolean swapDimensions = orientation >= 5 && orientation <= 8;
        int newW = swapDimensions ? h : w;
        int newH = swapDimensions ? w : h;

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2: // Flip horizontal
                transform.scale(-1, 1);
                transform.translate(-w, 0);
                break;
            case 3: // Rotate 180
                transform.translate(w, h);
                transform.rotate(Math.PI);
                break;
            case 4: // Flip vertical
                transform.scale(1, -1);
                transform.translate(0, -h);
                break;
            case 5: // Flip horizontal + rotate 270
                transform.translate(h - w, 0);
                transform.scale(-1, 1);
                transform.translate(0, h);
                transform.rotate(-Math.PI / 2);
                break;
            case 6: // Rotate 90 CW
                transform.translate(h, 0);
                transform.rotate(Math.PI / 2);
                break;
            case 7: // Flip horizontal + rotate 90
                transform.translate(h, w);
                transform.scale(-1, 1);
                transform.translate(-h, 0);
                transform.rotate(Math.PI / 2);
                break;
            case 8: // Rotate 270 CW / 90 CCW
                transform.translate(0, w);
                transform.rotate(-Math.PI / 2);
                break;
            default:
                return image;
        }

        BufferedImage result = new BufferedImage(newW, newH,
                image.getTransparency() == Transparency.OPAQUE ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, transform, null);
        g.dispose();
        return result;
    }

    /**
     * Composite alpha onto a white background so JPEG encoding produces
     * clean opaque pixels instead of black replacement.
     */
    private BufferedImage compositeOntoWhite(BufferedImage image) {
        if (image.getTransparency() == Transparency.OPAQUE) {
            return image;
        }
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    private BufferedImage scaleToLongEdge(BufferedImage image, int targetLongEdge) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longEdge = Math.max(w, h);

        if (longEdge <= targetLongEdge) {
            return image; // already within bounds
        }

        double ratio = (double) targetLongEdge / longEdge;
        int newW = Math.max(1, (int) Math.round(w * ratio));
        int newH = Math.max(1, (int) Math.round(h * ratio));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();
        return scaled;
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        // Ensure RGB for JPEG
        BufferedImage rgb = image.getType() == BufferedImage.TYPE_INT_RGB
                ? image
                : toRgb(image);

        var writer = ImageIO.getImageWritersByFormatName("JPEG").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        try (var ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage toRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /**
     * Holds the result of a derivative generation.
     */
    public static class DerivativeResult {
        private final byte[] bytes;
        private final int width;
        private final int height;

        public DerivativeResult(byte[] bytes, int width, int height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }

        public byte[] getBytes() { return bytes; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
}
