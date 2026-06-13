package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppLogger;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;
import org.duollectis.mapart.tools.gui.util.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AppLogPane;
import org.duollectis.mapart.tools.gui.widget.ColorPickerPopup;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;
import org.duollectis.mapart.tools.gui.widget.LayerListPanel;
import org.duollectis.mapart.tools.gui.widget.ModernToggleButton;
import org.duollectis.mapart.tools.gui.widget.RippleButton;
import org.duollectis.mapart.tools.gui.widget.StyledSlider;
import org.duollectis.mapart.tools.gui.widget.WrapLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

class PreviewPanelBuilder {

	/**
	 * Строит центральную панель с двумя превью-изображениями (исходник и результат).
	 * Левое превью — полностью интерактивное, кнопки управления вынесены над карточкой.
	 * Правое превью — pixel-perfect отображение результата, только кнопка раскрытия.
	 */
	static JPanel buildPreviewPanel(MainWindow w) {
		w.sourcePreview = new ImagePreviewPanel("");
		UpdatableRegistry.registerLang("preview.source", w.sourcePreview::setTitle);
		w.sourcePreview.setInteractive(() -> {
			if (w.autoConvertToggle.isSelected()) {
				w.actions.scheduleConversion();
			}
		});
		w.actions.setupImageDropTarget(w.sourcePreview);

		w.resultPreview = new ImagePreviewPanel("");
		UpdatableRegistry.registerLang("preview.result", w.resultPreview::setTitle);
		w.resultPreview.setPixelPerfect(true);

		w.layerListPanel = new LayerListPanel(w.sourcePreview, w);
		w.layerListPanel.setOnActiveChanged(idx -> {
			if (w.autoConvertToggle.isSelected()) {
				w.actions.scheduleConversion();
			}
		});
		w.layerListPanel.setOnLayerRemoved(idx -> {
			if (w.sourcePreview.getLayers().isEmpty()) {
				w.resultPreview.clear();
			} else if (w.autoConvertToggle.isSelected()) {
				w.actions.scheduleConversion();
			}
		});
		w.layerListPanel.setOnLayersReordered(() -> {
			if (w.autoConvertToggle.isSelected()) {
				w.actions.scheduleConversion();
			}
		});

		JPanel sourceColumn = wrapSourcePreview(w.sourcePreview);
		JPanel resultColumn = wrapResultPreview(w.resultPreview, w);

		JPanel previewsGrid = new JPanel(new GridLayout(1, 2, 8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		previewsGrid.setOpaque(true);
		previewsGrid.add(sourceColumn);
		previewsGrid.add(resultColumn);

		JPanel toolbar = buildSourceToolbar(w);

		JPanel bottom = new JPanel(new BorderLayout(0, 4));
		bottom.setOpaque(false);
		bottom.add(w.layerListPanel, BorderLayout.CENTER);
		bottom.add(toolbar, BorderLayout.SOUTH);

		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.add(previewsGrid, BorderLayout.CENTER);
		panel.add(bottom, BorderLayout.SOUTH);

		return panel;
	}

	private static JPanel buildSourceToolbar(MainWindow w) {
		RippleButton fitBtn = new RippleButton(AppIcon.FIT, w);
		fitBtn.addActionListener(e -> w.sourcePreview.resetDisplayOffset());
		UpdatableRegistry.registerLang("preview.reset_view", t -> AppTooltip.install(fitBtn, t));

		RippleButton coverBtn = new RippleButton(AppIcon.COVER, w);
		coverBtn.addActionListener(e -> w.sourcePreview.resetDisplayOffsetCover());
		UpdatableRegistry.registerLang("preview.cover_view", t -> AppTooltip.install(coverBtn, t));

		RippleButton bgColorBtn = new RippleButton(AppIcon.COLOR_PICKER, w);
		bgColorBtn.addActionListener(e -> {
			Point anchor = bgColorBtn.getLocationOnScreen();
			anchor.translate(bgColorBtn.getWidth() / 2, bgColorBtn.getHeight());
			ColorPickerPopup.show(w, w.sourcePreview.getGridBackgroundColor(), anchor, chosen -> {
				w.sourcePreview.setGridBackgroundColor(chosen);
				w.sourcePreview.setShowGridBackground(true);
				w.sourcePreview.repaint();
				UiStateRegistry.notifyColor("preview.bg_color_rgb", chosen);
			});
		});
		UpdatableRegistry.registerLang("preview.bg_color", t -> AppTooltip.install(bgColorBtn, t));

		w.snapButton = new ModernToggleButton("");
		w.snapButton.putClientProperty("appIcon", AppIcon.MAGNET);
		w.snapButton.setSelected(w.sourcePreview.isSnapEnabled());
		w.snapButton.syncVisualState();
		w.snapButton.addActionListener(e -> w.sourcePreview.setSnapEnabled(w.snapButton.isSelected()));
		UiStateRegistry.bindToggle("preview.snap", w.snapButton);
		UpdatableRegistry.registerLang("preview.snap_tooltip", t -> AppTooltip.install(w.snapButton, t));

		w.showGridButton = new ModernToggleButton("");
		w.showGridButton.putClientProperty("appIcon", AppIcon.GRID);
		w.showGridButton.setSelected(false);
		w.showGridButton.syncVisualState();
		w.showGridButton.addActionListener(e -> {
			boolean show = w.showGridButton.isSelected();
			w.sourcePreview.setShowGrid(show);
			w.resultPreview.setShowGrid(show);
		});
		UiStateRegistry.bindToggle("preview.show_grid", w.showGridButton);
		UpdatableRegistry.registerLang("preview.grid_tooltip", t -> AppTooltip.install(w.showGridButton, t));

		RippleButton oneMapBtn = new RippleButton(AppIcon.BLOCK, w);
		oneMapBtn.addActionListener(e -> w.sourcePreview.resetDisplayOffsetOneMap());
		UpdatableRegistry.registerLang("preview.one_map_view", t -> AppTooltip.install(oneMapBtn, t));

		RippleButton addLayerBtn = new RippleButton(AppIcon.IMAGE, w);
		addLayerBtn.addActionListener(e -> w.actions.chooseImageAsNewLayer());
		UpdatableRegistry.registerLang("preview.add_layer", t -> AppTooltip.install(addLayerBtn, t));

		RippleButton halvBtn = new RippleButton(AppIcon.HALVE, w);
		halvBtn.addActionListener(e -> w.actions.scaleMapCount(0.5));
		UpdatableRegistry.registerLang("btn.halve_maps", t -> AppTooltip.install(halvBtn, t));

		RippleButton doubleBtn = new RippleButton(AppIcon.DOUBLE, w);
		doubleBtn.addActionListener(e -> w.actions.scaleMapCount(2.0));
		UpdatableRegistry.registerLang("btn.double_maps", t -> AppTooltip.install(doubleBtn, t));

		RippleButton autoFitBtn = new RippleButton(AppIcon.AUTO_FIT, w);
		autoFitBtn.addActionListener(e -> w.actions.autoFitMapCount());
		UpdatableRegistry.registerLang("btn.auto_fit_maps", t -> AppTooltip.install(autoFitBtn, t));

		w.mapSizeControl = new org.duollectis.mapart.tools.gui.widget.MapSizeControl(w);
		w.mapSizeControl.addWidthChangeListener(v -> {
			w.actions.syncSourcePreviewMapCount();
			w.actions.scheduleConversionIfAuto();
		});
		w.mapSizeControl.addHeightChangeListener(v -> {
			w.actions.syncSourcePreviewMapCount();
			w.actions.scheduleConversionIfAuto();
		});

		w.convertButton = new RippleButton(AppIcon.PLAY, new Insets(4, 8, 4, 8), w);
		w.convertButton.setAccentMode(true);
		w.convertButton.addActionListener(e -> w.actions.startConversion());
		UpdatableRegistry.registerLang("btn.convert", t -> AppTooltip.install(w.convertButton, t));

		JPanel toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
		toolbar.setOpaque(false);
		toolbar.add(bgColorBtn);
		toolbar.add(fitBtn);
		toolbar.add(coverBtn);
		toolbar.add(oneMapBtn);
		toolbar.add(w.snapButton);
		toolbar.add(w.showGridButton);
		toolbar.add(addLayerBtn);
		toolbar.add(halvBtn);
		toolbar.add(doubleBtn);
		toolbar.add(autoFitBtn);
		toolbar.add(w.mapSizeControl);
		toolbar.add(w.convertButton);

		return toolbar;
	}

	private static JPanel wrapSourcePreview(ImagePreviewPanel preview) {
		JPanel column = new JPanel(new BorderLayout(0, 0));
		column.setOpaque(false);
		column.add(preview, BorderLayout.CENTER);
		return column;
	}

	private static JPanel wrapResultPreview(ImagePreviewPanel preview, MainWindow w) {
		w.blurSlider = new StyledSlider(0, 100, 0);
		w.blurLabel = SettingsWidgetFactory.buildSliderValueLabel(0, true);
		w.blurLabel.setText("0.0");

		w.blurSlider.addChangeListener(e -> {
			double radius = w.blurSlider.getValue() / 100.0 * ImagePreviewPanel.MAX_BLUR_RADIUS;
			w.blurLabel.setText(String.format("%.1f", radius));
			preview.setBlurRadius(radius);
		});
		UiStateRegistry.bindSlider("preview.blur", w.blurSlider);

		RippleButton saveBtn = new RippleButton("", w);
		saveBtn.addActionListener(e -> w.actions.savePreview());
		UpdatableRegistry.registerLang("btn.save_preview", saveBtn::setText);
		UpdatableRegistry.registerLang("btn.save_preview", t -> AppTooltip.install(saveBtn, t));

		JLabel blurNameLabel = SettingsWidgetFactory.dimLabel("");
		UpdatableRegistry.registerLang("preview.blur", blurNameLabel::setText);

		JPanel toolbar = new JPanel(new GridBagLayout());
		toolbar.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.CENTER;

		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 0, 0, 4);
		toolbar.add(blurNameLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 0, 4);
		toolbar.add(w.blurSlider, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(0, 0, 0, 6);
		toolbar.add(w.blurLabel, gbc);

		gbc.gridx = 3;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 0, 0, 0);
		toolbar.add(saveBtn, gbc);

		JPanel column = new JPanel(new BorderLayout(0, 4));
		column.setOpaque(false);
		column.add(preview, BorderLayout.CENTER);
		column.add(toolbar, BorderLayout.SOUTH);

		return column;
	}

	private static void openImageInWindow(ImagePreviewPanel preview, MainWindow w) {
		BufferedImage image = preview.getImage();

		if (image == null) {
			return;
		}

		String title = preview.getTitle();
		JDialog dialog = new JDialog(w, title, false);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		ImagePreviewPanel fullPreview = new ImagePreviewPanel(title);
		fullPreview.setImage(image);
		fullPreview.copyDisplayStateFrom(preview);

		JButton saveBtn = new JButton("") {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				Color fg = ContrastTextRenderer.contrastFor(getBackground());
				setForeground(fg);
				Object appIconProp = getClientProperty("appIcon");
				if (appIconProp instanceof AppIcon ai) {
					setIcon(ai.colored(fg));
				}
				super.paintComponent(g);
			}
		};

		UpdatableRegistry.registerLang("btn.save_preview", saveBtn::setText);
		saveBtn.putClientProperty("appIcon", AppIcon.IMAGE);
		saveBtn.setBackground(MainWindow.ACCENT());
		saveBtn.setForeground(ContrastTextRenderer.contrastFor(MainWindow.ACCENT()));
		saveBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
		saveBtn.setFocusPainted(false);
		saveBtn.setBorderPainted(false);
		saveBtn.setContentAreaFilled(false);
		saveBtn.setOpaque(false);
		saveBtn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
		saveBtn.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setSelectedFile(new File("preview.png"));

			if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
				return;
			}

			try {
				ImageIO.write(image, "png", chooser.getSelectedFile());
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(
					dialog,
					ex.getMessage(),
					UpdatableRegistry.translate("dialog.error_title"),
					JOptionPane.ERROR_MESSAGE
				);
			}
		});

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		bottom.setBackground(MainWindow.BG());
		bottom.add(saveBtn);

		dialog.setLayout(new BorderLayout());
		dialog.getContentPane().setBackground(MainWindow.BG());
		dialog.add(fullPreview, BorderLayout.CENTER);
		dialog.add(bottom, BorderLayout.SOUTH);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(w);
		dialog.setVisible(true);
	}

	/**
	 * Строит нижнюю панель с прогресс-баром и логом.
	 * Подключает {@link AppLogger} к виджету лога для перехвата stdout/stderr.
	 */
	static JPanel buildBottomPanel(MainWindow w) {
		JPanel panel = new JPanel(new BorderLayout(0, 6)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		panel.setOpaque(true);
		panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		panel.add(buildProgressBar(w), BorderLayout.CENTER);
		panel.add(buildLogArea(w), BorderLayout.SOUTH);

		AppLogger.attach(w.logArea);

		return panel;
	}

	static JProgressBar buildProgressBar(MainWindow w) {
		w.progressBar = new AnimatedProgressBar();
		w.progressBar.setUI(new BasicProgressBarUI());
		w.progressBar.setStringPainted(true);
		UpdatableRegistry.registerLang("progress.ready", w.progressBar::setString);
		w.progressBar.setForeground(MainWindow.PROGRESS_BAR());
		w.progressBar.setBackground(MainWindow.CARD());
		w.progressBar.setOpaque(false);
		w.progressBar.setPreferredSize(new Dimension(0, 22));
		w.progressBar.setBorder(BorderFactory.createEmptyBorder());
		UpdatableRegistry.onThemeAnimFrame(() -> w.progressBar.applyThemeColor(MainWindow.PROGRESS_BAR()));

		return w.progressBar;
	}

	private static AppLogPane buildLogArea(MainWindow w) {
		w.logArea = new AppLogPane();
		return w.logArea;
	}
}

/**
	* Прогресс-бар с плавной анимацией смены цвета заливки и значения прогресса.
	* При вызове {@link #setForeground(Color)} запускает интерполяцию от текущего
	* отображаемого цвета к новому за {@code COLOR_ANIM_MS} мс.
	* При вызове {@link #setValue(int)} плавно анимирует заполнение за {@code VALUE_ANIM_MS} мс.
	*/
class AnimatedProgressBar extends JProgressBar {

	private static final int COLOR_ANIM_MS = 400;
	private static final int VALUE_ANIM_MS = 300;
	private static final Font TEXT_FONT = new Font("SansSerif", Font.PLAIN, 11);

	private Color displayColor;
	private float displayValue;
	private javax.swing.Timer colorTimer;
	private javax.swing.Timer valueTimer;

	AnimatedProgressBar() {
		super(0, 100);
		displayColor = MainWindow.PROGRESS_BAR();
		displayValue = 0f;
	}

	/**
	 * Обновляет цвет без анимации — вызывается на каждом кадре анимации темы.
	 * Не запускает colorTimer, чтобы не конфликтовать с анимацией смены состояния.
	 */
	void applyThemeColor(Color color) {
		super.setForeground(color);

		if (colorTimer == null || !colorTimer.isRunning()) {
			displayColor = color;
			repaint();
		}
	}

	@Override
	public void setForeground(Color target) {
		super.setForeground(target);

		if (displayColor == null) {
			displayColor = target;
			return;
		}

		Color from = displayColor;

		if (colorTimer != null) {
			colorTimer.stop();
		}

		colorTimer = UiAnimator.animateFloat(0f, 1f, COLOR_ANIM_MS, t -> {
			displayColor = UiAnimator.lerp(from, target, t);
			repaint();
		}, null);
	}

	@Override
	public void setValue(int target) {
		super.setValue(target);

		float from = displayValue;
		float to = target;

		if (valueTimer != null) {
			valueTimer.stop();
		}

		valueTimer = UiAnimator.animateFloat(0f, 1f, VALUE_ANIM_MS, t -> {
			displayValue = from + (to - from) * UiAnimator.easeOutCubic(t);
			repaint();
		}, null);
	}

	@Override
	protected void paintComponent(Graphics g) {
		int width = getWidth();
		int height = getHeight();
		Color fillColor = displayColor != null ? displayColor : getForeground();

		BufferedImage bgSnapshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D bgG = bgSnapshot.createGraphics();
		bgG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		bgG.setColor(MainWindow.CARD());
		bgG.fillRoundRect(0, 0, width, height, 8, 8);

		if (displayValue >= 1f || isIndeterminate()) {
			bgG.setColor(fillColor);
			int fillWidth = isIndeterminate()
				? width
				: (int) (displayValue / getMaximum() * width);
			bgG.fillRoundRect(0, 0, fillWidth, height, 8, 8);
		}

		bgG.dispose();

		Graphics2D g2 = (Graphics2D) g.create();
		g2.drawImage(bgSnapshot, 0, 0, null);

		if (isStringPainted() && getString() != null) {
			ContrastTextRenderer.drawCenteredOnBackground(
				g2, getString(), TEXT_FONT, bgSnapshot, width, height
			);
		}

		g2.dispose();
	}
}
