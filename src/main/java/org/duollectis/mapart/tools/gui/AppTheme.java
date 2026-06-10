package org.duollectis.mapart.tools.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Иммутабельная запись, хранящая все цвета активной темы интерфейса.
 * Загружается из JSON-файла в ресурсах {@code themes/<name>.json}.
 * При ошибке загрузки автоматически возвращается тёмная тема по умолчанию.
 */
public record AppTheme(
	Color bgDeep,
	Color bgCard,
	Color bgInput,
	Color border,
	Color accent,
	Color accentBright,
	Color text,
	Color textDim,
	Color success,
	Color error,
	Color warn,
	Color selectionBg,
	Color scrollbarThumb,
	Color scrollbarThumbHover,
	Color scrollbarTrack
) {

	private static final String THEMES_PATH = "themes/";

	public static AppTheme load(String themeName) {
		String path = THEMES_PATH + themeName + ".json";

		try (InputStream stream = AppTheme.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return dark();
			}

			JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

			return new AppTheme(
				parseColor(json, "bgDeep"),
				parseColor(json, "bgCard"),
				parseColor(json, "bgInput"),
				parseColor(json, "border"),
				parseColor(json, "accent"),
				parseColor(json, "accentBright"),
				parseColor(json, "text"),
				parseColor(json, "textDim"),
				parseColor(json, "success"),
				parseColor(json, "error"),
				parseColor(json, "warn"),
				parseColorAlpha(json, "selectionBg"),
				parseColor(json, "scrollbarThumb"),
				parseColor(json, "scrollbarThumbHover"),
				parseColor(json, "scrollbarTrack")
			);
		}
		catch (Exception e) {
			return dark();
		}
	}

	public static AppTheme dark() {
		return new AppTheme(
			new Color(13, 13, 20),
			new Color(22, 24, 35),
			new Color(30, 32, 46),
			new Color(45, 48, 68),
			new Color(99, 179, 237),
			new Color(144, 205, 244),
			new Color(226, 232, 240),
			new Color(113, 128, 150),
			new Color(72, 199, 142),
			new Color(252, 129, 129),
			new Color(251, 191, 36),
			new Color(49, 130, 206, 180),
			new Color(55, 60, 85),
			new Color(80, 88, 120),
			new Color(18, 18, 28)
		);
	}

	private static Color parseColor(JsonObject json, String key) {
		String hex = json.get(key).getAsString();
		return Color.decode(hex);
	}

	private static Color parseColorAlpha(JsonObject json, String key) {
		String value = json.get(key).getAsString();

		if (value.length() == 9 && value.startsWith("#")) {
			int r = Integer.parseInt(value.substring(1, 3), 16);
			int g = Integer.parseInt(value.substring(3, 5), 16);
			int b = Integer.parseInt(value.substring(5, 7), 16);
			int a = Integer.parseInt(value.substring(7, 9), 16);
			return new Color(r, g, b, a);
		}

		return Color.decode(value);
	}
}
