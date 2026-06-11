package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class GuiApp {

	private static AppTheme targetTheme = AppTheme.load("dark");
	private static AppTheme previousTheme = targetTheme;
	private static float colorProgress = 1f;

	/**
	 * Возвращает текущую активную тему.
	 * Во время анимации смены темы возвращает интерполированный снимок между
	 * {@code previousTheme} и {@code targetTheme}. После завершения — {@code targetTheme}.
	 */
	public static AppTheme theme = targetTheme;

	public static void launch() {
		String savedTheme = AppPreferences.loadTheme("dark");
		applyTheme(savedTheme);
		SwingUtilities.invokeLater(MainWindow::new);
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
					Lang.t("theme_editor.error_load_failed", themeName, error),
					Lang.t("settings.theme"),
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
		theme = colorProgress >= 1f
			? targetTheme
			: AppTheme.blend(previousTheme, targetTheme, colorProgress);
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
