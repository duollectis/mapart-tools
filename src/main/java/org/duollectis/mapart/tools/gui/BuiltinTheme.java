package org.duollectis.mapart.tools.gui;

import lombok.Getter;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Встроенные темы интерфейса, поставляемые вместе с приложением.
 * Путь к файлу ресурса строится из идентификатора темы автоматически.
 * Отображаемое имя берётся из активных переводов через {@link UpdatableRegistry#translate(String)}.
 */
public enum BuiltinTheme {

	DARK("dark", "app_settings.theme_dark"),
	LIGHT("light", "app_settings.theme_light");

	@Getter
	private final String id;

	private final String langKey;

	BuiltinTheme(String id, String langKey) {
		this.id = id;
		this.langKey = langKey;
	}

	public String getDisplayName() {
		return UpdatableRegistry.translate(langKey);
	}

	/**
	 * Загружает содержимое JSON-файла темы через ClassLoader.
	 * Путь формируется как {@code themes/{id}.json}.
	 *
	 * @return поток для чтения JSON-файла темы
	 * @throws IllegalStateException если файл не найден в classpath
	 */
	public InputStreamReader openReader() {
		String resourcePath = "themes/" + id + ".json";
		InputStream stream = BuiltinTheme.class.getClassLoader().getResourceAsStream(resourcePath);

		if (stream == null) {
			throw new IllegalStateException("Файл встроенной темы не найден в ресурсах: " + resourcePath);
		}

		return new InputStreamReader(stream, StandardCharsets.UTF_8);
	}

	/**
	 * Проверяет, является ли переданный идентификатор встроенной темой.
	 *
	 * @param id идентификатор темы
	 * @return {@code true} если тема встроенная
	 */
	public static boolean isBuiltin(String id) {
		for (BuiltinTheme theme : values()) {
			if (theme.id.equals(id)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Находит встроенную тему по идентификатору.
	 *
	 * @param id идентификатор темы
	 * @return соответствующий элемент enum
	 * @throws IllegalArgumentException если тема с таким идентификатором не найдена
	 */
	public static BuiltinTheme fromId(String id) {
		for (BuiltinTheme theme : values()) {
			if (theme.id.equals(id)) {
				return theme;
			}
		}

		throw new IllegalArgumentException("Неизвестная встроенная тема: " + id);
	}
}
