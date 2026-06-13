package org.duollectis.mapart.tools.gui.keybind;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import lombok.Getter;

/**
 * Перечисление всех действий, которым можно назначить горячую клавишу.
 * Каждое действие хранит дефолтный бинд, lang-ключ и флаг блокировки
 * при фокусе в текстовом поле.
 */
@Getter
public enum KeyBindAction {

	CONVERT(
		"keybind.action.convert",
		new KeyBind(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
		false
	),
	OPEN_IMAGE(
		"keybind.action.open_image",
		new KeyBind(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
		true
	),
	PASTE_IMAGE(
		"keybind.action.paste_image",
		new KeyBind(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
		true
	),
	EXPORT(
		"keybind.action.export",
		new KeyBind(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK),
		true
	),
	IMPORT(
		"keybind.action.import",
		new KeyBind(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK),
		true
	),
	SAVE_PREVIEW(
		"keybind.action.save_preview",
		new KeyBind(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
		true
	),
	MERGE_LAYERS(
		"keybind.action.merge_layers",
		new KeyBind(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK),
		true
	),
	DELETE_LAYER(
		"keybind.action.delete_layer",
		new KeyBind(KeyEvent.VK_DELETE, 0),
		true
	);

	private final String langKey;
	private final KeyBind defaultBind;
	private final boolean blockedInTextField;

	KeyBindAction(String langKey, KeyBind defaultBind, boolean blockedInTextField) {
		this.langKey = langKey;
		this.defaultBind = defaultBind;
		this.blockedInTextField = blockedInTextField;
	}
}
