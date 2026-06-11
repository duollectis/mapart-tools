package org.duollectis.mapart.tools.gui.dialog;

import org.duollectis.mapart.tools.gui.AppTheme;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.Lang;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Диалог редактора темы оформления.
 * Позволяет настроить все цвета активной темы, задать имя и сохранить
 * в файл {@code themes/<name>.json} рядом с jar-файлом.
 * При сохранении папка {@code themes/} создаётся автоматически.
 */
public class ThemeEditorDialog extends JDialog {

	private static final int SWATCH_SIZE = 28;
	private static final int DIALOG_WIDTH = 560;
	private static final int DIALOG_HEIGHT = 680;

	private final Map<String, Color[]> colorValues = new LinkedHashMap<>();
	private JTextField nameField;

	public ThemeEditorDialog(JFrame parent) {
		super(parent, Lang.t("theme_editor.title"), true);
		initColorValues();
		buildUi();
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setResizable(true);
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	private void initColorValues() {
		AppTheme t = GuiApp.theme;
		colorValues.put("bg_deep", new Color[]{t.getBgDeep()});
		colorValues.put("bg_card", new Color[]{t.getBgCard()});
		colorValues.put("bg_input", new Color[]{t.getBgInput()});
		colorValues.put("border", new Color[]{t.getBorder()});
		colorValues.put("accent", new Color[]{t.getAccent()});
		colorValues.put("accent_bright", new Color[]{t.getAccentBright()});
		colorValues.put("text", new Color[]{t.getText()});
		colorValues.put("text_dim", new Color[]{t.getTextDim()});
		colorValues.put("success", new Color[]{t.getSuccess()});
		colorValues.put("error", new Color[]{t.getError()});
		colorValues.put("warn", new Color[]{t.getWarn()});
		colorValues.put("selection_bg", new Color[]{t.getSelectionBg()});
		colorValues.put("scrollbar_thumb", new Color[]{t.getScrollbarThumb()});
		colorValues.put("scrollbar_thumb_hover", new Color[]{t.getScrollbarThumbHover()});
		colorValues.put("scrollbar_track", new Color[]{t.getScrollbarTrack()});
		colorValues.put("slider_track_bg", new Color[]{t.getSliderTrackBg()});
		colorValues.put("slider_track_fill", new Color[]{t.getSliderTrackFill()});
		colorValues.put("slider_thumb", new Color[]{t.getSliderThumb()});
		colorValues.put("slider_thumb_hover", new Color[]{t.getSliderThumbHover()});
		colorValues.put("text_on_accent", new Color[]{t.getTextOnAccent()});
		colorValues.put("tooltip_bg", new Color[]{t.getTooltipBg()});
		colorValues.put("nimbus_base", new Color[]{t.getNimbusBase()});
		colorValues.put("btn_export_bg", new Color[]{t.getBtnExportBg()});
		colorValues.put("btn_export_fg", new Color[]{t.getBtnExportFg()});
		colorValues.put("btn_blocks_bg", new Color[]{t.getBtnBlocksBg()});
		colorValues.put("btn_blocks_fg", new Color[]{t.getBtnBlocksFg()});
		colorValues.put("btn_import_bg", new Color[]{t.getBtnImportBg()});
		colorValues.put("btn_import_fg", new Color[]{t.getBtnImportFg()});
		colorValues.put("btn_hover_bg", new Color[]{t.getBtnHoverBg()});
		colorValues.put("hover_bg_overlay", new Color[]{t.getHoverBgOverlay()});
		colorValues.put("importing_progress_fg", new Color[]{t.getImportingProgressFg()});
	}

	private void buildUi() {
		getContentPane().setBackground(GuiApp.theme.getBgDeep());
		setLayout(new BorderLayout());

		add(buildNameRow(), BorderLayout.NORTH);
		add(buildScrollableColorGrid(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	private JPanel buildNameRow() {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(GuiApp.theme.getBgDeep());
		row.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

		JLabel label = new JLabel(Lang.t("theme_editor.theme_name_label"));
		label.setForeground(GuiApp.theme.getTextDim());
		label.setFont(new Font("SansSerif", Font.BOLD, 11));

		nameField = new JTextField(Lang.t("theme_editor.theme_name_hint"));
		nameField.setBackground(GuiApp.theme.getBgInput());
		nameField.setForeground(GuiApp.theme.getText());
		nameField.setCaretColor(GuiApp.theme.getAccent());
		nameField.setFont(new Font("SansSerif", Font.PLAIN, 13));
		nameField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GuiApp.theme.getBorder()),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));

		row.add(label, BorderLayout.WEST);
		row.add(nameField, BorderLayout.CENTER);

		return row;
	}

	private JScrollPane buildScrollableColorGrid() {
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBackground(GuiApp.theme.getBgCard());
		grid.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.anchor = GridBagConstraints.WEST;
		labelConstraints.fill = GridBagConstraints.HORIZONTAL;
		labelConstraints.weightx = 1.0;
		labelConstraints.insets = new Insets(3, 4, 3, 8);

		GridBagConstraints swatchConstraints = new GridBagConstraints();
		swatchConstraints.anchor = GridBagConstraints.EAST;
		swatchConstraints.insets = new Insets(3, 0, 3, 4);

		int row = 0;

		for (Map.Entry<String, Color[]> entry : colorValues.entrySet()) {
			String key = entry.getKey();
			Color[] colorRef = entry.getValue();

			JLabel label = new JLabel(Lang.t("theme_editor.field_" + key));
			label.setForeground(GuiApp.theme.getText());
			label.setFont(new Font("SansSerif", Font.PLAIN, 12));

			JButton swatch = buildColorSwatch(colorRef);

			labelConstraints.gridx = 0;
			labelConstraints.gridy = row;
			grid.add(label, labelConstraints);

			swatchConstraints.gridx = 1;
			swatchConstraints.gridy = row;
			grid.add(swatch, swatchConstraints);

			row++;
		}

		JScrollPane scroll = new JScrollPane(grid);
		scroll.setBackground(GuiApp.theme.getBgDeep());
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, GuiApp.theme.getBorder()));
		return scroll;
	}

	private JButton buildColorSwatch(Color[] colorRef) {
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(colorRef[0]);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
				g2.dispose();
			}
		};

		btn.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
		btn.setMinimumSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
		btn.setMaximumSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		btn.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "", colorRef[0], false);

			if (chosen != null) {
				colorRef[0] = chosen;
				btn.repaint();
			}
		});

		return btn;
	}

	private JPanel buildBottomBar() {
		JButton saveBtn = buildPrimaryButton(Lang.t("theme_editor.save"), GuiApp.theme.getAccent(), GuiApp.theme.getBgDeep());
		JButton cancelBtn = buildAccentButton(Lang.t("theme_editor.cancel"), GuiApp.theme.getBgInput(), GuiApp.theme.getTextDim());

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
		String themeName = nameField.getText().trim();

		if (themeName.isBlank()) {
			JOptionPane.showMessageDialog(
				this,
				Lang.t("theme_editor.theme_name_label"),
				Lang.t("theme_editor.title"),
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		AppTheme theme = buildThemeFromValues();

		try {
			AppTheme.save(theme, themeName);
			JOptionPane.showMessageDialog(
				this,
				Lang.t("theme_editor.save_success", themeName),
				Lang.t("theme_editor.title"),
				JOptionPane.INFORMATION_MESSAGE
			);
			dispose();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
				this,
				Lang.t("theme_editor.error_save_failed", ex.getMessage()),
				Lang.t("theme_editor.title"),
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	/**
	 * Собирает объект {@link AppTheme} из текущих значений цветов в редакторе
	 * через рефлексию — маппит snake_case ключи на camelCase поля.
	 */
	private AppTheme buildThemeFromValues() {
		AppTheme theme = new AppTheme();

		for (Map.Entry<String, Color[]> entry : colorValues.entrySet()) {
			String fieldName = snakeToCamel(entry.getKey());

			try {
				Field field = AppTheme.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(theme, entry.getValue()[0]);
			} catch (NoSuchFieldException | IllegalAccessException ignored) {
				// поле не найдено — пропускаем
			}
		}

		return theme;
	}

	private static String snakeToCamel(String snake) {
		StringBuilder result = new StringBuilder();
		boolean nextUpper = false;

		for (char c : snake.toCharArray()) {
			if (c == '_') {
				nextUpper = true;
			} else if (nextUpper) {
				result.append(Character.toUpperCase(c));
				nextUpper = false;
			} else {
				result.append(c);
			}
		}

		return result.toString();
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
