package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.app.SingleInstanceGuard;
import org.duollectis.mapart.tools.gui.keybind.KeyBindManager;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class GuiApp {

	private static AppTheme targetTheme = AppTheme.load(BuiltinTheme.DARK.getId());
	private static AppTheme previousTheme = targetTheme;
	private static float colorProgress = 1f;

	/**
	 * Переиспользуемый объект для интерполяции — мутируется на каждом кадре анимации
	 * через {@link AppTheme#blendInto} без создания новых объектов.
	 */
	private static final AppTheme blendedTheme = new AppTheme();

	/**
	 * Текущая активная тема.
	 * Во время анимации смены темы — это {@code blendedTheme} с интерполированными цветами.
	 * После завершения анимации — {@code targetTheme}.
	 */
	public static AppTheme theme = targetTheme;

	public static void launch() {
		AppState.init();
		KeyBindManager.load();

		boolean rpcEnabled = AppPreferences.loadDiscordRpc(true);
		DiscordRpc.setEnabled(rpcEnabled);

		// Синхронный прогрев кэша SVG-иконок до показа окна.
		// Без этого первый paintComponent() кнопок блокирует EDT на ~2-3 сек
		// из-за парсинга SVG через SVGUniverse для каждой иконки × цвет.
		AppIcon.warmupCache(
			theme.getText(),
			theme.getTextDim(),
			theme.getAccent(),
			theme.getBgCard(),
			theme.getContrastLight(),
			theme.getContrastDark()
		);

		SwingUtilities.invokeLater(() -> {
			MainWindow window = new MainWindow();
			SingleInstanceGuard.registerFocusHandler(() -> {
				window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
				window.setVisible(true);
				window.toFront();
				window.requestFocus();
			});
		});
	}

	public static void applyTheme(String themeName) {
		previousTheme = theme;
		targetTheme = AppTheme.load(themeName);
		colorProgress = 1f;
		theme = targetTheme;

		applyLookAndFeel();
		applyUiDefaults();

		String error = AppTheme.getLoadError();

		if (error != null) {
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(
					null,
					UpdatableRegistry.translate("theme_editor.error_load_failed", themeName, error),
					UpdatableRegistry.translate("settings.theme"),
					JOptionPane.WARNING_MESSAGE
				)
			);
		}
	}

	/**
	 * Обновляет прогресс анимации цветового перехода.
	 * Вызывается из {@link org.duollectis.mapart.tools.gui.util.ThemeTransition} на каждом кадре.
	 *
	 * @param progress коэффициент интерполяции [0.0, 1.0]
	 */
	public static void setColorProgress(float progress) {
		colorProgress = progress;

		if (colorProgress >= 1f) {
			theme = targetTheme;
		} else {
			AppTheme.blendInto(blendedTheme, previousTheme, targetTheme, colorProgress);
			theme = blendedTheme;
		}
	}

	private static void applyLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception ignored) {
			// Оставляем системный L&F если Metal недоступен
		}
	}

	private static void applyUiDefaults() {
		Font base = new Font("SansSerif", Font.PLAIN, 13);
		Font mono = new Font("Monospaced", Font.PLAIN, 12);

		UIManager.put("Label.font", base);
		UIManager.put("Button.font", base);
		UIManager.put("TextField.font", base);
		UIManager.put("TextArea.font", mono);
		UIManager.put("ComboBox.font", base);
		UIManager.put("Spinner.font", base);
		UIManager.put("CheckBox.font", base);
		UIManager.put("List.font", base);
		UIManager.put("OptionPane.messageFont", base);

		UIManager.put("ScrollBar.width", 8);

		Border empty = BorderFactory.createEmptyBorder();
		UIManager.put("Panel.border", empty);
		UIManager.put("ScrollPane.border", empty);
		UIManager.put("Spinner.border", empty);
		UIManager.put("ComboBox.border", empty);
		UIManager.put("List.border", empty);
		UIManager.put("Table.border", empty);
		UIManager.put("TableHeader.cellBorder", empty);
		UIManager.put("SplitPane.border", empty);
		UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
		UIManager.put("PopupMenu.border", empty);
		UIManager.put("MenuBar.border", empty);
		UIManager.put("ToolBar.border", empty);
		UIManager.put("Viewport.border", empty);
		UIManager.put("ProgressBar.border", empty);
	}
}
