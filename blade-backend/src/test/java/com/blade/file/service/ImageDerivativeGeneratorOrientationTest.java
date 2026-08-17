package com.blade.file.service;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link ImageDerivativeGenerator#applyOrientation} with non-symmetric
 * real BufferedImages and pixel-level assertions.  Same package so we can
 * reach the package-private method.
 */
class ImageDerivativeGeneratorOrientationTest {

    private final ImageDerivativeGenerator gen = new ImageDerivativeGenerator();

    @Test
    void orientation6_swapsDimensionsAndMovesVisiblePixels() {
        // Non-symmetric 200×100: left half RED, right half BLUE.
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 100, 100);
        g.dispose();

        BufferedImage result = gen.applyOrientation(src, 6); // 90° CW

        // Dimensions swap
        assertEquals(100, result.getWidth());
        assertEquals(200, result.getHeight());

        int red = Color.RED.getRGB();
        int blue = Color.BLUE.getRGB();

        // After rotate-90-CW, left-source (RED) pixels map to the right side
        // of the output, top portion.  Blue maps to the right side, bottom portion.
        assertEquals(red, result.getRGB(50, 20),
                "Top-centre should be RED (from left source after CW rotation)");
        assertEquals(blue, result.getRGB(50, 150),
                "Bottom-centre should be BLUE (from right source after CW rotation)");

        // Clipping / blank check
        assertNotEquals(0, result.getRGB(50, 20),
                "Top-centre must not be blank (clipping)");
        assertNotEquals(Color.WHITE.getRGB(), result.getRGB(50, 20),
                "Top-centre must not be white");
    }

    @Test
    void orientation1_identity_noChange() {
        BufferedImage src = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        src.setRGB(10, 10, Color.RED.getRGB());
        BufferedImage result = gen.applyOrientation(src, 1);
        assertEquals(50, result.getWidth());
        assertEquals(50, result.getHeight());
        assertEquals(Color.RED.getRGB(), result.getRGB(10, 10));
    }
}
