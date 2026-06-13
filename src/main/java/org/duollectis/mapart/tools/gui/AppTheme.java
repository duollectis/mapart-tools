package org.duollectis.mapart.tools.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

	private static final Path EXTERNAL_THEMES_DIR = resolveExternalThemesSaveDir();

	private static final Gson GSON = new GsonBuilder()
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.registerTypeAdapter(Color.class, new ColorTypeAdapter())
		.setPrettyPrinting()
		.create();

	private String name;

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
	private Color dropdownBg;
	private Color dropdownItemBg;
	private Color previewPlaceholderBg;
	private Color progressBarFg;
	private Color contrastLight;
	private Color contrastDark;

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
	 * Мутирует {@code result} интерполированными значениями между {@code from} и {@code to}.
	 * Переиспользует существующие {@link Color} объекты через ARGB-кэш — ноль аллокаций на кадр
	 * при неизменном значении цвета, одна аллокация при реальном изменении.
	 * Используется для плавной анимации смены цветов без давления на GC.
	 *
	 * @param result объект для мутации (переиспользуется между кадрами)
	 * @param from   начальная тема (t=0)
	 * @param to     конечная тема (t=1)
	 * @param t      коэффициент интерполяции [0.0, 1.0]
	 */
	public static void blendInto(AppTheme result, AppTheme from, AppTheme to, float t) {
		result.bgDeep = lerpCached(result.bgDeep, from.bgDeep, to.bgDeep, t);
		result.bgCard = lerpCached(result.bgCard, from.bgCard, to.bgCard, t);
		result.bgInput = lerpCached(result.bgInput, from.bgInput, to.bgInput, t);
		result.border = lerpCached(result.border, from.border, to.border, t);
		result.accent = lerpCached(result.accent, from.accent, to.accent, t);
		result.accentBright = lerpCached(result.accentBright, from.accentBright, to.accentBright, t);
		result.text = lerpCached(result.text, from.text, to.text, t);
		result.textDim = lerpCached(result.textDim, from.textDim, to.textDim, t);
		result.success = lerpCached(result.success, from.success, to.success, t);
		result.error = lerpCached(result.error, from.error, to.error, t);
		result.warn = lerpCached(result.warn, from.warn, to.warn, t);
		result.selectionBg = lerpCached(result.selectionBg, from.selectionBg, to.selectionBg, t);
		result.scrollbarThumb = lerpCached(result.scrollbarThumb, from.scrollbarThumb, to.scrollbarThumb, t);
		result.scrollbarThumbHover = lerpCached(result.scrollbarThumbHover, from.scrollbarThumbHover, to.scrollbarThumbHover, t);
		result.scrollbarTrack = lerpCached(result.scrollbarTrack, from.scrollbarTrack, to.scrollbarTrack, t);
		result.sliderTrackBg = lerpCached(result.sliderTrackBg, from.sliderTrackBg, to.sliderTrackBg, t);
		result.sliderTrackFill = lerpCached(result.sliderTrackFill, from.sliderTrackFill, to.sliderTrackFill, t);
		result.sliderThumb = lerpCached(result.sliderThumb, from.sliderThumb, to.sliderThumb, t);
		result.sliderThumbHover = lerpCached(result.sliderThumbHover, from.sliderThumbHover, to.sliderThumbHover, t);
		result.textOnAccent = lerpCached(result.textOnAccent, from.textOnAccent, to.textOnAccent, t);
		result.tooltipBg = lerpCached(result.tooltipBg, from.tooltipBg, to.tooltipBg, t);
		result.nimbusBase = lerpCached(result.nimbusBase, from.nimbusBase, to.nimbusBase, t);
		result.btnExportBg = lerpCached(result.btnExportBg, from.btnExportBg, to.btnExportBg, t);
		result.btnExportFg = lerpCached(result.btnExportFg, from.btnExportFg, to.btnExportFg, t);
		result.btnBlocksBg = lerpCached(result.btnBlocksBg, from.btnBlocksBg, to.btnBlocksBg, t);
		result.btnBlocksFg = lerpCached(result.btnBlocksFg, from.btnBlocksFg, to.btnBlocksFg, t);
		result.btnImportBg = lerpCached(result.btnImportBg, from.btnImportBg, to.btnImportBg, t);
		result.btnImportFg = lerpCached(result.btnImportFg, from.btnImportFg, to.btnImportFg, t);
		result.btnHoverBg = lerpCached(result.btnHoverBg, from.btnHoverBg, to.btnHoverBg, t);
		result.hoverBgOverlay = lerpCached(result.hoverBgOverlay, from.hoverBgOverlay, to.hoverBgOverlay, t);
		result.importingProgressFg = lerpCached(result.importingProgressFg, from.importingProgressFg, to.importingProgressFg, t);
		result.dropdownBg = lerpCached(result.dropdownBg, from.dropdownBg, to.dropdownBg, t);
		result.dropdownItemBg = lerpCached(result.dropdownItemBg, from.dropdownItemBg, to.dropdownItemBg, t);
		result.previewPlaceholderBg = lerpCached(result.previewPlaceholderBg, from.previewPlaceholderBg, to.previewPlaceholderBg, t);
		result.progressBarFg = lerpCached(result.progressBarFg, from.progressBarFg, to.progressBarFg, t);
		result.contrastLight = lerpCached(result.contrastLight, from.contrastLight, to.contrastLight, t);
		result.contrastDark = lerpCached(result.contrastDark, from.contrastDark, to.contrastDark, t);
	}

	/**
	 * Интерполирует цвет и переиспользует {@code current} если ARGB-значение не изменилось.
	 * Это устраняет аллокации {@link Color} объектов на кадрах, где цвет стабилен.
	 */
	private static Color lerpCached(Color current, Color a, Color b, float t) {
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
		int argb = (alpha << 24) | (r << 16) | (g << 8) | bl;

		if (current != null && current.getRGB() == argb) {
			return current;
		}

		return new Color(argb, true);
	}

	public static AppTheme load(String themeName) {
		loadError = null;

		return BuiltinTheme.isBuiltin(themeName)
			? loadBuiltin(BuiltinTheme.fromId(themeName))
			: loadExternal(themeName);
	}

	/**
	 * Преобразует произвольную строку в snake_case для использования как имя файла.
	 * Пробелы и дефисы заменяются на подчёркивания, все символы приводятся к нижнему регистру,
	 * символы кроме букв, цифр и подчёркиваний удаляются.
	 *
	 * @param displayName отображаемое имя темы
	 * @return строка в snake_case, пригодная для имени файла
	 */
	public static String toSnakeCase(String displayName) {
		return transliterate(displayName.trim())
			.toLowerCase()
			.replaceAll("[\\s\\-]+", "_")
			.replaceAll("[^a-z0-9_]", "");
	}

	private static String transliterate(String text) {
		String[] cyrillic = {
			"а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й",
			"к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф",
			"х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я",
			"А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й",
			"К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф",
			"Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я"
		};
		String[] latin = {
			"a", "b", "v", "g", "d", "e", "yo", "zh", "z", "i", "y",
			"k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f",
			"kh", "ts", "ch", "sh", "shch", "", "y", "", "e", "yu", "ya",
			"A", "B", "V", "G", "D", "E", "Yo", "Zh", "Z", "I", "Y",
			"K", "L", "M", "N", "O", "P", "R", "S", "T", "U", "F",
			"Kh", "Ts", "Ch", "Sh", "Shch", "", "Y", "", "E", "Yu", "Ya"
		};

		StringBuilder result = new StringBuilder(text.length());

		for (char ch : text.toCharArray()) {
			boolean replaced = false;

			for (int i = 0; i < cyrillic.length; i++) {
				if (cyrillic[i].charAt(0) == ch) {
					result.append(latin[i]);
					replaced = true;
					break;
				}
			}

			if (!replaced) {
				result.append(ch);
			}
		}

		return result.toString();
	}

	/**
	 * Сохраняет тему в файл {@code themes/<snakeCase(theme.name)>.json} рядом с jar-файлом.
	 * Имя файла вычисляется из {@link #getName()} через {@link #toSnakeCase(String)}.
	 * Папка {@code themes/} создаётся автоматически если не существует.
	 *
	 * @param theme тема для сохранения (должна иметь непустое поле {@code name})
	 * @return имя файла без расширения (snake_case)
	 * @throws IOException если не удалось записать файл
	 */
	public static String save(AppTheme theme) throws IOException {
		Files.createDirectories(EXTERNAL_THEMES_DIR);

		String fileName = toSnakeCase(theme.getName());
		Path file = EXTERNAL_THEMES_DIR.resolve(fileName + ".json");
		Files.writeString(file, GSON.toJson(theme), StandardCharsets.UTF_8);
		return fileName;
	}

	/**
	 * Формирует список элементов для SelectionPanel выбора темы:
	 * сначала встроенные темы с разделителем, затем пользовательские (если есть).
	 */
	public static List<Object> buildThemeMenuItems() {
		List<Object> items = new ArrayList<>();
		items.add(new SelectionPanel.Separator("app_settings.theme_group_builtin"));

		for (BuiltinTheme bt : BuiltinTheme.values()) {
			items.add(bt);
		}

		List<String> custom = listCustomThemes();

		if (!custom.isEmpty()) {
			items.add(new SelectionPanel.Separator("app_settings.theme_group_custom"));

			for (String name : custom) {
				items.add(name);
			}
		}

		return items;
	}

	/**
	 * Возвращает список имён кастомных тем из всех известных папок themes/.
	 * Встроенные темы в список не включаются.
	 *
	 * @return список имён тем (без расширения .json)
	 */
	public static List<String> listCustomThemes() {
		return resolveAllThemesDirs()
			.stream()
			.filter(Files::exists)
			.flatMap(dir -> {
				try (var stream = Files.list(dir)) {
					return stream
						.filter(p -> p.toString().endsWith(".json"))
						.map(p -> p.getFileName().toString().replace(".json", ""))
						.filter(name -> !BuiltinTheme.isBuiltin(name))
						.toList()
						.stream();
				} catch (IOException e) {
					return java.util.stream.Stream.empty();
				}
			})
			.distinct()
			.sorted()
			.toList();
	}

	private static AppTheme loadBuiltin(BuiltinTheme builtin) {
		try (var reader = builtin.openReader()) {
			AppTheme theme = GSON.fromJson(reader, AppTheme.class);

			if (theme == null) {
				throw new IllegalStateException("Не удалось десериализовать встроенную тему: " + builtin.getId());
			}

			return theme;
		} catch (IOException e) {
			throw new IllegalStateException("Ошибка чтения встроенной темы: " + builtin.getId(), e);
		}
	}

	/**
	 * Директория для сохранения новых тем.
	 * При запуске из jar — папка рядом с ним. При запуске из IDE — {@code build/libs/themes/}.
	 */
	private static Path resolveExternalThemesSaveDir() {
		try {
			Path codePath = Path.of(
				AppTheme.class.getProtectionDomain().getCodeSource().getLocation().toURI()
			);

			if (Files.isRegularFile(codePath) && codePath.toString().endsWith(".jar")) {
				return codePath.getParent().resolve("themes");
			}
		} catch (URISyntaxException ignored) {
		}

		return Path.of(System.getProperty("user.dir"), "build", "libs", "themes");
	}

	/**
	 * Возвращает все кандидатные директории для поиска кастомных тем.
	 * При запуске из jar — только папка рядом с ним.
	 * При запуске из IDE — {@code build/libs/themes/} и {@code user.dir/themes/}.
	 */
	private static List<Path> resolveAllThemesDirs() {
		try {
			Path codePath = Path.of(
				AppTheme.class.getProtectionDomain().getCodeSource().getLocation().toURI()
			);

			if (Files.isRegularFile(codePath) && codePath.toString().endsWith(".jar")) {
				return List.of(codePath.getParent().resolve("themes"));
			}
		} catch (URISyntaxException ignored) {
		}

		Path userDir = Path.of(System.getProperty("user.dir"));

		return List.of(
			userDir.resolve(Path.of("build", "libs", "themes")),
			userDir.resolve("themes")
		);
	}

	private static AppTheme loadExternal(String themeName) {
		Path file = EXTERNAL_THEMES_DIR.resolve(themeName + ".json");

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			AppTheme theme = GSON.fromJson(reader, AppTheme.class);

			if (theme == null) {
				throw new IllegalStateException("Файл темы пуст или имеет неверный формат");
			}

			return theme;
		} catch (Exception e) {
			loadError = e.getMessage();
			return loadBuiltin(BuiltinTheme.DARK);
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
