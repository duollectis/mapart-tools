package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.theme.AppTheme;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.window.MainWindow;
import org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.widget.FadingLabel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Встроенная панель редактора темы оформления.
 * Отображается внутри главного окна через {@link PanelNavigator} вместо отдельного диалога.
 * Позволяет настроить все цвета активной темы, задать имя и сохранить
 * в файл {@code themes/<name>.json} рядом с jar-файлом.
 */
public class ThemeEditorPanel extends JPanel implements NavigablePanel {

	private static final int SWATCH_SIZE = 28;
	private static final int PREVIEW_WIDTH = 56;
	private static final int PREVIEW_HEIGHT = 28;

	private final Map<String, ColorEntry> colorEntries = new LinkedHashMap<>();
	private final Runnable onSaved;
	private final Runnable onBack;
	private final String editingThemeId;
	private JTextField nameField;

	public ThemeEditorPanel(String themeId, Runnable onSaved, Runnable onBack) {
		this.editingThemeId = themeId;
		this.onSaved = onSaved;
		this.onBack = onBack;
		initColorEntries();
		buildUi();
	}

	@Override
	public String getNavTitle() {
		return UpdatableRegistry.translate("theme_editor.title");
	}

	private void initColorEntries() {
		AppTheme t = editingThemeId != null ? AppTheme.load(editingThemeId) : GuiApp.theme;
		addEntry("bg_deep", t.getBgDeep(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRect(0, 0, w, h); });
		addEntry("bg_card", t.getBgCard(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 2, w - 4, h - 4, 6, 6); });
		addEntry("bg_input", t.getBgInput(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 4, 4); });
		addEntry("border", t.getBorder(), (g, w, h, c) -> { g.setColor(c[0]); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(2, 2, w - 5, h - 5, 5, 5); });
		addEntry("accent", t.getAccent(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("accent_bright", t.getAccentBright(), (g, w, h, c) -> { g.setColor(c[0]); g.setStroke(new BasicStroke(2f)); g.drawLine(w / 2, 4, w / 2, h - 4); });
		addEntry("text", t.getText(), (g, w, h, c) -> { g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.drawString("Aa", 8, h / 2 + 4); });
		addEntry("text_dim", t.getTextDim(), (g, w, h, c) -> { g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.drawString("Aa", 8, h / 2 + 4); });
		addEntry("success", t.getSuccess(), (g, w, h, c) -> AppIcon.CHECK.colored(16, c[0]).paintIcon(null, g, 8, h / 2 - 8));
		addEntry("error", t.getError(), (g, w, h, c) -> AppIcon.CROSS.colored(16, c[0]).paintIcon(null, g, 8, h / 2 - 8));
		addEntry("warn", t.getWarn(), (g, w, h, c) -> AppIcon.WARNING.colored(16, c[0]).paintIcon(null, g, 8, h / 2 - 8));
		addEntry("selection_bg", t.getSelectionBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(4, h / 2 - 5, w - 8, 10, 3, 3); });
		addEntry("scrollbar_thumb", t.getScrollbarThumb(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(w / 2 - 3, 4, 6, h - 8, 4, 4); });
		addEntry("scrollbar_thumb_hover", t.getScrollbarThumbHover(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(w / 2 - 4, 4, 8, h - 8, 4, 4); });
		addEntry("scrollbar_track", t.getScrollbarTrack(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(w / 2 - 5, 2, 10, h - 4, 5, 5); });
		addEntry("slider_track_bg", t.getSliderTrackBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(4, h / 2 - 2, w - 8, 4, 3, 3); });
		addEntry("slider_track_fill", t.getSliderTrackFill(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(4, h / 2 - 2, (w - 8) / 2, 4, 3, 3); });
		addEntry("slider_thumb", t.getSliderThumb(), (g, w, h, c) -> { g.setColor(c[0]); g.fillOval(w / 2 - 5, h / 2 - 5, 10, 10); });
		addEntry("slider_thumb_hover", t.getSliderThumbHover(), (g, w, h, c) -> { g.setColor(c[0]); g.fillOval(w / 2 - 6, h / 2 - 6, 12, 12); });
		addEntry("text_on_accent", t.getTextOnAccent(), (g, w, h, c) -> { g.setColor(t.getAccent()); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Btn", 9, h / 2 + 3); });
		addEntry("tooltip_bg", t.getTooltipBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 4, 4); });
		addEntry("nimbus_base", t.getNimbusBase(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 4, 4); });
		addEntry("btn_export_bg", t.getBtnExportBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("btn_export_fg", t.getBtnExportFg(), (g, w, h, c) -> { g.setColor(t.getBtnExportBg()); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Exp", 8, h / 2 + 3); });
		addEntry("btn_blocks_bg", t.getBtnBlocksBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("btn_blocks_fg", t.getBtnBlocksFg(), (g, w, h, c) -> { g.setColor(t.getBtnBlocksBg()); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Blk", 8, h / 2 + 3); });
		addEntry("btn_import_bg", t.getBtnImportBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("btn_import_fg", t.getBtnImportFg(), (g, w, h, c) -> { g.setColor(t.getBtnImportBg()); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Imp", 8, h / 2 + 3); });
		addEntry("btn_hover_bg", t.getBtnHoverBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("hover_bg_overlay", t.getHoverBgOverlay(), (g, w, h, c) -> { g.setColor(new Color(80, 80, 80, 60)); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); });
		addEntry("importing_progress_fg", t.getImportingProgressFg(), (g, w, h, c) -> { g.setColor(new Color(60, 60, 60, 120)); g.fillRoundRect(2, h / 2 - 3, w - 4, 6, 3, 3); g.setColor(c[0]); g.fillRoundRect(2, h / 2 - 3, (w - 4) * 2 / 3, 6, 3, 3); });
		addEntry("dropdown_bg", t.getDropdownBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 2, w - 4, h - 4, 6, 6); });
		addEntry("dropdown_item_bg", t.getDropdownItemBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 4, w - 4, h - 8, 4, 4); });
		addEntry("preview_placeholder_bg", t.getPreviewPlaceholderBg(), (g, w, h, c) -> { g.setColor(c[0]); g.fillRoundRect(2, 2, w - 4, h - 4, 4, 4); });
		Color pbFg = t.getProgressBarFg() != null ? t.getProgressBarFg() : t.getAccent();
		addEntry("progress_bar_fg", pbFg, (g, w, h, c) -> { g.setColor(new Color(60, 60, 60, 80)); g.fillRoundRect(2, h / 2 - 4, w - 4, 8, 4, 4); g.setColor(c[0]); g.fillRoundRect(2, h / 2 - 4, (w - 4) * 2 / 3, 8, 4, 4); });
		Color contrastLight = t.getContrastLight() != null ? t.getContrastLight() : Color.WHITE;
		addEntry("contrast_light", contrastLight, (g, w, h, c) -> { g.setColor(new Color(80, 80, 80, 60)); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Aa", 8, h / 2 + 3); });
		Color contrastDark = t.getContrastDark() != null ? t.getContrastDark() : Color.BLACK;
		addEntry("contrast_dark", contrastDark, (g, w, h, c) -> { g.setColor(new Color(200, 200, 200, 80)); g.fillRoundRect(2, 4, w - 4, h - 8, 6, 6); g.setColor(c[0]); g.setFont(new Font("SansSerif", Font.BOLD, 9)); g.drawString("Aa", 8, h / 2 + 3); });
	}

	private void addEntry(String key, Color initial, PreviewPainter painter) {
		colorEntries.put(key, new ColorEntry(new Color[]{initial}, painter));
	}

	private void buildUi() {
		setLayout(new BorderLayout());
		setOpaque(false);
		add(buildNameRow(), BorderLayout.NORTH);
		add(buildScrollableColorGrid(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	private JPanel buildNameRow() {
		JPanel row = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));

		FadingLabel label = new FadingLabel("");
		UpdatableRegistry.registerLangFading("theme_editor.theme_name_label", label);
		label.setFont(new Font("SansSerif", Font.BOLD, 11));
		label.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));

		String initialName = editingThemeId != null
			? AppTheme.load(editingThemeId).getName()
			: UpdatableRegistry.translate("theme_editor.theme_name_hint");

		nameField = SettingsWidgetFactory.buildTextField();
		nameField.setText(initialName != null ? initialName : editingThemeId);

		row.add(label, BorderLayout.WEST);
		row.add(nameField, BorderLayout.CENTER);
		return row;
	}
	private InertialScrollPane buildScrollableColorGrid() {
		JPanel grid = new JPanel(new GridBagLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.CARD());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		grid.setOpaque(false);
		grid.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

		GridBagConstraints labelGbc = new GridBagConstraints();
		labelGbc.anchor = GridBagConstraints.WEST;
		labelGbc.fill = GridBagConstraints.HORIZONTAL;
		labelGbc.weightx = 1.0;
		labelGbc.insets = new Insets(3, 4, 3, 8);

		GridBagConstraints previewGbc = new GridBagConstraints();
		previewGbc.anchor = GridBagConstraints.EAST;
		previewGbc.insets = new Insets(3, 0, 3, 6);

		GridBagConstraints swatchGbc = new GridBagConstraints();
		swatchGbc.anchor = GridBagConstraints.EAST;
		swatchGbc.insets = new Insets(3, 0, 3, 4);

		int row = 0;

		for (Map.Entry<String, ColorEntry> entry : colorEntries.entrySet()) {
			String key = entry.getKey();
			ColorEntry colorEntry = entry.getValue();

			FadingLabel lbl = new FadingLabel("");
			UpdatableRegistry.registerLangFading("theme_editor.field_" + key, lbl);
			lbl.setForeground(MainWindow.TEXT());
			UpdatableRegistry.onThemeAnimFrame(() -> lbl.setForeground(MainWindow.TEXT()));
			lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

			JPanel preview = buildPreviewPanel(colorEntry);
			JButton swatch = buildColorSwatch(colorEntry, preview);

			labelGbc.gridx = 0;
			labelGbc.gridy = row;
			grid.add(lbl, labelGbc);

			previewGbc.gridx = 1;
			previewGbc.gridy = row;
			grid.add(preview, previewGbc);

			swatchGbc.gridx = 2;
			swatchGbc.gridy = row;
			grid.add(swatch, swatchGbc);

			row++;
		}

		InertialScrollPane scroll = new InertialScrollPane(grid);
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, MainWindow.BORDER()));
		UpdatableRegistry.onThemeAnimFrame(() -> scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, MainWindow.BORDER())));
		return scroll;
	}

	private JPanel buildPreviewPanel(ColorEntry entry) {

		JPanel panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2bg = (Graphics2D) g.create(); g2bg.setColor(MainWindow.BG()); g2bg.fillRect(0, 0, getWidth(), getHeight()); g2bg.dispose();
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				entry.painter().paint(g2, getWidth(), getHeight(), entry.colorRef());
				g2.dispose();
			}
		};
		panel.setPreferredSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		panel.setMinimumSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		panel.setMaximumSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createLineBorder(MainWindow.BORDER(), 1));
		UpdatableRegistry.onThemeAnimFrame(() -> panel.setBorder(BorderFactory.createLineBorder(MainWindow.BORDER(), 1)));
		return panel;
	}

	private JButton buildColorSwatch(ColorEntry entry, JPanel preview) {
		Color[] colorRef = entry.colorRef();

		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(colorRef[0]);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.setColor(MainWindow.BORDER());
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
		UiAnimator.applyHandCursor(btn);

		btn.addActionListener(e -> {
			Point anchor = btn.getLocationOnScreen();
			anchor.translate(btn.getWidth() / 2, btn.getHeight());
			ColorPickerPopup.show(SwingUtilities.getWindowAncestor(this), colorRef[0], anchor, chosen -> {
				colorRef[0] = chosen;
				btn.repaint();
				preview.repaint();
			});
		});

		return btn;
	}

	private JPanel buildBottomBar() {
		ThemedButton saveBtn = new ThemedButton(UpdatableRegistry.translate("theme_editor.save"), ThemedButton.Style.PRIMARY, false);
		ThemedButton cancelBtn = new ThemedButton(UpdatableRegistry.translate("theme_editor.cancel"), ThemedButton.Style.THEMED, false);

		UpdatableRegistry.registerLang("theme_editor.save", saveBtn::setText);
		UpdatableRegistry.registerLang("theme_editor.cancel", cancelBtn::setText);

		saveBtn.addActionListener(e -> onSave());
		cancelBtn.addActionListener(e -> onBack.run());

		JPanel bar = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, MainWindow.BORDER()), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		UpdatableRegistry.onThemeAnimFrame(() -> bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, MainWindow.BORDER()), BorderFactory.createEmptyBorder(10, 12, 10, 12))));
		bar.add(cancelBtn, BorderLayout.WEST);
		bar.add(saveBtn, BorderLayout.EAST);
		return bar;
	}

	private void onSave() {
		String displayName = nameField.getText().trim();

		if (displayName.isBlank()) {
			showStyledMessage(UpdatableRegistry.translate("theme_editor.theme_name_label"), false);
			return;
		}

		AppTheme theme = buildThemeFromValues(displayName);

		try {
			AppTheme.save(theme);

			if (onSaved != null) {
				onSaved.run();
			}

			onBack.run();
		} catch (IOException ex) {
			showStyledMessage(UpdatableRegistry.translate("theme_editor.error_save_failed", ex.getMessage()), true);
		}
	}

	private void showStyledMessage(String message, boolean isError) {
		JOptionPane.showMessageDialog(
			SwingUtilities.getWindowAncestor(this),
			message,
			UpdatableRegistry.translate("theme_editor.title"),
			isError ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE
		);
	}

	/**
	 * Собирает объект {@link AppTheme} из текущих значений цветов через рефлексию.
	 * Маппит snake_case ключи на camelCase поля. Поле {@code name} устанавливается отдельно.
	 *
	 * @param displayName отображаемое имя темы
	 */
	private AppTheme buildThemeFromValues(String displayName) {
		AppTheme theme = new AppTheme();

		try {
			Field nameField = AppTheme.class.getDeclaredField("name");
			nameField.setAccessible(true);
			nameField.set(theme, displayName);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {}

		for (Map.Entry<String, ColorEntry> entry : colorEntries.entrySet()) {
			String fieldName = snakeToCamel(entry.getKey());

			try {
				Field field = AppTheme.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(theme, entry.getValue().colorRef()[0]);
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
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

	@FunctionalInterface
	private interface PreviewPainter {
		void paint(Graphics2D g, int width, int height, Color[] colorRef);
	}

	private record ColorEntry(Color[] colorRef, PreviewPainter painter) {}
}
