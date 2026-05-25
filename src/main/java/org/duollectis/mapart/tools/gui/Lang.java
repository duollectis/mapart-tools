package org.duollectis.mapart.tools.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Система локализации. Загружает строки из {@code lang/{locale}.json} в classpath.
 * Поддерживает подстановку аргументов через плейсхолдеры {0}, {1}, ...
 */
@UtilityClass
public class Lang {

	private static final String DEFAULT_LOCALE = "ru_ru";
	private static final String LANG_PATH_TEMPLATE = "lang/%s.json";

	private static JsonObject translations = new JsonObject();

	static {
		load(AppPreferences.loadLocale(DEFAULT_LOCALE));
	}

	/**
	 * Загружает файл локализации по коду языка.
	 * При отсутствии файла — тихий fallback на пустой словарь.
	 */
	public void load(String locale) {
		String path = LANG_PATH_TEMPLATE.formatted(locale);

		try (InputStream stream = Lang.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				translations = new JsonObject();
				return;
			}

			translations = JsonParser
				.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject();
		} catch (Exception ignored) {
			translations = new JsonObject();
		}
	}

	/**
	 * Возвращает переведённую строку по ключу.
	 * Если ключ не найден — возвращает сам ключ как fallback.
	 */
	public String t(String key) {
		return translations.has(key)
			? translations.get(key).getAsString()
			: key;
	}

	/**
	 * Возвращает переведённую строку с подстановкой аргументов.
	 * Плейсхолдеры {0}, {1}, ... заменяются на соответствующие аргументы.
	 */
	public String t(String key, Object... args) {
		String value = t(key);

		for (int i = 0; i < args.length; i++) {
			value = value.replace("{" + i + "}", String.valueOf(args[i]));
		}

		return value;
	}
}
