package org.duollectis.mapart.tools.gui.util;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * Рендерит текст с автоматическим выбором цвета каждого пикселя глифа
 * на основе яркости фона под ним.
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>Рендерим текст белым цветом в ARGB-буфер с полным AA.</li>
 *   <li>Строим Summed Area Table (SAT) яркостей фона — O(W×H).</li>
 *   <li>Для каждого непрозрачного пикселя глифа получаем среднюю яркость
 *       в окрестности {@code SAMPLE_RADIUS} пикселей за O(1) через SAT.</li>
 *   <li>По средней яркости выбираем тёмный или светлый цвет из темы.</li>
 *   <li>Рисуем итоговый буфер поверх целевого контекста.</li>
 * </ol>
 *
 * <p>Яркость вычисляется по стандарту WCAG 2.1 (relative luminance, sRGB linearization),
 * что корректно обрабатывает насыщенные цвета (красный, синий и т.д.),
 * в отличие от упрощённой формулы ITU-R BT.601.
 */
@UtilityClass
public class ContrastTextRenderer {

	/**
	 * Порог WCAG relative luminance для выбора тёмного/светлого текста.
	 * Значение 0.179 соответствует точке, где контраст с белым и чёрным одинаков (≈ 4.5:1).
	 */
	private static final double LUMINANCE_THRESHOLD = 0.179;

	/** Радиус окрестности (в пикселях) для усреднения яркости фона под глифом. */
	private static final int SAMPLE_RADIUS = 3;

	/**
	 * Рисует строку {@code text} по центру прямоугольника {@code [0, 0, width, height]}
	 * с автоматическим выбором цвета пикселей на основе {@code bgColor}.
	 *
	 * <p>Если фон неоднородный (например, прогресс-бар), используй
	 * {@link #drawCenteredOnBackground} для попиксельного рендеринга.
	 *
	 * @param target  целевой контекст рисования
	 * @param text    строка для отображения
	 * @param font    шрифт
	 * @param bgColor цвет фона под текстом
	 * @param width   ширина области рисования
	 * @param height  высота области рисования
	 */
	public static void drawCentered(
		Graphics2D target, String text, Font font, Color bgColor, int width, int height
	) {
		Color textColor = contrastFor(bgColor);
		BufferedImage glyphMask = renderGlyphMask(text, font, width, height);
		paintMask(target, glyphMask, textColor, width, height);
	}

	/**
	 * Рисует строку {@code text} по центру прямоугольника с попиксельным выбором цвета.
	 * Для каждого пикселя глифа усредняет яркость фона в окрестности {@code SAMPLE_RADIUS}
	 * пикселей через Summed Area Table (O(1) на пиксель) и выбирает тёмный или светлый
	 * цвет из темы — тот, что контрастнее.
	 *
	 * <p>Используется для прогресс-баров и любых компонентов с неоднородным фоном.
	 *
	 * @param target     целевой контекст рисования
	 * @param text       строка для отображения
	 * @param font       шрифт
	 * @param background снимок фона компонента (тот же размер, что width × height)
	 * @param width      ширина области рисования
	 * @param height     высота области рисования
	 */
	public static void drawCenteredOnBackground(
		Graphics2D target, String text, Font font, BufferedImage background, int width, int height
	) {
		BufferedImage glyphMask = renderGlyphMask(text, font, width, height);
		paintMaskOnBackground(target, glyphMask, background, width, height);
	}

	private static BufferedImage renderGlyphMask(String text, Font font, int width, int height) {
		BufferedImage buf = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buf.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setFont(font);
		g.setColor(Color.WHITE);

		FontMetrics fm = g.getFontMetrics();
		int tx = (width - fm.stringWidth(text)) / 2;
		int ty = (height - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(text, tx, ty);
		g.dispose();

		return buf;
	}

	/**
	 * Перекрашивает все непрозрачные пиксели маски глифа в {@code textColor}
	 * и рисует результат на целевом контексте.
	 */
	private static void paintMask(
		Graphics2D target, BufferedImage glyphMask, Color textColor, int width, int height
	) {
		int rgb = textColor.getRGB() & 0x00FFFFFF;
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = glyphMask.getRGB(x, y);
				int alpha = (pixel >> 24) & 0xFF;

				if (alpha == 0) {
					continue;
				}

				result.setRGB(x, y, (alpha << 24) | rgb);
			}
		}

		target.drawImage(result, 0, 0, null);
	}

	/**
	 * Для каждого непрозрачного пикселя глифа усредняет яркость фона в окрестности
	 * {@code SAMPLE_RADIUS} пикселей через Summed Area Table и выбирает тёмный или
	 * светлый цвет из темы. SAT строится один раз за O(W×H), каждый запрос — O(1).
	 */
	private static void paintMaskOnBackground(
		Graphics2D target, BufferedImage glyphMask, BufferedImage background, int width, int height
	) {
		Color dark = resolveContrastDark();
		Color light = resolveContrastLight();
		int darkRgb = dark.getRGB() & 0x00FFFFFF;
		int lightRgb = light.getRGB() & 0x00FFFFFF;

		double[] sat = buildLuminanceSat(background, width, height);
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = glyphMask.getRGB(x, y);
				int alpha = (pixel >> 24) & 0xFF;

				if (alpha == 0) {
					continue;
				}

				double avgLuminance = queryAreaLuminance(sat, x, y, width, height, SAMPLE_RADIUS);
				int textRgb = avgLuminance > LUMINANCE_THRESHOLD ? darkRgb : lightRgb;

				result.setRGB(x, y, (alpha << 24) | textRgb);
			}
		}

		target.drawImage(result, 0, 0, null);
	}

	/**
	 * Возвращает светлый или тёмный цвет из активной темы — тот, что контрастнее на данном фоне.
	 * Цвета берутся из {@code GuiApp.theme.getContrastLight()} / {@code getContrastDark()}.
	 * Если тема не инициализирована или поля не заданы — используются WHITE / BLACK как запасные.
	 * Использует WCAG 2.1 relative luminance для корректной обработки насыщенных цветов.
	 */
	public static Color contrastFor(Color bg) {
		Color light = resolveContrastLight();
		Color dark = resolveContrastDark();
		return relativeLuminance(bg.getRGB()) > LUMINANCE_THRESHOLD ? dark : light;
	}

	/**
	 * Плавно интерполирует контрастный цвет текста между двумя фонами.
	 * Используется для анимированных компонентов, где фон меняется от {@code bgFrom} к {@code bgTo}
	 * с прогрессом {@code t ∈ [0, 1]}.
	 *
	 * <p>Вычисляет контрастный цвет для каждого фона отдельно, затем интерполирует
	 * между ними — это даёт плавный переход текста вместо резкого бинарного скачка.
	 *
	 * @param bgFrom начальный цвет фона
	 * @param bgTo   конечный цвет фона
	 * @param t      прогресс анимации [0.0, 1.0]
	 * @return интерполированный контрастный цвет текста
	 */
	public static Color contrastLerp(Color bgFrom, Color bgTo, float t) {
		Color from = contrastFor(bgFrom);
		Color to = contrastFor(bgTo);
		return UiAnimator.lerp(from, to, t);
	}

	/**
	 * Возвращает воспринимаемую яркость цвета в диапазоне [0..255].
	 * Вычисляется через WCAG relative luminance для корректной обработки насыщенных цветов.
	 * Метод сохранён для обратной совместимости с кодом, ожидающим целочисленный результат.
	 *
	 * @param rgb цвет в формате 0xAARRGGBB или 0x00RRGGBB
	 * @return яркость в диапазоне [0, 255]
	 */
	public static int luminance(int rgb) {
		return (int) Math.round(relativeLuminance(rgb) * 255);
	}

	/**
	 * Вычисляет WCAG 2.1 relative luminance цвета.
	 * Линеаризует sRGB-компоненты и взвешивает по чувствительности глаза.
	 * Диапазон результата: [0.0, 1.0], где 0 — чёрный, 1 — белый.
	 *
	 * @param rgb цвет в формате 0xAARRGGBB или 0x00RRGGBB
	 * @return relative luminance в диапазоне [0.0, 1.0]
	 */
	public static double relativeLuminance(int rgb) {
		double r = linearize((rgb >> 16) & 0xFF);
		double g = linearize((rgb >> 8) & 0xFF);
		double b = linearize(rgb & 0xFF);
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	/**
	 * Строит Summed Area Table (SAT) WCAG relative luminance за один проход O(W×H).
	 * SAT[(y+1)*(W+1)+(x+1)] = сумма luminance всех пикселей в прямоугольнике [0,0]..[x,y].
	 */
	private static double[] buildLuminanceSat(BufferedImage img, int width, int height) {
		int stride = width + 1;
		double[] sat = new double[stride * (height + 1)];

		for (int y = 0; y < height; y++) {
			double rowSum = 0;

			for (int x = 0; x < width; x++) {
				rowSum += relativeLuminance(img.getRGB(x, y));
				sat[(y + 1) * stride + (x + 1)] = rowSum + sat[y * stride + (x + 1)];
			}
		}

		return sat;
	}

	/**
	 * Возвращает среднюю WCAG relative luminance в квадратной окрестности радиуса {@code radius}
	 * вокруг точки {@code (cx, cy)} за O(1) через Summed Area Table.
	 * Область автоматически обрезается по границам изображения.
	 */
	private static double queryAreaLuminance(
		double[] sat, int cx, int cy, int width, int height, int radius
	) {
		int x1 = Math.max(0, cx - radius);
		int y1 = Math.max(0, cy - radius);
		int x2 = Math.min(width - 1, cx + radius);
		int y2 = Math.min(height - 1, cy + radius);

		int stride = width + 1;
		double sum = sat[(y2 + 1) * stride + (x2 + 1)]
			- sat[y1 * stride + (x2 + 1)]
			- sat[(y2 + 1) * stride + x1]
			+ sat[y1 * stride + x1];

		int count = (x2 - x1 + 1) * (y2 - y1 + 1);
		return count == 0 ? 0 : sum / count;
	}

	/** Линеаризует один sRGB-канал [0..255] в линейное значение [0.0..1.0] по стандарту WCAG 2.1. */
	private static double linearize(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045
			? c / 12.92
			: Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static Color resolveContrastLight() {
		try {
			Color c = GuiApp.theme.getContrastLight();
			return c != null ? c : Color.WHITE;
		} catch (Exception e) {
			return Color.WHITE;
		}
	}

	private static Color resolveContrastDark() {
		try {
			Color c = GuiApp.theme.getContrastDark();
			return c != null ? c : Color.BLACK;
		} catch (Exception e) {
			return Color.BLACK;
		}
	}
}
