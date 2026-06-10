package org.duollectis.mapart.tools.utils.image;

import java.awt.image.BufferedImage;

/**
 * Результат вписывания изображения в холст.
 * Содержит готовый холст и координаты реальной области изображения внутри него.
 * Clip rect используется дизерером для подавления артефактов Floyd-Steinberg:
 * пиксели вне области не участвуют в диффузии ошибки.
 *
 * @param image  холст целевого размера с нарисованным изображением
 * @param clipX  X-координата начала реальной области (пиксели холста)
 * @param clipY  Y-координата начала реальной области (пиксели холста)
 * @param clipW  ширина реальной области (0 если изображение полностью за пределами)
 * @param clipH  высота реальной области (0 если изображение полностью за пределами)
 */
public record FitResult(BufferedImage image, int clipX, int clipY, int clipW, int clipH) {
}
