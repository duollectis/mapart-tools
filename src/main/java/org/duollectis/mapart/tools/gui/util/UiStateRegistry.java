package org.duollectis.mapart.tools.gui.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.StyledSlider;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Реестр состояния UI с автосохранением.
 * <p>
 * Принцип работы: каждый виджет регистрируется через {@code bind*} — метод вешает
 * listener на компонент и при любом изменении планирует запись в JSON через дебаунс
 * {@value DEBOUNCE_MS}мс. Восстановление — через {@code restore*}.
 * <p>
 * Хранит состояние в {@code ui_state.json} отдельно от {@code settings.json},
 * чтобы не смешивать бизнес-настройки с визуальным состоянием.
 */
@UtilityClass
public class UiStateRegistry {

	private static final Path STATE_FILE = Path.of("./ui_state.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int DEBOUNCE_MS = 300;

	private static Timer debounceTimer;
	private static JsonObject cache;

	// ── Bind-методы (регистрация + listener) ──────────────────────────────────

	public static void bindSlider(String key, StyledSlider slider) {
		slider.addChangeListener(e -> schedule(key, slider.getValue()));
	}

	public static void bindToggle(String key, AbstractButton toggle) {
		toggle.addActionListener(e -> schedule(key, toggle.isSelected()));
	}

	public static void bindAccordion(String key, AccordionPanel accordion) {
 		accordion.addAccordionListener(evt -> schedule(key, accordion.isExpanded()));
	}

	public static void bindColor(String key, Runnable colorReader, int[] colorHolder) {
		// Цвет не имеет стандартного listener — вызывается вручную через notifyColor
	}

	public static void notifyColor(String key, Color color) {
		schedule(key, color.getRGB() & 0x00FFFFFF);
	}

	public static void bindSpinner(String key, JSpinner spinner) {
		spinner.addChangeListener(e -> schedule(key, ((Number) spinner.getValue()).intValue()));
	}

	public static void bindTextField(String key, JTextField field) {
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				schedule(key, field.getText());
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				schedule(key, field.getText());
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				schedule(key, field.getText());
			}
		});
	}

	public static void bindWindow(String key, Window window) {
		window.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				saveWindowBounds(key, window);
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				saveWindowBounds(key, window);
			}
		});

		if (window instanceof JFrame frame) {
			frame.addWindowStateListener(e -> schedule(key + "_state", e.getNewState()));
		}
	}

	// ── Restore-методы (чтение и применение) ──────────────────────────────────

	public static void restoreSlider(String key, StyledSlider slider, int defaultValue) {
		JsonObject root = root();

		if (root.has(key)) {
			slider.setValue(root.get(key).getAsInt());
		} else {
			slider.setValue(defaultValue);
		}
	}

	public static void restoreToggle(String key, AbstractButton toggle, boolean defaultValue) {
		JsonObject root = root();
		toggle.setSelected(root.has(key) ? root.get(key).getAsBoolean() : defaultValue);
	}

	public static void restoreAccordion(String key, AccordionPanel accordion, boolean defaultExpanded) {
		JsonObject root = root();
		boolean expanded = root.has(key) ? root.get(key).getAsBoolean() : defaultExpanded;

		if (expanded) {
			accordion.expandInstant();
		} else {
			accordion.collapse();
		}
	}

	public static Color restoreColor(String key, Color defaultColor) {
		JsonObject root = root();

		if (root.has(key)) {
			int rgb = root.get(key).getAsInt();
			return new Color(rgb);
		}

		return defaultColor;
	}

	public static void restoreSpinner(String key, JSpinner spinner, int defaultValue) {
		JsonObject root = root();
		spinner.setValue(root.has(key) ? root.get(key).getAsInt() : defaultValue);
	}

	public static String restoreTextField(String key, String defaultValue) {
		JsonObject root = root();
		return root.has(key) ? root.get(key).getAsString() : defaultValue;
	}

	public static void restoreWindow(String key, Window window, boolean restoreMaximized) {
		JsonObject root = root();
		String xKey = key + "_x";
		String yKey = key + "_y";
		String wKey = key + "_w";
		String hKey = key + "_h";

		if (root.has(xKey) && root.has(yKey) && root.has(wKey) && root.has(hKey)) {
			int x = root.get(xKey).getAsInt();
			int y = root.get(yKey).getAsInt();
			int w = root.get(wKey).getAsInt();
			int h = root.get(hKey).getAsInt();

			Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

			boolean fitsOnScreen = w >= 100 && h >= 100
				&& x + w >= 0 && y + h >= 0
				&& x <= screen.width && y <= screen.height;

			if (fitsOnScreen) {
				window.setBounds(x, y, w, h);
			}
		}

		if (restoreMaximized && window instanceof JFrame frame && root.has(key + "_state")) {
			int state = root.get(key + "_state").getAsInt();

			if ((state & JFrame.MAXIMIZED_BOTH) != 0) {
				frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
			}
		}
	}

	/**
	 * @deprecated Используй {@link #restoreWindow(String, Window, boolean)}
	 */
	@Deprecated
	public static void restoreWindow(String key, Window window) {
		restoreWindow(key, window, false);
	}

	// ── Прямая запись/чтение произвольных значений ────────────────────────────

	public static void putInt(String key, int value) {
		schedule(key, value);
	}

	public static void putString(String key, String value) {
		schedule(key, value);
	}

	public static void putBoolean(String key, boolean value) {
		schedule(key, value);
	}

	public static int getInt(String key, int defaultValue) {
		JsonObject root = root();
		return root.has(key) ? root.get(key).getAsInt() : defaultValue;
	}

	public static String getString(String key, String defaultValue) {
		JsonObject root = root();
		return root.has(key) ? root.get(key).getAsString() : defaultValue;
	}

	public static boolean getBoolean(String key, boolean defaultValue) {
		JsonObject root = root();
		return root.has(key) ? root.get(key).getAsBoolean() : defaultValue;
	}

	// ── Внутренняя механика ───────────────────────────────────────────────────

	private static void schedule(String key, int value) {
		root().addProperty(key, value);
		scheduleFlush();
	}

	private static void schedule(String key, boolean value) {
		root().addProperty(key, value);
		scheduleFlush();
	}

	private static void schedule(String key, String value) {
		root().addProperty(key, value);
		scheduleFlush();
	}

	private static void saveWindowBounds(String key, Window window) {
		if (window instanceof JFrame frame && (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			return;
		}

		Rectangle b = window.getBounds();
		JsonObject root = root();
		root.addProperty(key + "_x", b.x);
		root.addProperty(key + "_y", b.y);
		root.addProperty(key + "_w", b.width);
		root.addProperty(key + "_h", b.height);
		scheduleFlush();
	}

	private static void scheduleFlush() {
		if (debounceTimer != null && debounceTimer.isRunning()) {
			debounceTimer.restart();
			return;
		}

		debounceTimer = new Timer(DEBOUNCE_MS, e -> flush());
		debounceTimer.setRepeats(false);
		debounceTimer.start();
	}

	private static void flush() {
		try {
			Files.writeString(STATE_FILE, GSON.toJson(root()));
		} catch (IOException ignored) {
			// не критично — состояние просто не сохранится
		}
	}

	private static JsonObject root() {
		if (cache != null) {
			return cache;
		}

		if (Files.notExists(STATE_FILE)) {
			cache = new JsonObject();
			return cache;
		}

		try {
			String content = Files.readString(STATE_FILE);
			cache = JsonParser.parseString(content).getAsJsonObject();
		} catch (IOException | IllegalStateException ignored) {
			cache = new JsonObject();
		}

		return cache;
	}
}
