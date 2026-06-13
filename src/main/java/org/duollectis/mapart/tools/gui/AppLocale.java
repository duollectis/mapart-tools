package org.duollectis.mapart.tools.gui;

import lombok.Getter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Поддерживаемые языки интерфейса.
 * Путь к файлу ресурса строится из кода локали автоматически.
 */
@Getter
public enum AppLocale {

	RUSSIAN("ru_ru", "Русский"),
	ENGLISH("en_us", "English");

	private final String code;
	private final String displayName;

	AppLocale(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}

	/**
	 * Загружает содержимое JSON-файла локализации через ClassLoader.
	 * Путь формируется как {@code lang/{code}.json}.
	 *
	 * @return поток для чтения JSON-файла
	 * @throws IllegalStateException если файл не найден в classpath
	 */
	public InputStreamReader openReader() {
		String resourcePath = "lang/" + code + ".json";
		InputStream stream = AppLocale.class.getClassLoader().getResourceAsStream(resourcePath);

		if (stream == null) {
			throw new IllegalStateException("Файл локализации не найден в ресурсах: " + resourcePath);
		}

		return new InputStreamReader(stream, StandardCharsets.UTF_8);
	}

	/**
	 * Находит локаль по коду. При отсутствии совпадения возвращает {@link #RUSSIAN}.
	 *
	 * @param code код локали, например {@code "ru_ru"} или {@code "en_us"}
	 * @return соответствующий элемент enum или {@link #RUSSIAN} как fallback
	 */
	public static AppLocale fromCode(String code) {
		for (AppLocale locale : values()) {
			if (locale.code.equals(code)) {
				return locale;
			}
		}

		return RUSSIAN;
	}
}
