package org.duollectis.mapart.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.duollectis.mapart.tools.convert.ColorSpace;

public class ColorSpaceTest {

    @Test
    public void testRgbToLabWhite() {
        double[] lab = ColorSpace.rgbToLab(255, 255, 255);
        assertArrayEquals(new double[] { 100.0, 0.0, 0.0 }, lab, 0.001);
    }

    @Test
    public void testRgbToLabBlack() {
        double[] lab = ColorSpace.rgbToLab(0, 0, 0);
        assertArrayEquals(new double[] { 0.0, 0.0, 0.0 }, lab, 0.001);
    }

    @Test
    public void testRgbToLabRed() {
        double[] lab = ColorSpace.rgbToLab(255, 0, 0);
        assertArrayEquals(new double[] { 53.2, 80.1, 67.2 }, lab, 0.0408);
    }

    @Test
    public void testRgbToLabYellow() {
        double[] lab = ColorSpace.rgbToLab(200, 200, 0);
        assertArrayEquals(new double[] { 78.22, -17.9, 78.6 }, lab, 0.1);
    }
}
