package org.duollectis.mapart.tools.gui.dialog;

import lombok.Getter;
import org.duollectis.mapart.tools.gui.AppPreferences;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.Lang;
import org.duollectis.mapart.tools.gui.widget.ModernComboBox;

import javax.swing.*;
import java.awt.*;

/**
 * Диалог настроек приложения.
 * Позволяет выбрать язык интерфейса и тему оформления. Изменения применяются после перезапуска.
 */
public class SettingsDialog extends JDialog {

	private static final String[][] LANGUAGES = {
		{"ru_ru", "Русский"},
		{"en_us", "English"}
	};

	private static final String[][] THEMES = {
		{"dark", null},
		{"light", null}
	};

	private ModernComboBox<String> langCombo;
	private ModernComboBox<String> themeCombo;
	private String initialLocale;

	@Getter
	private boolean confirmed = false;

	public SettingsDialog(JFrame parent) {
		super(parent, Lang.t("settings.title"), true);
		initialLocale = AppPreferences.loadLocale("ru_ru");
		buildUi();
		setSize(360, 290);
		setResizable(false);
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	public boolean isLocaleChanged() {
		int idx = langCombo.getSelectedIndex();
		String selected = (idx >= 0 && idx < LANGUAGES.length) ? LANGUAGES[idx][0] : initialLocale;
		return !selected.equals(initialLocale);
	}

	private void buildUi() {
		getContentPane().setBackground(GuiApp.theme.getBgDeep());
		setLayout(new BorderLayout());

		add(buildContent(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	private JPanel buildContent() {
		JPanel card = buildCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		card.add(buildRowLabel("settings.language"));
		card.add(Box.createVerticalStrut(6));
		card.add(buildLangCombo());
		card.add(Box.createVerticalStrut(12));
		card.add(buildRowLabel("settings.theme"));
		card.add(Box.createVerticalStrut(6));
		card.add(buildThemeCombo());
		card.add(Box.createVerticalStrut(10));
		card.add(buildThemeEditorButton());

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(GuiApp.theme.getBgDeep());
		wrapper.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		wrapper.add(card, BorderLayout.CENTER);

		return wrapper;
	}

	private JButton buildThemeEditorButton() {
		JButton btn = buildAccentButton(
			"🎨  " + Lang.t("theme_editor.title"),
			GuiApp.theme.getBgInput(),
			GuiApp.theme.getTextDim()
		);
		btn.setAlignmentX(LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		btn.addActionListener(e -> new ThemeEditorDialog((JFrame) getOwner()));
		return btn;
	}

	private JLabel buildRowLabel(String langKey) {
		JLabel label = new JLabel(Lang.t(langKey));
		label.setForeground(GuiApp.theme.getTextDim());
		label.setFont(new Font("SansSerif", Font.BOLD, 11));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private ModernComboBox<String> buildLangCombo() {
		String[] displayNames = new String[LANGUAGES.length];

		for (int i = 0; i < LANGUAGES.length; i++) {
			displayNames[i] = LANGUAGES[i][1];
		}

		langCombo = new ModernComboBox<>(displayNames);
		langCombo.setAlignmentX(LEFT_ALIGNMENT);
		langCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		String savedLocale = AppPreferences.loadLocale("ru_ru");

		for (int i = 0; i < LANGUAGES.length; i++) {
			if (LANGUAGES[i][0].equals(savedLocale)) {
				langCombo.setSelectedIndex(i);
				break;
			}
		}

		return langCombo;
	}

	private ModernComboBox<String> buildThemeCombo() {
		THEMES[0][1] = Lang.t("settings.theme_dark");
		THEMES[1][1] = Lang.t("settings.theme_light");

		String[] displayNames = {THEMES[0][1], THEMES[1][1]};

		themeCombo = new ModernComboBox<>(displayNames);
		themeCombo.setAlignmentX(LEFT_ALIGNMENT);
		themeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		String savedTheme = AppPreferences.loadTheme("dark");

		for (int i = 0; i < THEMES.length; i++) {
			if (THEMES[i][0].equals(savedTheme)) {
				themeCombo.setSelectedIndex(i);
				break;
			}
		}

		return themeCombo;
	}

	private JPanel buildBottomBar() {
		JButton saveBtn = buildPrimaryButton(Lang.t("settings.save"), GuiApp.theme.getAccent(), GuiApp.theme.getBgDeep());
		JButton cancelBtn = buildAccentButton(Lang.t("settings.cancel"), GuiApp.theme.getBgInput(), GuiApp.theme.getTextDim());

		saveBtn.addActionListener(e -> onSave());
		cancelBtn.addActionListener(e -> dispose());

		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBackground(GuiApp.theme.getBgDeep());
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, GuiApp.theme.getBorder()),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)
		));
		bar.add(cancelBtn, BorderLayout.WEST);
		bar.add(saveBtn, BorderLayout.EAST);

		return bar;
	}

	private void onSave() {
		int langIdx = langCombo.getSelectedIndex();

		if (langIdx >= 0 && langIdx < LANGUAGES.length) {
			AppPreferences.saveLocale(LANGUAGES[langIdx][0]);
		}

		int themeIdx = themeCombo.getSelectedIndex();

		if (themeIdx >= 0 && themeIdx < THEMES.length) {
			AppPreferences.saveTheme(THEMES[themeIdx][0]);
		}

		confirmed = true;
		dispose();
	}

	private JPanel buildCard() {
		return new JPanel() {
			{
				setOpaque(false);
				setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
				g2.dispose();
			}
		};
	}

	private JButton buildPrimaryButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? bgColor.darker()
					: (getModel().isRollover() ? bgColor.brighter() : bgColor);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
		btn.setHorizontalAlignment(SwingConstants.CENTER);

		return btn;
	}

	private JButton buildAccentButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? bgColor.darker()
					: (getModel().isRollover() ? bgColor.brighter() : bgColor);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

		return btn;
	}
}
