package org.duollectis.mapart.tools.gui.keybind;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.app.AppPreferences;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Центральный реестр горячих клавиш приложения.
 * <p>
 * Хранит текущие бинды для каждого {@link KeyBindAction}, загружает их из
 * {@link AppPreferences} при старте и сохраняет при изменении.
 * Устанавливает глобальный {@link KeyEventDispatcher} на {@link KeyboardFocusManager},
 * который диспетчеризует нажатия в зарегистрированные обработчики.
 */
@UtilityClass
public class KeyBindManager {

	private static final EnumMap<KeyBindAction, KeyBind> binds = new EnumMap<>(KeyBindAction.class);
	private static final EnumMap<KeyBindAction, Runnable> handlers = new EnumMap<>(KeyBindAction.class);

	static {
		for (KeyBindAction action : KeyBindAction.values()) {
			binds.put(action, action.getDefaultBind());
		}
	}

	/** Загружает сохранённые бинды из {@link AppPreferences}. */
	public static void load() {
		Map<String, String> saved = AppPreferences.loadKeyBinds();

		for (KeyBindAction action : KeyBindAction.values()) {
			String raw = saved.get(action.name());

			if (raw != null) {
				binds.put(action, KeyBind.deserialize(raw));
			}
		}
	}

	/** Сохраняет текущие бинды в {@link AppPreferences}. */
	public static void save() {
		Map<String, String> map = new HashMap<>();

		binds.forEach((action, bind) -> map.put(action.name(), bind.serialize()));

		AppPreferences.saveKeyBinds(map);
	}

	public static KeyBind getBind(KeyBindAction action) {
		return binds.getOrDefault(action, action.getDefaultBind());
	}

	public static void setBind(KeyBindAction action, KeyBind bind) {
		binds.put(action, bind);
		save();
	}

	public static void resetToDefault(KeyBindAction action) {
		binds.put(action, action.getDefaultBind());
		save();
	}

	public static void resetAllToDefault() {
		for (KeyBindAction action : KeyBindAction.values()) {
			binds.put(action, action.getDefaultBind());
		}

		save();
	}

	/**
	 * Устанавливает глобальный диспетчер клавиш на {@link KeyboardFocusManager}.
	 * Вызывается один раз при построении главного окна.
	 *
	 * @param actionHandlers маппинг действие → обработчик
	 */
	public static void install(Map<KeyBindAction, Runnable> actionHandlers) {
		handlers.clear();
		handlers.putAll(actionHandlers);

		KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addKeyEventDispatcher(KeyBindManager::dispatch);
	}

	private static boolean dispatch(KeyEvent event) {
		if (event.getID() != KeyEvent.KEY_PRESSED) {
			return false;
		}

		boolean focusInTextField = event.getComponent() instanceof JTextField;

		for (KeyBindAction action : KeyBindAction.values()) {
			KeyBind bind = binds.getOrDefault(action, action.getDefaultBind());

			if (bind.isNone() || !bind.matches(event)) {
				continue;
			}

			if (focusInTextField && action.isBlockedInTextField()) {
					return false;
				}

			Runnable handler = handlers.get(action);

			if (handler != null) {
				handler.run();
				return true;
			}
		}

		return false;
	}

}
