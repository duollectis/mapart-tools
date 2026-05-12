package org.duollectis.mapart.tools.convert;

import java.awt.image.BufferedImage;

public class DitherEngine {

    // public void process(BufferedImage img, double[][] edgeMap, Palette palette) {
    //     int w = img.getWidth();
    //     int h = img.getHeight();

    //     // Буфер для хранения ошибок (3 канала: L, a, b)
    //     // Делаем его чуть больше, чтобы не проверять границы массива
    //     double[][][] errorBuffer = new double[w + 4][h + 4][3];

    //     for (int y = 0; y < h; y++) {
    //         for (int x = 0; x < w; x++) {
    //             // 1. Берем исходный цвет пикселя и переводим в Lab
    //             int rgb = img.getRGB(x, y);
    //             double[] currentLab = ColorMath.rgbToLab(
    //                     (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);

    //             // 2. Добавляем накопленную ошибку из буфера
    //             double[] targetLab = {
    //                     currentLab[0] + errorBuffer[x + 2][y][0],
    //                     currentLab[1] + errorBuffer[x + 2][y][1],
    //                     currentLab[2] + errorBuffer[x + 2][y][2]
    //             };

    //             // 3. Ищем ЛУЧШИЙ блок (CIEDE2000) во всей 3D-палитре
    //             // (palette.findBest вернет объект с Lab-цветом выбранного блока)
    //             BlockMatch bestMatch = palette.findBest(targetLab);
    //             saveBlockToResult(x, y, bestMatch);

    //             // 4. Считаем ошибку (что хотели - что получили)
    //             double[] error = {
    //                     targetLab[0] - bestMatch.lab[0],
    //                     targetLab[1] - bestMatch.lab[1],
    //                     targetLab[2] - bestMatch.lab[2]
    //             };

    //             // 5. АДАПТИВНОСТЬ: Если это край (Edge), гасим ошибку
    //             // Это предотвращает "ошметки" и ореолы вокруг контуров тела
    //             double edgeStrength = edgeMap[x][y];
    //             double reduction = 1.0 - (edgeStrength * 0.8); // на жестких краях оставляем только
    //                                                            // 20% ошибки

    //             for (int i = 0; i < 3; i++)
    //                 error[i] *= reduction;

    //             // 6. Распределяем ошибку по матрице Stucki (42 - делитель)
    //             distributeStucki(errorBuffer, x + 2, y, error);
    //         }
    //     }
    // }

    private void distributeStucki(double[][][] buffer, int x, int y, double[] err) {
        // Ряд 0
        addErr(buffer, x + 1, y, err, 8 / 42.0);
        addErr(buffer, x + 2, y, err, 4 / 42.0);
        // Ряд 1
        addErr(buffer, x - 2, y + 1, err, 2 / 42.0);
        addErr(buffer, x - 1, y + 1, err, 4 / 42.0);
        addErr(buffer, x, y + 1, err, 8 / 42.0);
        addErr(buffer, x + 1, y + 1, err, 4 / 42.0);
        addErr(buffer, x + 2, y + 1, err, 2 / 42.0);
        // Ряд 2
        addErr(buffer, x - 2, y + 2, err, 1 / 42.0);
        addErr(buffer, x - 1, y + 2, err, 2 / 42.0);
        addErr(buffer, x, y + 2, err, 4 / 42.0);
        addErr(buffer, x + 1, y + 2, err, 2 / 42.0);
        addErr(buffer, x + 2, y + 2, err, 1 / 42.0);
    }

    private void addErr(double[][][] buffer, int x, int y, double[] err, double weight) {
        for (int i = 0; i < 3; i++) {
            buffer[x][y][i] += err[i] * weight;
        }
    }
}
