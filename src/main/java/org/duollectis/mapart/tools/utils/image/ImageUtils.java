package org.duollectis.mapart.tools.utils.image;

import lombok.experimental.UtilityClass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@UtilityClass
public class ImageUtils {

	/**
	 * Масштабирует изображение до указанных размеров с использованием бикубической интерполяции.
	 * Бикубика обеспечивает баланс между скоростью и качеством при масштабировании.
	 */
	public BufferedImage resizeImage(BufferedImage source, int targetWidth, int targetHeight) {
		BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

		Graphics2D graphics = result.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		graphics.dispose();

		return result;
	}

	/**
	 * Применяет коррекцию изображения (яркость, контраст, насыщенность, гамма, оттенок).
	 * Обработка выполняется попиксельно в пространстве HSB для корректной работы
	 * с насыщенностью и оттенком, и в линейном RGB для яркости/контраста/гаммы.
	 * Нейтральные настройки ({@link ImageAdjustments#isNeutral()}) пропускаются без обработки.
	 */
	public BufferedImage applyAdjustments(BufferedImage source, ImageAdjustments adj) {
		if (adj.isNeutral()) {
			return source;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		// Предвычисляем LUT для гаммы — избегаем pow() в горячем цикле
		double gammaValue = adj.gamma() / 100.0;
		int[] gammaLut = buildGammaLut(gammaValue);

		// Нормализуем параметры в рабочие диапазоны
		float brightnessShift = adj.brightness() / 100.0f;
		float contrastFactor = buildContrastFactor(adj.contrast());
		float saturationFactor = 1.0f + adj.saturation() / 100.0f;
		float hueShift = adj.hue() / 360.0f;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >> 24) & 0xFF;
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				// Гамма-коррекция через LUT
				r = gammaLut[r];
				g = gammaLut[g];
				b = gammaLut[b];

				// Яркость и контраст в RGB-пространстве
					r = clamp((int) ((r - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));
					g = clamp((int) ((g - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));
					b = clamp((int) ((b - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));

				// Насыщенность и оттенок в HSB-пространстве
				if (adj.saturation() != 0 || adj.hue() != 0) {
					float[] hsb = Color.RGBtoHSB(r, g, b, null);

					hsb[0] = (hsb[0] + hueShift + 1.0f) % 1.0f;
					hsb[1] = Math.min(1.0f, Math.max(0.0f, hsb[1] * saturationFactor));

					int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
					r = (rgb >> 16) & 0xFF;
					g = (rgb >> 8) & 0xFF;
					b = rgb & 0xFF;
				}

				result.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
			}
		}

		return result;
	}

	private int[] buildGammaLut(double gamma) {
		int[] lut = new int[256];

		for (int i = 0; i < 256; i++) {
			lut[i] = (int) Math.round(Math.pow(i / 255.0, 1.0 / gamma) * 255.0);
		}

		return lut;
	}

	private float buildContrastFactor(int contrast) {
		// Формула S-кривой: factor = (259 * (contrast + 255)) / (255 * (259 - contrast))
		// Нормализуем contrast из [-100, 100] в [-255, 255]
		float c = contrast * 2.55f;
		return (259.0f * (c + 255.0f)) / (255.0f * (259.0f - c));
	}

	private int clamp(int value) {
		return Math.min(255, Math.max(0, value));
	}
}
