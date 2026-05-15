package org.duollectis.mapart.tools.utils.image;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageUtils {

	public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
		// Создаем пустой холст нужного размера
		BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

		// Рисуем на нем
		Graphics2D g2d = resizedImage.createGraphics();

		// Настройки качества (BICUBIC — золотая середина)
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

		g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose(); // Обязательно освобождаем ресурсы

		return resizedImage;
	}
}
