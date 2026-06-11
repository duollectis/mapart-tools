package org.duollectis.mapart.tools.gui.util;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Векторные SVG-иконки из ресурсов {@code /icons/*.svg}.
 * Перекрашиваются под цвет темы через замену {@code currentColor} перед рендерингом.
 * Кэшируются по паре (имя, цвет, размер) для исключения повторного рендеринга.
 *
 * <p>Использование:
 * <pre>
 *     btn.setIcon(AppIcon.RESET.colored(theme.getText()));
 *     btn.setIcon(AppIcon.RESET.colored(20, theme.getText()));
 * </pre>
 */
public enum AppIcon {

	RESET("reset"),
	BROWSE("browse"),
	OPEN_WINDOW("open_window"),
	PREV("prev"),
	NEXT("next"),
	AUTO_FIT("auto_fit"),
	HALVE("halve"),
	DOUBLE("double"),
	COLOR_PICKER("color_picker"),
	PLAY("play"),
	STOP("stop");

	private static final int DEFAULT_SIZE = 16;
	private static final ConcurrentMap<Long, Icon> CACHE = new ConcurrentHashMap<>();

	private final String resourceName;

	AppIcon(String resourceName) {
		this.resourceName = resourceName;
	}

	/**
	 * Создаёт иконку 16×16 с заданным цветом.
	 *
	 * @param color цвет иконки из текущей темы
	 */
	public Icon colored(Color color) {
		return colored(DEFAULT_SIZE, color);
	}

	/**
	 * Создаёт иконку заданного размера с заданным цветом.
	 *
	 * @param size  размер в пикселях (квадрат)
	 * @param color цвет иконки из текущей темы
	 */
	public Icon colored(int size, Color color) {
		long key = cacheKey(size, color.getRGB());
		return CACHE.computeIfAbsent(key, k -> render(size, color));
	}

	private long cacheKey(int size, int rgb) {
		return ((long) ordinal() << 48) | ((long) size << 32) | (rgb & 0xFFFFFFFFL);
	}

	private Icon render(int size, Color color) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		try {
			String svgText = loadSvgText(resourceName);
			String colorHex = toHex(color);
			String colored = svgText.replace("currentColor", colorHex);

			SVGUniverse universe = new SVGUniverse();
			URI uri = universe.loadSVG(new StringReader(colored), resourceName + "_" + colorHex + "_" + size);
			SVGDiagram diagram = universe.getDiagram(uri);

			if (diagram != null) {
				diagram.setIgnoringClipHeuristic(true);
				float svgW = diagram.getWidth();
				float svgH = diagram.getHeight();
				g2.scale((double) size / svgW, (double) size / svgH);
				diagram.render(g2);
			}
		} catch (Exception ignored) {
		} finally {
			g2.dispose();
		}

		return new ImageIcon(image);
	}

	private static String loadSvgText(String name) throws Exception {
		String path = "/icons/" + name + ".svg";
		try (InputStream stream = AppIcon.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IllegalStateException("SVG-ресурс не найден: " + path);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String toHex(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private record ImageIcon(BufferedImage image) implements Icon {

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			g.drawImage(image, x, y, null);
		}

		@Override
		public int getIconWidth() {
			return image.getWidth();
		}

		@Override
		public int getIconHeight() {
			return image.getHeight();
		}
	}
}
