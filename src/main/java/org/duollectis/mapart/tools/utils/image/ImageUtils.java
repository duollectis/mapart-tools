package org.duollectis.mapart.tools.utils.image;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.converter.CropSettings;

import java.awt.*;
import java.awt.image.BufferedImage;

@UtilityClass
public class ImageUtils {

	/**
	 * Подготавливает изображение к дизерингу: вписывает исходник в целевой размер карт
	 * с учётом пользовательского зума и смещения из {@link CropSettings}.
	 * <p>
	 * Возвращает {@link FitResult} с холстом и clip rect реальной области изображения.
	 * Clip rect передаётся в C++ дизерер для подавления артефактов Floyd-Steinberg
	 * на границе чёрного фона и реального изображения.
	 */
	public FitResult prepareImage(BufferedImage source, int targetWidth, int targetHeight, CropSettings crop) {
		return fitImage(
			source,
			targetWidth,
			targetHeight,
			crop.offsetX(),
			crop.offsetY(),
			crop.scaleX(),
			crop.scaleY(),
			crop.bgColor()
		);
	}

	/**
	 * Вписывает изображение в холст с независимым масштабом по X и Y.
	 * Базовый fit-scale по каждой оси умножается на соответствующий userScale.
	 * offsetX/offsetY сдвигают картинку внутри холста в пикселях целевого размера.
	 * <p>
	 * Возвращает {@link FitResult}: холст targetWidth×targetHeight с нарисованным
	 * изображением и clip rect видимой области. Clip rect используется дизерером
	 * чтобы пиксели чёрного фона не участвовали в диффузии ошибки.
	 */
	public FitResult fitImage(
		BufferedImage source,
		int targetWidth,
		int targetHeight,
		int offsetX,
		int offsetY,
		double userScaleX,
		double userScaleY
	) {
		return fitImage(source, targetWidth, targetHeight, offsetX, offsetY, userScaleX, userScaleY, Color.BLACK);
	}

	public FitResult fitImage(
		BufferedImage source,
		int targetWidth,
		int targetHeight,
		int offsetX,
		int offsetY,
		double userScaleX,
		double userScaleY,
		Color bgColor
	) {
		double baseScaleX = (double) targetWidth / source.getWidth();
		double baseScaleY = (double) targetHeight / source.getHeight();
		double baseScale = Math.min(baseScaleX, baseScaleY);

		int scaledW = Math.max(1, (int) Math.round(source.getWidth() * baseScale * userScaleX));
		int scaledH = Math.max(1, (int) Math.round(source.getHeight() * baseScale * userScaleY));

		int drawX = (targetWidth - scaledW) / 2 + offsetX;
		int drawY = (targetHeight - scaledH) / 2 + offsetY;

		int visX1 = Math.max(drawX, 0);
		int visY1 = Math.max(drawY, 0);
		int visX2 = Math.min(drawX + scaledW, targetWidth);
		int visY2 = Math.min(drawY + scaledH, targetHeight);

		BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2 = result.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.setColor(bgColor);
		g2.fillRect(0, 0, targetWidth, targetHeight);

		if (visX1 >= visX2 || visY1 >= visY2) {
			g2.dispose();
			return new FitResult(result, 0, 0, 0, 0);
		}

		double srcScaleX = (double) source.getWidth() / scaledW;
		double srcScaleY = (double) source.getHeight() / scaledH;
		int sx1 = (int) Math.round((visX1 - drawX) * srcScaleX);
		int sy1 = (int) Math.round((visY1 - drawY) * srcScaleY);
		int sx2 = Math.min((int) Math.round((visX2 - drawX) * srcScaleX), source.getWidth());
		int sy2 = Math.min((int) Math.round((visY2 - drawY) * srcScaleY), source.getHeight());

		g2.drawImage(source, visX1, visY1, visX2, visY2, sx1, sy1, sx2, sy2, null);
		g2.dispose();

		return new FitResult(result, visX1, visY1, visX2 - visX1, visY2 - visY1);
	}

	/**
	 * Масштабирует изображение до указанных размеров с использованием бикубической интерполяции.
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

				r = gammaLut[r];
				g = gammaLut[g];
				b = gammaLut[b];

				r = clamp((int) ((r - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));
				g = clamp((int) ((g - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));
				b = clamp((int) ((b - 127.5f) * contrastFactor + 127.5f + brightnessShift * 255));

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
		return Math.clamp(value, 0, 255);
	}
}
