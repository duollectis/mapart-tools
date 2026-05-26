package org.duollectis.mapart.tools.gui;

import lombok.Getter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Диалог настроек приложения.
 * Позволяет выбрать язык интерфейса. Изменения применяются после перезапуска.
 */
public class SettingsDialog extends JDialog {

	private static final Color BG = GuiApp.BG_DEEP;
	private static final Color CARD = GuiApp.BG_CARD;
	private static final Color INPUT = GuiApp.BG_INPUT;
	private static final Color BORDER = GuiApp.BORDER;
	private static final Color ACCENT = GuiApp.ACCENT;
	private static final Color TEXT = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;
	private static final Color SUCCESS = GuiApp.SUCCESS;

	private static final String[][] LANGUAGES = {
		{"ru_ru", "Русский"},
		{"en_us", "English"}
	};

	private ModernComboBox<String> langCombo;
	@Getter
	private boolean confirmed = false;

	public SettingsDialog(JFrame parent) {
		super(parent, Lang.t("settings.title"), true);
		buildUi();
		setSize(360, 180);
		setResizable(false);
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	private void buildUi() {
		getContentPane().setBackground(BG);
		setLayout(new BorderLayout());

		add(buildContent(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	private JPanel buildContent() {
		JPanel card = buildCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		JLabel langLabel = new JLabel(Lang.t("settings.language"));
		langLabel.setForeground(TEXT_DIM);
		langLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		langLabel.setAlignmentX(LEFT_ALIGNMENT);

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

		card.add(langLabel);
		card.add(Box.createVerticalStrut(6));
		card.add(langCombo);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(BG);
		wrapper.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		wrapper.add(card, BorderLayout.CENTER);

		return wrapper;
	}

	private JPanel buildBottomBar() {
		JButton saveBtn = buildPrimaryButton(Lang.t("settings.save"), ACCENT, BG);
		JButton cancelBtn = buildAccentButton(Lang.t("settings.cancel"), INPUT, TEXT_DIM);

		saveBtn.addActionListener(e -> onSave());
		cancelBtn.addActionListener(e -> dispose());

		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBackground(BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)
		));
		bar.add(cancelBtn, BorderLayout.WEST);
		bar.add(saveBtn, BorderLayout.EAST);

		return bar;
	}

	private void onSave() {
		int idx = langCombo.getSelectedIndex();

		if (idx >= 0 && idx < LANGUAGES.length) {
			AppPreferences.saveLocale(LANGUAGES[idx][0]);
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
				g2.setColor(CARD);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
				g2.setColor(BORDER);
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
				g2.setColor(BORDER);
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
