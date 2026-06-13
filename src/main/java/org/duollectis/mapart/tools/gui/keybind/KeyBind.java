package org.duollectis.mapart.tools.gui.keybind;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Иммутабельный дескриптор горячей клавиши.
 * Хранит код клавиши и маску модификаторов (Ctrl/Shift/Alt).
 * Сериализуется в строку вида {@code "CTRL+SHIFT+V"} для хранения в JSON.
 */
public record KeyBind(int keyCode, int modifiers) {

	public static final KeyBind NONE = new KeyBind(KeyEvent.VK_UNDEFINED, 0);

	public boolean isNone() {
		return keyCode == KeyEvent.VK_UNDEFINED;
	}

	public boolean matches(KeyEvent event) {
		return event.getKeyCode() == keyCode
			&& (event.getModifiersEx() & RELEVANT_MODIFIERS) == (modifiers & RELEVANT_MODIFIERS);
	}

	/**
	 * Сериализует бинд в строку вида {@code "CTRL+V"} или {@code "NONE"}.
	 * Используется для хранения в {@code settings.json}.
	 */
	public String serialize() {
		if (isNone()) {
			return "NONE";
		}

		StringBuilder sb = new StringBuilder();

		if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
			sb.append("CTRL+");
		}

		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
			sb.append("SHIFT+");
		}

		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
			sb.append("ALT+");
		}

		sb.append(KeyEvent.getKeyText(keyCode).toUpperCase());

		return sb.toString();
	}

	/**
	 * Десериализует строку вида {@code "CTRL+V"} обратно в {@link KeyBind}.
	 * При ошибке парсинга возвращает {@link #NONE}.
	 */
	public static KeyBind deserialize(String raw) {
		if (raw == null || raw.isBlank() || "NONE".equalsIgnoreCase(raw)) {
			return NONE;
		}

		String[] parts = raw.toUpperCase().split("\\+");
		int mods = 0;
		String keyName = parts[parts.length - 1];

		for (int i = 0; i < parts.length - 1; i++) {
			mods |= switch (parts[i]) {
				case "CTRL" -> InputEvent.CTRL_DOWN_MASK;
				case "SHIFT" -> InputEvent.SHIFT_DOWN_MASK;
				case "ALT" -> InputEvent.ALT_DOWN_MASK;
				default -> 0;
			};
		}

		int code = resolveKeyCode(keyName);

		return code == KeyEvent.VK_UNDEFINED ? NONE : new KeyBind(code, mods);
	}

	/** Человекочитаемое представление для отображения в UI. */
	public String displayText() {
		return isNone() ? "—" : serialize();
	}

	private static final int RELEVANT_MODIFIERS =
		InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

	private static int resolveKeyCode(String name) {
		return switch (name) {
			case "ENTER" -> KeyEvent.VK_ENTER;
			case "ESCAPE", "ESC" -> KeyEvent.VK_ESCAPE;
			case "SPACE" -> KeyEvent.VK_SPACE;
			case "TAB" -> KeyEvent.VK_TAB;
			case "BACKSPACE" -> KeyEvent.VK_BACK_SPACE;
			case "DELETE", "DEL" -> KeyEvent.VK_DELETE;
			case "F1" -> KeyEvent.VK_F1;
			case "F2" -> KeyEvent.VK_F2;
			case "F3" -> KeyEvent.VK_F3;
			case "F4" -> KeyEvent.VK_F4;
			case "F5" -> KeyEvent.VK_F5;
			case "F6" -> KeyEvent.VK_F6;
			case "F7" -> KeyEvent.VK_F7;
			case "F8" -> KeyEvent.VK_F8;
			case "F9" -> KeyEvent.VK_F9;
			case "F10" -> KeyEvent.VK_F10;
			case "F11" -> KeyEvent.VK_F11;
			case "F12" -> KeyEvent.VK_F12;
			default -> {
				if (name.length() == 1) {
					yield KeyEvent.getExtendedKeyCodeForChar(name.charAt(0));
				}
				yield KeyEvent.VK_UNDEFINED;
			}
		};
	}
}
