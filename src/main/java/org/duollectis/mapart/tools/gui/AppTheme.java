package org.duollectis.mapart.tools.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Хранит все цвета активной темы интерфейса.
 * <p>
 * Встроенные темы ({@code dark}, {@code light}) загружаются из ресурсов {@code themes/<name>.json}.
 * Кастомные темы загружаются из внешней папки {@code themes/} рядом с jar-файлом.
 * При ошибке загрузки кастомной темы автоматически применяется встроенная тёмная тема,
 * а в {@link #loadError} сохраняется сообщение об ошибке для отображения пользователю.
 * <p>
 * Поля JSON именуются в snake_case, GSON маппит их на camelCase через
 * {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}.
 */
@Getter
public class AppTheme {

	private static final String BUILTIN_THEMES_PATH = "themes/";
	private static final String EXTERNAL_THEMES_DIR = "themes";

	private static final Gson GSON = new GsonBuilder()
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.registerTypeAdapter(Color.class, new ColorTypeAdapter())
		.setPrettyPrinting()
		.create();

	private static final java.util.Set<String> BUILTIN_THEME_NAMES = java.util.Set.of("dark", "light");

	private Color bgDeep;
	private Color bgCard;
	private Color bgInput;
	private Color border;
	private Color accent;
	private Color accentBright;
	private Color text;
	private Color textDim;
	private Color success;
	private Color error;
	private Color warn;
	private Color selectionBg;
	private Color scrollbarThumb;
	private Color scrollbarThumbHover;
	private Color scrollbarTrack;
	private Color sliderTrackBg;
	private Color sliderTrackFill;
	private Color sliderThumb;
	private Color sliderThumbHover;
	private Color textOnAccent;
	private Color tooltipBg;
	private Color nimbusBase;
	private Color btnExportBg;
	private Color btnExportFg;
	private Color btnBlocksBg;
	private Color btnBlocksFg;
	private Color btnImportBg;
	private Color btnImportFg;
	private Color btnHoverBg;
	private Color hoverBgOverlay;
	private Color importingProgressFg;

	/**
	 * Результат последней загрузки темы. Содержит сообщение об ошибке если кастомная тема
	 * не смогла загрузиться, иначе {@code null}.
	 */
	@Getter
	private static String loadError = null;

	/**
	 * Загружает тему по имени.
	 * Встроенные темы ({@code dark}, {@code light}) берутся из ресурсов приложения.
	 * Остальные имена считаются кастомными и ищутся в папке {@code themes/} рядом с jar.
	 * При ошибке загрузки кастомной темы возвращается встроенная тёмная тема,
	 * а {@link #loadError} заполняется сообщением об ошибке.
	 *
	 * @param themeName имя темы (например {@code "dark"}, {@code "light"} или {@code "my_theme"})
	 * @return загруженная тема
	 */
	/**
	 * Создаёт интерполированную тему между {@code from} и {@code to}.
	 * Используется для плавной анимации смены цветов без пересборки UI.
	 *
	 * @param from начальная тема (t=0)
	 * @param to   конечная тема (t=1)
	 * @param t    коэффициент интерполяции [0.0, 1.0]
	 * @return новый объект {@link AppTheme} с интерполированными цветами
	 */
	public static AppTheme blend(AppTheme from, AppTheme to, float t) {
		AppTheme result = new AppTheme();
		result.bgDeep = lerp(from.bgDeep, to.bgDeep, t);
		result.bgCard = lerp(from.bgCard, to.bgCard, t);
		result.bgInput = lerp(from.bgInput, to.bgInput, t);
		result.border = lerp(from.border, to.border, t);
		result.accent = lerp(from.accent, to.accent, t);
		result.accentBright = lerp(from.accentBright, to.accentBright, t);
		result.text = lerp(from.text, to.text, t);
		result.textDim = lerp(from.textDim, to.textDim, t);
		result.success = lerp(from.success, to.success, t);
		result.error = lerp(from.error, to.error, t);
		result.warn = lerp(from.warn, to.warn, t);
		result.selectionBg = lerp(from.selectionBg, to.selectionBg, t);
		result.scrollbarThumb = lerp(from.scrollbarThumb, to.scrollbarThumb, t);
		result.scrollbarThumbHover = lerp(from.scrollbarThumbHover, to.scrollbarThumbHover, t);
		result.scrollbarTrack = lerp(from.scrollbarTrack, to.scrollbarTrack, t);
		result.sliderTrackBg = lerp(from.sliderTrackBg, to.sliderTrackBg, t);
		result.sliderTrackFill = lerp(from.sliderTrackFill, to.sliderTrackFill, t);
		result.sliderThumb = lerp(from.sliderThumb, to.sliderThumb, t);
		result.sliderThumbHover = lerp(from.sliderThumbHover, to.sliderThumbHover, t);
		result.textOnAccent = lerp(from.textOnAccent, to.textOnAccent, t);
		result.tooltipBg = lerp(from.tooltipBg, to.tooltipBg, t);
		result.nimbusBase = lerp(from.nimbusBase, to.nimbusBase, t);
		result.btnExportBg = lerp(from.btnExportBg, to.btnExportBg, t);
		result.btnExportFg = lerp(from.btnExportFg, to.btnExportFg, t);
		result.btnBlocksBg = lerp(from.btnBlocksBg, to.btnBlocksBg, t);
		result.btnBlocksFg = lerp(from.btnBlocksFg, to.btnBlocksFg, t);
		result.btnImportBg = lerp(from.btnImportBg, to.btnImportBg, t);
		result.btnImportFg = lerp(from.btnImportFg, to.btnImportFg, t);
		result.btnHoverBg = lerp(from.btnHoverBg, to.btnHoverBg, t);
		result.hoverBgOverlay = lerp(from.hoverBgOverlay, to.hoverBgOverlay, t);
		result.importingProgressFg = lerp(from.importingProgressFg, to.importingProgressFg, t);
		return result;
	}

	private static Color lerp(Color a, Color b, float t) {
		if (a == null) {
			return b;
		}

		if (b == null) {
			return a;
		}

		int r = Math.clamp((int) (a.getRed() + (b.getRed() - a.getRed()) * t), 0, 255);
		int g = Math.clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t), 0, 255);
		int bl = Math.clamp((int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), 0, 255);
		int alpha = Math.clamp((int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t), 0, 255);
		return new Color(r, g, bl, alpha);
	}

	public static AppTheme load(String themeName) {
		loadError = null;

		return BUILTIN_THEME_NAMES.contains(themeName)
			? loadBuiltin(themeName)
			: loadExternal(themeName);
	}

	/**
	 * Сохраняет тему в файл {@code themes/<name>.json} рядом с jar-файлом.
	 * Папка {@code themes/} создаётся автоматически если не существует.
	 *
	 * @param theme тема для сохранения
	 * @param themeName имя файла (без расширения)
	 * @throws IOException если не удалось записать файл
	 */
	public static void save(AppTheme theme, String themeName) throws IOException {
		Path dir = Path.of(EXTERNAL_THEMES_DIR);
		Files.createDirectories(dir);

		Path file = dir.resolve(themeName + ".json");
		Files.writeString(file, GSON.toJson(theme), StandardCharsets.UTF_8);
	}

	/**
	 * Возвращает список имён кастомных тем из папки {@code themes/} рядом с jar.
	 * Встроенные темы в список не включаются.
	 *
	 * @return список имён тем (без расширения .json)
	 */
	public static java.util.List<String> listCustomThemes() {
		Path dir = Path.of(EXTERNAL_THEMES_DIR);

		if (Files.notExists(dir)) {
			return java.util.List.of();
		}

		try (var stream = Files.list(dir)) {
			return stream
				.filter(p -> p.toString().endsWith(".json"))
				.map(p -> p.getFileName().toString().replace(".json", ""))
				.filter(name -> !BUILTIN_THEME_NAMES.contains(name))
				.sorted()
				.toList();
		} catch (IOException e) {
			return java.util.List.of();
		}
	}

	private static AppTheme loadBuiltin(String themeName) {
		String resourcePath = BUILTIN_THEMES_PATH + themeName + ".json";

		try (InputStream stream = AppTheme.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (stream == null) {
				throw new IllegalStateException("Встроенная тема не найдена в ресурсах: " + resourcePath);
			}

			AppTheme theme = GSON.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8),
				AppTheme.class
			);

			if (theme == null) {
				throw new IllegalStateException("Не удалось десериализовать встроенную тему: " + themeName);
			}

			return theme;
		} catch (IOException e) {
			throw new IllegalStateException("Ошибка чтения встроенной темы: " + themeName, e);
		}
	}

	private static AppTheme loadExternal(String themeName) {
		Path file = Path.of(EXTERNAL_THEMES_DIR, themeName + ".json");

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			AppTheme theme = GSON.fromJson(reader, AppTheme.class);

			if (theme == null) {
				throw new IllegalStateException("Файл темы пуст или имеет неверный формат");
			}

			return theme;
		} catch (Exception e) {
			loadError = e.getMessage();
			return loadBuiltin("dark");
		}
	}

	private static final class ColorTypeAdapter extends TypeAdapter<Color> {

		@Override
		public void write(JsonWriter out, Color color) throws IOException {
			if (color == null) {
				out.nullValue();
				return;
			}

			if (color.getAlpha() == 255) {
				out.value("#%02X%02X%02X".formatted(
					color.getRed(),
					color.getGreen(),
					color.getBlue()
				));
			} else {
				out.value("#%02X%02X%02X%02X".formatted(
					color.getRed(),
					color.getGreen(),
					color.getBlue(),
					color.getAlpha()
				));
			}
		}

		@Override
		public Color read(JsonReader in) throws IOException {
			String value = in.nextString();

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
}
