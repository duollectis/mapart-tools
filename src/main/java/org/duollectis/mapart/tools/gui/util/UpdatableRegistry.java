package org.duollectis.mapart.tools.gui.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.AppLocale;
import org.duollectis.mapart.tools.gui.AppPreferences;

import javax.swing.JComponent;
import java.awt.Component;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Единый реестр обновляемых GUI-компонентов, хранилище активных переводов
 * и точка входа для загрузки локализации.
 *
 * <p>Реестр очищается при {@link #clearLang()} / {@link #clearTheme()},
 * которые вызываются при пересборке UI.
 */
@UtilityClass
public class UpdatableRegistry {

	private static JsonObject translations = new JsonObject();

	private static final Map<String, List<Consumer<String>>> langComponents = new HashMap<>();

	/** Флаг активной анимации темы — используется компонентами для упрощения отрисовки. */
	public static volatile boolean themeAnimating;

	/** Слушатели темы — вызываются только по завершении анимации (fireTheme). */
	private static final List<Runnable> themeListeners = new ArrayList<>();

	/** Пары (компонент, слушатель) — вызываются только по завершении анимации (fireTheme). */
	private static final List<ThemeEntry> themeEntries = new ArrayList<>();

	/**
	 * Слушатели покадровой анимации — вызываются на каждом кадре {@link #fireThemeAnimFrame()}.
	 * Используются компонентами с {@code setBackground()}, которые не могут анимироваться
	 * через {@code repaint()} — им нужно обновлять цвет на каждом кадре явно.
	 */
	private static final List<Runnable> animListeners = new ArrayList<>();

	/** Корневое окно для покадровой перерисовки всего UI во время анимации темы. */
	private static Component animWindow;

	private static final List<Runnable> langListeners = new ArrayList<>();

	static {
		load(AppLocale.fromCode(AppPreferences.loadLocale(AppLocale.RUSSIAN.getCode())));
	}

	// ── Загрузка локализации ───────────────────────────────────────────────────

	/**
	 * Загружает файл локализации и обновляет активные переводы.
	 *
	 * <p>Для {@link AppLocale#ENGLISH}: проверяет отсутствие дублирующихся ключей.
	 * При обнаружении дублей — бросает {@link IllegalStateException} (краш приложения).
	 *
	 * <p>Для остальных локалей: проверяет, что все зарегистрированные ключи
	 * присутствуют в файле локали. Пропущенные ключи → stderr + fallback на {@link AppLocale#ENGLISH}.
	 *
	 * @param locale локаль для загрузки
	 * @return реально загруженная локаль — может отличаться от запрошенной при фолбэке
	 */
	public static AppLocale load(AppLocale locale) {
		if (locale == AppLocale.ENGLISH) {
			translations = loadAndValidateDefault();
			return AppLocale.ENGLISH;
		}

		Set<String> registeredKeys = getLangKeys();
		JsonObject loaded = parseLocale(locale);

		if (registeredKeys.isEmpty()) {
			translations = loaded;
			return locale;
		}

		Set<String> missingKeys = findMissingKeys(loaded, registeredKeys);

		if (missingKeys.isEmpty()) {
			translations = loaded;
			return locale;
		}

		System.err.println(
			"[Lang] Локаль '" + locale.getCode() + "' не содержит ключей: " + missingKeys
				+ ". Используется дефолтная локаль '" + AppLocale.ENGLISH.getCode() + "'."
		);

		translations = parseLocale(AppLocale.ENGLISH);
		return AppLocale.ENGLISH;
	}

	/** Загружает локаль по строковому коду — удобный перегруженный вариант. */
	public static AppLocale load(String localeCode) {
		return load(AppLocale.fromCode(localeCode));
	}

	// ── Переводы ───────────────────────────────────────────────────────────────

	/**
	 * Возвращает переведённую строку по ключу.
	 * Если ключ не найден — возвращает сам ключ как fallback.
	 */
	public static String translate(String key) {
		return translations.has(key)
			? translations.get(key).getAsString()
			: key;
	}

	/**
	 * Возвращает переведённую строку с подстановкой аргументов.
	 * Плейсхолдеры {0}, {1}, ... заменяются на соответствующие аргументы.
	 */
	public static String translate(String key, Object... args) {
		String value = translate(key);

		for (int i = 0; i < args.length; i++) {
			value = value.replace("{" + i + "}", String.valueOf(args[i]));
		}

		return value;
	}

	// ── Регистрация и уведомление ──────────────────────────────────────────────

	public static void onThemeChanged(Runnable listener) {
		themeListeners.add(listener);
	}

	/**
	 * Регистрирует слушатель темы с привязкой к конкретному компоненту.
	 * На каждом кадре анимации цветового перехода вызывается {@code listener.run()},
	 * затем {@code component.repaint()} — только для зарегистрированных компонентов,
	 * без обхода всего дерева.
	 *
	 * @param component компонент, который нужно перерисовывать на каждом кадре
	 * @param listener  логика обновления (setBackground, setForeground и т.п.)
	 */
	public static void onThemeChanged(JComponent component, Runnable listener) {
		themeEntries.add(new ThemeEntry(component, listener));
	}

	public static void onLangChanged(Runnable listener) {
		langListeners.add(listener);
	}

	/**
	 * Регистрирует языковой апдейтер и немедленно применяет текущий перевод по {@code key}.
	 *
	 * @param key     ключ локализации
	 * @param updater функция обновления компонента
	 */
	public static void registerLang(String key, Consumer<String> updater) {
		updater.accept(translate(key));
		langComponents
			.computeIfAbsent(key, k -> new ArrayList<>())
			.add(updater);
	}

	/**
	 * Уведомляет всех языковых подписчиков о смене языка, затем рекурсивно
	 * вызывает {@code repaint()} на всём дереве компонентов начиная с {@code root}.
	 *
	 * @param root корневой компонент для обхода (обычно главное окно)
	 */
	public static void fireLang(Component root) {
		for (var entry : langComponents.entrySet()) {
			String translated = translate(entry.getKey());

			for (Consumer<String> updater : entry.getValue()) {
				updater.accept(translated);
			}
		}

		for (Runnable listener : langListeners) {
			listener.run();
		}

		repaintTree(root);
	}

	/**
	 * Регистрирует корневое окно для покадровой перерисовки всего UI во время анимации темы.
	 * Тяжёлые компоненты (ImagePreviewPanel) защищены флагом {@link #themeAnimating}
	 * и используют кеш контента вместо полного рендера.
	 *
	 * @param window корневое окно приложения
	 */
	public static void setAnimWindow(Component window) {
		animWindow = window;
	}

	/**
	 * Регистрирует слушатель, вызываемый на каждом кадре анимации темы.
	 * Используется компонентами с {@code setBackground()}/{@code setForeground()},
	 * которые не могут анимироваться через {@code repaint()} — им нужно обновлять
	 * цвет явно на каждом кадре.
	 *
	 * @param listener логика обновления цвета компонента
	 */
	public static void onThemeAnimFrame(Runnable listener) {
		animListeners.add(listener);
	}

	/**
	 * Вызывается на каждом кадре анимации цветового перехода.
	 * Сначала вызывает все покадровые слушатели (setBackground и т.п.),
	 * затем перерисовывает всё окно целиком.
	 * Флаг {@link #themeAnimating} позволяет тяжёлым компонентам использовать кеш.
	 */
	public static void fireThemeAnimFrame() {
		for (Runnable listener : animListeners) {
			listener.run();
		}

		if (animWindow != null) {
			animWindow.repaint();
		}
	}

	/** Устанавливает флаг начала анимации темы. */
	public static void beginThemeAnim() {
		themeAnimating = true;
	}

	/** Сбрасывает флаг анимации темы. */
	public static void endThemeAnim() {
		themeAnimating = false;
	}

	/**
	 * Уведомляет всех тематических подписчиков о смене темы и перерисовывает окно.
	 * Вызывается один раз по завершении анимации — применяет setBackground/setForeground.
	 *
	 * @param root корневой компонент для финальной перерисовки
	 */
	public static void fireTheme(Component root) {
		for (ThemeEntry entry : themeEntries) {
			entry.listener().run();
		}

		for (Runnable listener : themeListeners) {
			listener.run();
		}

		root.repaint();
	}

	/** Возвращает неизменяемое множество всех зарегистрированных ключей локализации. */
	public static Set<String> getLangKeys() {
		return Set.copyOf(langComponents.keySet());
	}

	public static void clearLang() {
		langComponents.clear();
	}

	public static void clearTheme() {
		themeListeners.clear();
		themeEntries.clear();
		animListeners.clear();
		animWindow = null;
		themeAnimating = false;
	}

	// ── Приватные методы загрузки ──────────────────────────────────────────────

	/**
	 * Загружает {@code en_us.json} с проверкой на дублирующиеся ключи.
	 * Дубли в дефолтной локали — ошибка разработчика, приводящая к краш-стопу.
	 *
	 * @throws IllegalStateException если в файле обнаружены дублирующиеся ключи
	 */
	private static JsonObject loadAndValidateDefault() {
		Set<String> seen = new HashSet<>();
		Set<String> duplicates = new TreeSet<>();

		try (InputStreamReader reader = AppLocale.ENGLISH.openReader()) {
			JsonReader jsonReader = new JsonReader(reader);
			JsonObject result = new JsonObject();

			jsonReader.beginObject();

			while (jsonReader.peek() != JsonToken.END_OBJECT) {
				String key = jsonReader.nextName();
				String value = jsonReader.nextString();

				if (!seen.add(key)) {
					duplicates.add(key);
				}

				result.addProperty(key, value);
			}

			jsonReader.endObject();

			if (duplicates.isEmpty()) {
				return result;
			}

			throw new IllegalStateException(
				"[Lang] Дефолтная локаль '" + AppLocale.ENGLISH.getCode()
					+ "' содержит дублирующиеся ключи: " + duplicates
			);
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			System.err.println("[Lang] Не удалось загрузить дефолтную локаль: " + e.getMessage());
			return new JsonObject();
		}
	}

	private static JsonObject parseLocale(AppLocale locale) {
		try (var reader = locale.openReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			System.err.println("[Lang] Не удалось загрузить локаль '" + locale.getCode() + "': " + e.getMessage());
			return new JsonObject();
		}
	}

	private static Set<String> findMissingKeys(JsonObject source, Set<String> requiredKeys) {
		Set<String> missing = new TreeSet<>();

		for (String key : requiredKeys) {
			if (!source.has(key)) {
				missing.add(key);
			}
		}

		return missing;
	}

	private static void repaintTree(Component component) {
		component.repaint();
	}

	private record ThemeEntry(JComponent component, Runnable listener) {}
}
