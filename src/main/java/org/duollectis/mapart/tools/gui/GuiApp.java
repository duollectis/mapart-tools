package org.duollectis.mapart.tools.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public class GuiApp {

	static AppTheme theme = AppTheme.dark();

	static Color BG_DEEP;
	static Color BG_CARD;
	static Color BG_INPUT;
	static Color BORDER;
	static Color ACCENT;
	static Color ACCENT_BRIGHT;
	static Color TEXT;
	static Color TEXT_DIM;
	static Color SUCCESS;
	static Color ERROR;
	static Color WARN;
	static Color SELECTION_BG;

	private static Color SCROLLBAR_THUMB;
	private static Color SCROLLBAR_THUMB_HOVER;
	private static Color SCROLLBAR_TRACK;

	public static void launch() {
		String savedTheme = AppPreferences.loadTheme("dark");
		applyTheme(savedTheme);
		SwingUtilities.invokeLater(MainWindow::new);
	}

	static void applyTheme(String themeName) {
		theme = AppTheme.load(themeName);
		syncColorFields();
		applyLookAndFeel();
		applyUiDefaults();
	}

	private static void syncColorFields() {
		BG_DEEP = theme.bgDeep();
		BG_CARD = theme.bgCard();
		BG_INPUT = theme.bgInput();
		BORDER = theme.border();
		ACCENT = theme.accent();
		ACCENT_BRIGHT = theme.accentBright();
		TEXT = theme.text();
		TEXT_DIM = theme.textDim();
		SUCCESS = theme.success();
		ERROR = theme.error();
		WARN = theme.warn();
		SELECTION_BG = theme.selectionBg();
		SCROLLBAR_THUMB = theme.scrollbarThumb();
		SCROLLBAR_THUMB_HOVER = theme.scrollbarThumbHover();
		SCROLLBAR_TRACK = theme.scrollbarTrack();
	}

	/**
	 * Создаёт кастомный {@link BasicScrollBarUI} с тонким скруглённым ползунком
	 * и невидимыми кнопками-стрелками — в стиле современных тёмных интерфейсов.
	 */
	static BasicScrollBarUI buildScrollBarUi() {
		return new BasicScrollBarUI() {

			@Override
			protected void configureScrollBarColors() {
				thumbColor = SCROLLBAR_THUMB;
				trackColor = SCROLLBAR_TRACK;
			}

			@Override
			protected void paintThumb(Graphics g, javax.swing.JComponent c, Rectangle thumbBounds) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(isThumbRollover() ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB);
				g2.fillRoundRect(
					thumbBounds.x + 2,
					thumbBounds.y + 2,
					thumbBounds.width - 4,
					thumbBounds.height - 4,
					6,
					6
				);
				g2.dispose();
			}

			@Override
			protected void paintTrack(Graphics g, javax.swing.JComponent c, Rectangle trackBounds) {
				g.setColor(SCROLLBAR_TRACK);
				g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
			}

			@Override
			protected JButton createDecreaseButton(int orientation) {
				return buildInvisibleButton();
			}

			@Override
			protected JButton createIncreaseButton(int orientation) {
				return buildInvisibleButton();
			}

			private JButton buildInvisibleButton() {
				JButton btn = new JButton();
				btn.setPreferredSize(new Dimension(0, 0));
				btn.setMinimumSize(new Dimension(0, 0));
				btn.setMaximumSize(new Dimension(0, 0));
				return btn;
			}

			@Override
			public Dimension getPreferredSize(javax.swing.JComponent c) {
				return scrollbar.getOrientation() == JScrollBar.VERTICAL
						? new Dimension(8, Integer.MAX_VALUE)
						: new Dimension(Integer.MAX_VALUE, 8);
			}
		};
	}

	private static void applyLookAndFeel() {
		try {
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		}
		catch (Exception ignored) {
			// Оставляем стандартный L&F если Nimbus недоступен
		}
	}

	private static void applyUiDefaults() {
		Font base = new Font("SansSerif", Font.PLAIN, 13);
		Font mono = new Font("Monospaced", Font.PLAIN, 12);

		UIManager.put("control", BG_CARD);
		UIManager.put("info", BG_CARD);
		UIManager.put("nimbusBase", new Color(18, 22, 40));
		UIManager.put("nimbusAlertYellow", WARN);
		UIManager.put("nimbusDisabledText", TEXT_DIM);
		UIManager.put("nimbusFocus", ACCENT);
		UIManager.put("nimbusGreen", SUCCESS);
		UIManager.put("nimbusInfoBlue", ACCENT);
		UIManager.put("nimbusLightBackground", BG_INPUT);
		UIManager.put("nimbusOrange", WARN);
		UIManager.put("nimbusRed", ERROR);
		UIManager.put("nimbusSelectedText", Color.WHITE);
		UIManager.put("nimbusSelectionBackground", SELECTION_BG);
		UIManager.put("text", TEXT);

		UIManager.put("Panel.background", BG_DEEP);
		UIManager.put("Panel.foreground", TEXT);

		UIManager.put("Label.foreground", TEXT);
		UIManager.put("Label.font", base);

		UIManager.put("Button.background", BG_CARD);
		UIManager.put("Button.foreground", TEXT);
		UIManager.put("Button.font", base);

		UIManager.put("TextField.background", BG_INPUT);
		UIManager.put("TextField.foreground", TEXT);
		UIManager.put("TextField.caretForeground", ACCENT);
		UIManager.put("TextField.selectionBackground", SELECTION_BG);
		UIManager.put("TextField.selectionForeground", Color.WHITE);
		UIManager.put("TextField.font", base);
		UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));

		UIManager.put("TextArea.background", BG_DEEP);
		UIManager.put("TextArea.foreground", TEXT);
		UIManager.put("TextArea.caretForeground", ACCENT);
		UIManager.put("TextArea.selectionBackground", SELECTION_BG);
		UIManager.put("TextArea.selectionForeground", Color.WHITE);
		UIManager.put("TextArea.font", mono);

		UIManager.put("ComboBox.background", BG_INPUT);
		UIManager.put("ComboBox.foreground", TEXT);
		UIManager.put("ComboBox.selectionBackground", SELECTION_BG);
		UIManager.put("ComboBox.selectionForeground", Color.WHITE);
		UIManager.put("ComboBox.font", base);

		UIManager.put("Spinner.background", BG_INPUT);
		UIManager.put("Spinner.foreground", TEXT);
		UIManager.put("Spinner.font", base);

		UIManager.put("CheckBox.background", BG_CARD);
		UIManager.put("CheckBox.foreground", TEXT);
		UIManager.put("CheckBox.font", base);

		UIManager.put("List.background", BG_INPUT);
		UIManager.put("List.foreground", TEXT);
		UIManager.put("List.selectionBackground", SELECTION_BG);
		UIManager.put("List.selectionForeground", Color.WHITE);
		UIManager.put("List.font", base);

		UIManager.put("ScrollPane.background", BG_DEEP);
		UIManager.put("ScrollPane.foreground", TEXT);
		UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(BORDER));
		UIManager.put("ScrollBar.background", SCROLLBAR_TRACK);
		UIManager.put("ScrollBar.foreground", SCROLLBAR_THUMB);
		UIManager.put("ScrollBar.thumb", SCROLLBAR_THUMB);
		UIManager.put("ScrollBar.thumbHighlight", SCROLLBAR_THUMB_HOVER);
		UIManager.put("ScrollBar.thumbShadow", SCROLLBAR_TRACK);
		UIManager.put("ScrollBar.track", SCROLLBAR_TRACK);
		UIManager.put("ScrollBar.width", 8);

		UIManager.put("ProgressBar.background", BG_INPUT);
		UIManager.put("ProgressBar.foreground", ACCENT);
		UIManager.put("ProgressBar.selectionBackground", TEXT);
		UIManager.put("ProgressBar.selectionForeground", BG_DEEP);
		UIManager.put("ProgressBar.border", BorderFactory.createLineBorder(BORDER));

		UIManager.put("OptionPane.background", BG_CARD);
		UIManager.put("OptionPane.messageForeground", TEXT);
		UIManager.put("OptionPane.messageFont", base);

		UIManager.put("TitledBorder.titleColor", TEXT_DIM);
		UIManager.put("TitledBorder.border", new javax.swing.border.LineBorder(BORDER));

		UIManager.put("ToolTip.background", new Color(28, 32, 48));
		UIManager.put("ToolTip.foreground", TEXT);
		UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
		UIManager.put("ToolTip.font", new Font("SansSerif", Font.PLAIN, 12));
	}
}
