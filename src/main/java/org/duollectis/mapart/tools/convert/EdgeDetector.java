package org.duollectis.mapart.tools.convert;

import java.awt.image.BufferedImage;

public class EdgeDetector {

    public static double[][] computeEdgeMap(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[][] edgeMap = new double[w][h];

        // Матрицы Собеля
        int[][] Gx = {
                { -1, 0, 1 },
                { -2, 0, 2 },
                { -1, 0, 1 }
        };
        int[][] Gy = {
                { -1, -2, -1 },
                { 0, 0, 0 },
                { 1, 2, 1 }
        };

        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                double intensityX = 0;
                double intensityY = 0;

                // Проход окном 3x3
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        // Берем яркость пикселя (L из нашего Lab или просто серый)
                        int rgb = img.getRGB(x + i, y + j);
                        double luma = getLuma(rgb);

                        intensityX += Gx[i + 1][j + 1] * luma;
                        intensityY += Gy[i + 1][j + 1] * luma;
                    }
                }

                double magnitude = Math.sqrt(intensityX * intensityX + intensityY * intensityY);
                // Нормализуем (примерный порог, можно подкрутить)
                edgeMap[x][y] = Math.min(1.0, magnitude / 255.0);
            }
        }
        return edgeMap;
    }

    private static double getLuma(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        // Стандартная формула яркости для глаза
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
