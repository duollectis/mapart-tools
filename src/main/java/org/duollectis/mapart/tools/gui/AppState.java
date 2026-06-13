package org.duollectis.mapart.tools.gui;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.util.ThemeTransition;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.JFrame;

/**
 * Единое состояние приложения: текущий язык и текущая тема.
 *
 * <p>Является единственной точкой входа для смены языка и темы.
 * Инициализируется при старте через {@link #init()}, который читает
 * сохранённые настройки из {@link AppPreferences}.
 */
@UtilityClass
public class AppState {

	private static AppLocale currentLocale;
	private static String currentThemeName;

	/**
	 * Инициализирует состояние из сохранённых настроек.
	 * Вызывается один раз при старте приложения до создания окна.
	 */
	public static void init() {
		currentLocale = AppLocale.fromCode(AppPreferences.loadLocale(AppLocale.RUSSIAN.getCode()));
		currentThemeName = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());

		UpdatableRegistry.load(currentLocale);
		GuiApp.applyTheme(currentThemeName);
	}

	public static AppLocale getLocale() {
		return currentLocale;
	}

	public static String getThemeName() {
		return currentThemeName;
	}

	/**
	 * Меняет язык интерфейса, сохраняет выбор и уведомляет все подписчики.
	 *
	 * @param locale  новая локаль
	 * @param window  корневое окно для обхода при {@code UpdatableRegistry.fireLang()}
	 */
	public static void applyLocale(AppLocale locale, JFrame window) {
		AppLocale actual = UpdatableRegistry.load(locale);
		currentLocale = actual;
		AppPreferences.saveLocale(actual.getCode());
		window.setTitle(UpdatableRegistry.translate("app.title"));
		UpdatableRegistry.fireLang(window);
	}

	/**
	 * Меняет тему интерфейса, сохраняет выбор и запускает анимацию перехода.
	 *
	 * @param themeName имя темы (встроенной или кастомной)
	 * @param window    корневое окно для анимации
	 */
	public static void applyTheme(String themeName, JFrame window) {
		currentThemeName = themeName;
		AppPreferences.saveTheme(themeName);
		ThemeTransition.applyColorOnly(window, themeName);
	}
}
