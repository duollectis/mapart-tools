package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.FadingLabel;
import org.duollectis.mapart.tools.gui.widget.RippleButton;
import org.duollectis.mapart.tools.gui.widget.SliderToggle;
import org.duollectis.mapart.tools.gui.widget.ThemedButton;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Фабрика переиспользуемых UI-компонентов для панели настроек.
 * Все методы статические — класс является чистой утилитой без состояния.
 */
public final class SettingsWidgetFactory {

	private SettingsWidgetFactory() {}

	public static JLabel dimLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		label.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));
		return label;
	}

	public static FadingLabel buildFadingDimLabel(String text) {
		FadingLabel label = new FadingLabel(text);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		label.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));
		return label;
	}

	public static JLabel buildSliderValueLabel(int value, boolean isGamma) {
		JLabel label = new JLabel(formatSliderValue(value, isGamma));
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		label.setForeground(MainWindow.TEXT_DIM());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));
		return label;
	}

	public static String formatSliderValue(int value, boolean isGamma) {
		return isGamma
			? String.format("%.2f", value / 100.0)
			: String.valueOf(value);
	}

	public static JTextField buildTextField() {
		JTextField field = new JTextField();
		field.setOpaque(true);
		field.setBackground(MainWindow.INPUT());
		field.setForeground(MainWindow.TEXT());
		field.setCaretColor(MainWindow.TEXT());
		field.setBorder(buildTextFieldBorder());
		field.setFont(new Font("SansSerif", Font.PLAIN, 12));
		UpdatableRegistry.onThemeAnimFrame(() -> {
			field.setBackground(MainWindow.INPUT());
			field.setForeground(MainWindow.TEXT());
			field.setCaretColor(MainWindow.TEXT());
			field.setBorder(buildTextFieldBorder());
		});
		return field;
	}

	public static RippleButton buildIconButton(AppIcon icon, Insets insets, MainWindow w) {
		return new RippleButton(icon, insets, w);
	}

	public static RippleButton buildIconButton(AppIcon icon, MainWindow w) {
		return new RippleButton(icon, new Insets(4, 6, 4, 6), w);
	}

	public static RippleButton buildResetButton(MainWindow w) {
		return new RippleButton(AppIcon.RESET, new Insets(4, 6, 4, 6), w);
	}

	public static JPanel buildToggleRow(String langKey, boolean initial, Consumer<Boolean> onChange) {
		SliderToggle toggle = new SliderToggle(initial);
		toggle.addChangeListener(onChange::accept);

		JLabel label = dimLabel("");
		UpdatableRegistry.registerLang(langKey, label::setText);

		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);
		row.add(toggle, BorderLayout.EAST);

		return row;
	}

	public static ThemedButton buildPrimaryButton(String text, MainWindow w) {
		return new ThemedButton(text, ThemedButton.Style.PRIMARY, w);
	}

	public static ThemedButton buildAccentButton(String text, MainWindow w) {
		return new ThemedButton(text, ThemedButton.Style.ACCENT, w);
	}

	public static ThemedButton buildThemedButton(String text, MainWindow w) {
		return new ThemedButton(text, ThemedButton.Style.THEMED, w);
	}

	private static Border buildTextFieldBorder() {
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(MainWindow.BORDER(), 1),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		);
	}
}
