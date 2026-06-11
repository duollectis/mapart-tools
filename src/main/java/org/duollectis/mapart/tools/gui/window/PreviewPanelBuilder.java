package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.Lang;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppLogger;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
 import org.duollectis.mapart.tools.gui.util.ThemeEventBus;
import org.duollectis.mapart.tools.gui.util.UiAnimator;
import org.duollectis.mapart.tools.gui.widget.AppLogPane;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;
import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;
import org.duollectis.mapart.tools.gui.widget.ModernToggleButton;
import org.duollectis.mapart.tools.gui.widget.StyledSlider;

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
		w.sourcePreview = new ImagePreviewPanel(Lang.t("preview.source"));
		w.sourcePreview.setInteractive(() -> {
			if (w.autoConvertToggle.isSelected()) {
				w.actions.scheduleConversion();
			}
		});
		w.actions.setupImageDropTarget(w.sourcePreview);

		w.resultPreview = new ImagePreviewPanel(Lang.t("preview.result"));
		w.resultPreview.setPixelPerfect(true);

		JPanel sourceColumn = wrapSourcePreview(w.sourcePreview, w);
		JPanel resultColumn = wrapResultPreview(w.resultPreview, w);

		JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
		panel.setBackground(MainWindow.BG());
		ThemeEventBus.register(() -> panel.setBackground(MainWindow.BG()));
		panel.add(sourceColumn);
		panel.add(resultColumn);

		return panel;
	}

	private static JPanel wrapSourcePreview(ImagePreviewPanel preview, MainWindow w) {
		JButton fitBtn = buildSmallBtn(Lang.t("preview.btn_fit"), Lang.t("preview.reset_view"), w);
		fitBtn.addActionListener(e -> preview.resetDisplayOffset());

		JButton coverBtn = buildSmallBtn(Lang.t("preview.btn_cover"), Lang.t("preview.cover_view"), w);
		coverBtn.addActionListener(e -> preview.resetDisplayOffsetCover());

		JButton openBtn = buildIconBtn(AppIcon.OPEN_WINDOW, w);
		AppTooltip.install(openBtn, Lang.t("preview.open_window"));
		openBtn.addActionListener(e -> openImageInWindow(preview, w));

		JButton bgColorBtn = buildIconBtn(AppIcon.COLOR_PICKER, w);
		AppTooltip.install(bgColorBtn, Lang.t("preview.bg_color"));
		bgColorBtn.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(w, Lang.t("preview.bg_color"), preview.getBackground());

			if (chosen == null) {
				return;
			}

			preview.setGridBackgroundColor(chosen);
			preview.setShowGridBackground(true);
			preview.repaint();
		});

		ModernToggleButton snapBtn = new ModernToggleButton(Lang.t("preview.btn_snap"));
		AppTooltip.install(snapBtn, Lang.t("preview.snap_tooltip"));
		snapBtn.setSelected(preview.isSnapEnabled());
		snapBtn.syncVisualState();
		snapBtn.addActionListener(e -> preview.setSnapEnabled(snapBtn.isSelected()));

		w.showGridButton = new ModernToggleButton(Lang.t("preview.show_grid"));
		AppTooltip.install(w.showGridButton, Lang.t("preview.show_grid"));
		w.showGridButton.setSelected(false);
		w.showGridButton.syncVisualState();
		w.showGridButton.addActionListener(e -> {
			boolean show = w.showGridButton.isSelected();
			w.sourcePreview.setShowGrid(show);
			w.resultPreview.setShowGrid(show);
		});

		JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		leftBtns.setOpaque(false);
		leftBtns.add(bgColorBtn);
		leftBtns.add(fitBtn);
		leftBtns.add(coverBtn);
		leftBtns.add(snapBtn);
		leftBtns.add(w.showGridButton);

		JPanel gridControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		gridControls.setOpaque(false);
		gridControls.add(openBtn);

		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setOpaque(false);
		toolbar.add(leftBtns, BorderLayout.WEST);
		toolbar.add(gridControls, BorderLayout.EAST);

		JPanel column = new JPanel(new BorderLayout(0, 4));
		column.setOpaque(false);
		column.add(preview, BorderLayout.CENTER);
		column.add(toolbar, BorderLayout.SOUTH);

		return column;
	}

	private static JPanel wrapResultPreview(ImagePreviewPanel preview, MainWindow w) {
		w.blurSlider = new StyledSlider(0, 100, 0);
		w.blurLabel = SettingsPanelBuilder.buildSliderValueLabel(0, true);
		w.blurLabel.setText("0.0");

		w.blurSlider.addChangeListener(e -> {
			double radius = w.blurSlider.getValue() / 100.0 * ImagePreviewPanel.MAX_BLUR_RADIUS;
			w.blurLabel.setText(String.format("%.1f", radius));
			preview.setBlurRadius(radius);
		});

		JButton saveBtn = buildSmallBtn(Lang.t("btn.save_preview"), Lang.t("btn.save_preview"), w);
		saveBtn.addActionListener(e -> w.actions.savePreview());

		JButton openBtn = buildIconBtn(AppIcon.OPEN_WINDOW, w);
		openBtn.addActionListener(e -> openImageInWindow(preview, w));

		JLabel blurNameLabel = SettingsPanelBuilder.dimLabel(Lang.t("preview.blur"));

		JPanel toolbar = new JPanel(new GridBagLayout());
		toolbar.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 0, 0, 6);
		toolbar.add(openBtn, gbc);

		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 0, 0, 4);
		toolbar.add(blurNameLabel, gbc);

		gbc.gridx = 2;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 0, 4);
		toolbar.add(w.blurSlider, gbc);

		gbc.gridx = 3;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(0, 0, 0, 6);
		toolbar.add(w.blurLabel, gbc);

		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 0, 0, 0);
		toolbar.add(saveBtn, gbc);

		JPanel column = new JPanel(new BorderLayout(0, 4));
		column.setOpaque(false);
		column.add(preview, BorderLayout.CENTER);
		column.add(toolbar, BorderLayout.SOUTH);

		return column;
	}

	private static JButton buildSmallBtn(String label, String tooltip, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton(label) {
			@Override
			protected void paintComponent(Graphics g) {
				float progress = hoverProgress(this);
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(UiAnimator.lerp(GuiApp.theme.getBgCard(), GuiApp.theme.getBtnHoverBg(), progress));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				setForeground(UiAnimator.lerp(GuiApp.theme.getTextDim(), GuiApp.theme.getText(), progress));
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		AppTooltip.install(btn, tooltip);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	private static JButton buildIconBtn(AppIcon icon, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				float progress = hoverProgress(this);
				setIcon(icon.colored(UiAnimator.lerp(GuiApp.theme.getTextDim(), GuiApp.theme.getText(), progress)));
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(UiAnimator.lerp(GuiApp.theme.getBgCard(), GuiApp.theme.getBtnHoverBg(), progress));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	private static float hoverProgress(JButton btn) {
		Object value = btn.getClientProperty("hoverProgress");
		return value instanceof Float f ? f : 0f;
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

		JButton saveBtn = new JButton(Lang.t("btn.save_preview")) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		saveBtn.setBackground(MainWindow.ACCENT());
		saveBtn.setForeground(GuiApp.theme.getTextOnAccent());
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
					Lang.t("dialog.error_title"),
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
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(MainWindow.BG());
		panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		ThemeEventBus.register(() -> panel.setBackground(MainWindow.BG()));

		panel.add(buildProgressBar(w), BorderLayout.CENTER);
		panel.add(buildLogArea(w), BorderLayout.SOUTH);

		AppLogger.attach(w.logArea);

		return panel;
	}

	static JProgressBar buildProgressBar(MainWindow w) {
		w.progressBar = new JProgressBar(0, 100) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(MainWindow.CARD());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

				if (getValue() > 0 || isIndeterminate()) {
					g2.setColor(getForeground());
					int fillWidth = isIndeterminate()
						? getWidth()
						: (int) ((double) getValue() / getMaximum() * getWidth());
					g2.fillRoundRect(0, 0, fillWidth, getHeight(), 8, 8);
				}

				if (isStringPainted() && getString() != null) {
					g2.setColor(MainWindow.TEXT());
					g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
					FontMetrics fm = g2.getFontMetrics();
					int textX = (getWidth() - fm.stringWidth(getString())) / 2;
					int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
					g2.drawString(getString(), textX, textY);
				}

				g2.dispose();
			}
		};

		w.progressBar.setUI(new BasicProgressBarUI());
		w.progressBar.setStringPainted(true);
		w.progressBar.setString(Lang.t("progress.ready"));
		w.progressBar.setForeground(MainWindow.ACCENT());
		w.progressBar.setBackground(MainWindow.CARD());
		w.progressBar.setOpaque(false);
		w.progressBar.setPreferredSize(new Dimension(0, 22));
		w.progressBar.setBorder(BorderFactory.createEmptyBorder());

		return w.progressBar;
	}

	private static AppLogPane buildLogArea(MainWindow w) {
		w.logArea = new AppLogPane();
		return w.logArea;
	}
}
