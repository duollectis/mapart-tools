package org.duollectis.mapart.tools.convert;

public class ColorSpace {
    // Перевод из стандартного RGB в Lab
    public static double[] rgbToLab(int R, int G, int B) {
        // sRGB в линейный формат
        double r = pivotRGB(R / 255.0);
        double g = pivotRGB(G / 255.0);
        double b = pivotRGB(B / 255.0);

        // Линейный sRGB в XYZ (D65)
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;

        // XYZ в Lab
        return xyzToLab(x, y, z);
    }

    private static double pivotRGB(double n) {
        return (n > 0.04045) ? Math.pow((n + 0.055) / 1.055, 2.4) : n / 12.92;
    }

    private static double[] xyzToLab(double x, double y, double z) {
        x /= 0.95047;
        y /= 1.00000;
        z /= 1.08883;
        x = pivotXYZ(x);
        y = pivotXYZ(y);
        z = pivotXYZ(z);
        return new double[] {(116 * y) - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double pivotXYZ(double n) {
        return (n > 0.008856) ? Math.pow(n, 1.0 / 3.0) : (7.787 * n) + (16.0 / 116.0);
    }
}
