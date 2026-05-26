package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.CropSettings;
import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.DitherSettings;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.swing.*;
import javax.swing.JColorChooser;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainWindow extends JFrame {

	private static final int WINDOW_WIDTH = 1140;
	private static final int WINDOW_HEIGHT = 800;
	private static final int SPINNER_MIN = 1;
	private static final int SPINNER_MAX = 32;
	private static final int CARD_RADIUS = 12;
	private static final int SETTINGS_MIN_WIDTH = 350;
	private static final int SETTINGS_MAX_WIDTH = 550;

	private static final Color BG = GuiApp.BG_DEEP;
	private static final Color CARD = GuiApp.BG_CARD;
	private static final Color INPUT = GuiApp.BG_INPUT;
	private static final Color BORDER = GuiApp.BORDER;
	private static final Color ACCENT = GuiApp.ACCENT;
	private static final Color TEXT = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;
	private static final Color SUCCESS = GuiApp.SUCCESS;
	private static final Color ERROR = GuiApp.ERROR;
	private static final Color WARN = GuiApp.WARN;

	private static final String DEFAULT_SUPPORT_BLOCK = "minecraft:stone";

	private ModernComboBox<String> versionCombo;
	private ModernSpinner widthSpinner;
	private ModernSpinner heightSpinner;
	private JTextField imagePathField;
	private JTextField blocksPathField;
	private JTextField outPathField;
	private JTextField supportBlockField;
	private ModernComboBox<Ditherer.Algorithm> algorithmCombo;
	private JPanel ditherSettingsPanel;
	private ModernSlider errorRateSlider;
	private ModernSlider noiseLevelSlider;
	private JLabel errorRateLabel;
	private JLabel noiseLevelLabel;
	private ModernToggleButton autoConvertToggle;
	private ModernSlider brightnessSlider;
	private ModernSlider contrastSlider;
	private ModernSlider saturationSlider;
	private ModernSlider gammaSlider;
	private ModernSlider hueSlider;
	private JLabel brightnessLabel;
	private JLabel contrastLabel;
	private JLabel saturationLabel;
	private JLabel gammaLabel;
	private JLabel hueLabel;
	private JButton convertButton;
	private JButton exportButton;
	private JButton savePreviewButton;
	private JButton pickBlocksButton;
	private JLabel blocksCountLabel;
	private JProgressBar progressBar;
	private JTextArea logArea;
	private ImagePreviewPanel sourcePreview;
	private ImagePreviewPanel resultPreview;
	private ModernCheckBox showGridCheckBox;
	private ModernSpinner gridWidthSpinner;
	private JButton gridBgColorButton;

	private File selectedImageFile;
	private BufferedImage rawSourceImage;
	private ConversionWorker activeConversionWorker;
	private ExportWorker activeExportWorker;
	private Ditherer lastDitherer;
	private Set<String> enabledBlocks = new HashSet<>();
	private volatile boolean sourcePreviewPending;
	private volatile boolean sourcePreviewRunning;
	private volatile boolean resetSourceViewOnNextImage;
	private Timer conversionDebounceTimer;
	private final Map<String, String> paletteCache = new HashMap<>();

	public MainWindow() {
		super(Lang.t("app.title"));
		buildUi();
		restorePreferences();
		tryAutoLoadBlocks();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				savePreferences();
			}
		});
		setVisible(true);
	}

	private void buildUi() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
		setMinimumSize(new Dimension(920, 620));
		setLocationRelativeTo(null);
		getContentPane().setBackground(BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenterPanel(), BorderLayout.CENTER);

		syncSourcePreviewMapCount();

		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addKeyEventDispatcher(event -> {
				boolean isCtrlV = event.getID() == KeyEvent.KEY_PRESSED
					&& event.getKeyCode() == KeyEvent.VK_V
					&& event.isControlDown();

				if (!isCtrlV) {
					return false;
				}

				boolean focusInTextField = event.getComponent() instanceof JTextField;

				if (focusInTextField) {
					return false;
				}

				pasteImageFromClipboard();
				return true;
			});
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				GradientPaint gradient = new GradientPaint(
					0, 0, new Color(18, 22, 45),
					getWidth(), 0, new Color(25, 30, 55)
				);
				g2.setPaint(gradient);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
				g2.dispose();
			}
		};
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

		JLabel title = new JLabel("🗺  " + Lang.t("app.title"));
		title.setFont(new Font("SansSerif", Font.BOLD, 22));
		title.setForeground(ACCENT);

		JLabel subtitle = new JLabel(Lang.t("app.subtitle"));
		subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
		subtitle.setForeground(TEXT_DIM);

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(title);
		left.add(Box.createVerticalStrut(2));
		left.add(subtitle);

		JButton settingsBtn = buildIconButton(Lang.t("btn.settings"));
		settingsBtn.addActionListener(e -> openSettings());

		header.add(left, BorderLayout.WEST);
		header.add(settingsBtn, BorderLayout.EAST);

		return header;
	}

	private JPanel buildCenterPanel() {
		JPanel rightColumn = new JPanel(new BorderLayout(0, 0));
		rightColumn.setBackground(BG);
		rightColumn.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 12));
		rightColumn.add(buildPreviewPanel(), BorderLayout.CENTER);
		rightColumn.add(buildBottomPanel(), BorderLayout.SOUTH);

		JPanel settingsPanel = buildSettingsPanel();

		JPanel center = new JPanel(new BorderLayout(10, 0));
		center.setBackground(BG);
		center.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 0));
		center.add(settingsPanel, BorderLayout.WEST);
		center.add(rightColumn, BorderLayout.CENTER);

		// Динамически ограничиваем ширину левой панели: растёт пропорционально окну,
		// но не выходит за пределы [SETTINGS_MIN_WIDTH, SETTINGS_MAX_WIDTH].
		center.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				int targetWidth = (int) (center.getWidth() * 0.28);
				int clampedWidth = Math.max(SETTINGS_MIN_WIDTH, Math.min(SETTINGS_MAX_WIDTH, targetWidth));
				settingsPanel.setPreferredSize(new Dimension(clampedWidth, 0));
				center.revalidate();
			}
		});

		return center;
	}

	private JPanel buildSettingsPanel() {
		// Контент с реализацией Scrollable — фиксирует ширину по viewport,
		// чтобы BoxLayout не растягивал панель горизонтально внутри JScrollPane.
		JPanel content = new ScrollablePanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));

		content.add(buildSectionLabel(Lang.t("section.version")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildVersionRow());

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.size")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildSizeRow());

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.algorithm")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildAlgorithmRow());
		content.add(Box.createVerticalStrut(4));
		content.add(buildDitherSettingsPanel());

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.image_adjust")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildImageAdjustPanel());

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.image")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildFileRow(imagePathField = buildTextField(Lang.t("placeholder.image")), this::chooseImage));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.blocks")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildBlocksRow());

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.outdir")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildFileRow(outPathField = buildTextField(Lang.t("placeholder.outdir")), this::chooseOutDir));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.support_block")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildSupportBlockRow());

		JScrollPane scroll = new JScrollPane(
			content,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
		);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(12);
		scroll.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());

		JPanel buttons = buildActionButtons();
		buttons.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));

		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(CARD);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS * 2, CARD_RADIUS * 2);
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS * 2, CARD_RADIUS * 2);
				g2.dispose();
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(SETTINGS_MIN_WIDTH, super.getMinimumSize().height);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(SETTINGS_MAX_WIDTH, Integer.MAX_VALUE);
			}
		};
		card.setOpaque(false);
		card.setBorder(BorderFactory.createEmptyBorder());
		card.setLayout(new BorderLayout());
		card.add(scroll, BorderLayout.CENTER);
		card.add(buttons, BorderLayout.SOUTH);

		return card;
	}

	private JPanel buildVersionRow() {
		versionCombo = new ModernComboBox<>(loadVersions());

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(versionCombo, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private JPanel buildSizeRow() {
		widthSpinner = new ModernSpinner(new SpinnerNumberModel(1, SPINNER_MIN, SPINNER_MAX, 1));
		heightSpinner = new ModernSpinner(new SpinnerNumberModel(1, SPINNER_MIN, SPINNER_MAX, 1));

		widthSpinner.addChangeListener(e -> {
			syncSourcePreviewMapCount();
			scheduleConversionIfAuto();
		});

		heightSpinner.addChangeListener(e -> {
			syncSourcePreviewMapCount();
			scheduleConversionIfAuto();
		});

		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 0, 6);

		gbc.gridx = 0;
		gbc.weightx = 0;
		row.add(dimLabel(Lang.t("label.width")), gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		row.add(widthSpinner, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 10, 0, 6);
		row.add(dimLabel(Lang.t("label.height")), gbc);

		gbc.gridx = 3;
		gbc.weightx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		row.add(heightSpinner, gbc);

		JButton autoFitBtn = buildIconButton("⊡", 14, new Insets(2, 6, 2, 6));
		autoFitBtn.setToolTipText(Lang.t("btn.auto_fit_maps"));
		autoFitBtn.addActionListener(e -> autoFitMapCount());

		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(0, 6, 0, 0);
		row.add(autoFitBtn, gbc);

		return row;
	}

	private JPanel buildImageAdjustPanel() {
		ImageAdjustments defaults = ImageAdjustments.defaults();

		brightnessSlider = buildSlider(-100, 100, defaults.brightness());
		contrastSlider = buildSlider(-100, 100, defaults.contrast());
		saturationSlider = buildSlider(-100, 100, defaults.saturation());
		gammaSlider = buildSlider(10, 300, defaults.gamma());
		hueSlider = buildSlider(-180, 180, defaults.hue());

		brightnessLabel = buildSliderValueLabel(defaults.brightness(), false);
		contrastLabel = buildSliderValueLabel(defaults.contrast(), false);
		saturationLabel = buildSliderValueLabel(defaults.saturation(), false);
		gammaLabel = buildSliderValueLabel(defaults.gamma(), true);
		hueLabel = buildSliderValueLabel(defaults.hue(), false);

		brightnessSlider.addChangeListener(e -> {
			brightnessLabel.setText(formatSliderValue(brightnessSlider.getValue(), false));
			scheduleSourcePreview();
			scheduleConversion();
		});

		contrastSlider.addChangeListener(e -> {
			contrastLabel.setText(formatSliderValue(contrastSlider.getValue(), false));
			scheduleSourcePreview();
			scheduleConversion();
		});

		saturationSlider.addChangeListener(e -> {
			saturationLabel.setText(formatSliderValue(saturationSlider.getValue(), false));
			scheduleSourcePreview();
			scheduleConversion();
		});

		gammaSlider.addChangeListener(e -> {
			gammaLabel.setText(formatSliderValue(gammaSlider.getValue(), true));
			scheduleSourcePreview();
			scheduleConversion();
		});

		hueSlider.addChangeListener(e -> {
			hueLabel.setText(formatSliderValue(hueSlider.getValue(), false));
			scheduleSourcePreview();
			scheduleConversion();
		});

		autoConvertToggle = new ModernToggleButton(Lang.t("adjust.auto_convert"));

		JButton resetBtn = buildIconButton(Lang.t("adjust.reset"));
		resetBtn.addActionListener(e -> resetAdjustments());

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 0, 1, 4);

		addSliderRow(grid, gbc, 0, Lang.t("adjust.brightness"), brightnessSlider, brightnessLabel, defaults.brightness(), false);
		addSliderRow(grid, gbc, 1, Lang.t("adjust.contrast"), contrastSlider, contrastLabel, defaults.contrast(), false);
		addSliderRow(grid, gbc, 2, Lang.t("adjust.saturation"), saturationSlider, saturationLabel, defaults.saturation(), false);
		addSliderRow(grid, gbc, 3, Lang.t("adjust.gamma"), gammaSlider, gammaLabel, defaults.gamma(), true);
		addSliderRow(grid, gbc, 4, Lang.t("adjust.hue"), hueSlider, hueLabel, defaults.hue(), false);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(grid);
		wrapper.add(Box.createVerticalStrut(4));

		JButton convertNowBtn = buildIconButton(Lang.t("adjust.convert_now"));
		convertNowBtn.addActionListener(e -> startConversion());

		JPanel controlRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
		controlRow.setOpaque(false);
		controlRow.add(autoConvertToggle);
		controlRow.add(resetBtn);
		controlRow.add(convertNowBtn);
		wrapper.add(controlRow);

		return wrapper;
	}

	private void addSliderRow(
		JPanel grid,
		GridBagConstraints gbc,
		int row,
		String label,
		ModernSlider slider,
		JLabel valueLabel,
		int defaultValue,
		boolean isGamma
	) {
		gbc.gridy = row;

		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 6);
		JLabel nameLabel = dimLabel(label);
		nameLabel.setPreferredSize(new Dimension(80, 16));
		grid.add(nameLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.insets = new Insets(1, 0, 1, 4);
		grid.add(slider, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 4);
		valueLabel.setPreferredSize(new Dimension(36, 16));
		JButton decBtn = buildIconButton("◀", 9, new Insets(2, 4, 2, 4));
		JButton incBtn = buildIconButton("▶", 9, new Insets(2, 4, 2, 4));
		decBtn.addActionListener(e -> {
			int next = Math.max(slider.getMinimum(), slider.getValue() - 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});
		incBtn.addActionListener(e -> {
			int next = Math.min(slider.getMaximum(), slider.getValue() + 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});
		JPanel valueCell = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 2, 0));
		valueCell.setOpaque(false);
		valueCell.add(decBtn);
		valueCell.add(valueLabel);
		valueCell.add(incBtn);
		grid.add(valueCell, gbc);

		gbc.gridx = 3;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 0);
		JButton resetOne = buildIconButton("↺");
		resetOne.addActionListener(e -> {
			slider.setValue(defaultValue);
			valueLabel.setText(formatSliderValue(defaultValue, isGamma));
		});
		grid.add(resetOne, gbc);
	}

	private ModernSlider buildSlider(int min, int max, int value) {
		return new ModernSlider(min, max, value);
	}

	private JButton buildIconButton(String text, int fontSize, Insets padding) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				if (getModel().isRollover()) {
					g2.setColor(new Color(255, 255, 255, 20));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setForeground(TEXT_DIM);
		btn.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right));
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				btn.setForeground(TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				btn.setForeground(TEXT_DIM);
			}
		});

		return btn;
	}

	private JLabel buildSliderValueLabel(int value, boolean isGamma) {
		JLabel label = new JLabel(formatSliderValue(value, isGamma));
		label.setForeground(TEXT_DIM);
		label.setFont(new Font("Monospaced", Font.PLAIN, 11));
		label.setHorizontalAlignment(JLabel.RIGHT);

		return label;
	}

	private String formatSliderValue(int value, boolean isGamma) {
		return isGamma
			? String.format("%.2f", value / 100.0)
			: (value >= 0 ? "+" : "") + value;
	}

	private void resetAdjustments() {
		ImageAdjustments defaults = ImageAdjustments.defaults();
		brightnessSlider.setValue(defaults.brightness());
		contrastSlider.setValue(defaults.contrast());
		saturationSlider.setValue(defaults.saturation());
		gammaSlider.setValue(defaults.gamma());
		hueSlider.setValue(defaults.hue());
	}

	private ImageAdjustments buildAdjustments() {
		return new ImageAdjustments(
			brightnessSlider.getValue(),
			contrastSlider.getValue(),
			saturationSlider.getValue(),
			gammaSlider.getValue(),
			hueSlider.getValue()
		);
	}

	private JPanel buildAlgorithmRow() {
		algorithmCombo = new ModernComboBox<>(Ditherer.Algorithm.values());
		algorithmCombo.addActionListener(e -> {
			refreshDitherSettingsPanel();
			scheduleConversionIfAuto();
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(algorithmCombo, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private JPanel buildDitherSettingsPanel() {
		DitherSettings defaults = DitherSettings.defaults();
		int defaultErrorRate = (int) (defaults.errorDiffusionRate() * 100);
		int defaultNoiseLevel = (int) (defaults.noiseLevel() * 100);

		errorRateSlider = buildSlider(0, 100, defaultErrorRate);
		noiseLevelSlider = buildSlider(0, 100, defaultNoiseLevel);

		errorRateLabel = buildSliderValueLabel(defaultErrorRate, true);
		noiseLevelLabel = buildSliderValueLabel(defaultNoiseLevel, true);

		errorRateSlider.addChangeListener(e -> {
			errorRateLabel.setText(formatSliderValue(errorRateSlider.getValue(), true));
			scheduleConversionIfAuto();
		});

		noiseLevelSlider.addChangeListener(e -> {
			noiseLevelLabel.setText(formatSliderValue(noiseLevelSlider.getValue(), true));
			scheduleConversionIfAuto();
		});

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 0, 1, 4);

		addSliderRow(grid, gbc, 0, Lang.t("dither.error_rate"), errorRateSlider, errorRateLabel, defaultErrorRate, true);
		addSliderRow(grid, gbc, 1, Lang.t("dither.noise_level"), noiseLevelSlider, noiseLevelLabel, defaultNoiseLevel, true);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(grid);

		ditherSettingsPanel = wrapper;

		refreshDitherSettingsPanel();

		return wrapper;
	}

	private void refreshDitherSettingsPanel() {
		if (ditherSettingsPanel == null) {
			return;
		}

		Ditherer.Algorithm selected = (Ditherer.Algorithm) algorithmCombo.getSelectedItem();

		if (selected == null || selected == Ditherer.Algorithm.NONE) {
			ditherSettingsPanel.setVisible(false);
			return;
		}

		boolean isErrorDiffusion = selected.getId() <= 8;
		ditherSettingsPanel.setVisible(true);

		// Строка errorRate видна только для алгоритмов диффузии ошибки
		setSliderRowVisible(errorRateSlider, errorRateLabel, isErrorDiffusion);
	}

	private void setSliderRowVisible(ModernSlider slider, JLabel valueLabel, boolean visible) {
		// Находим все компоненты в той же строке GridBagLayout и переключаем видимость
		java.awt.Container parent = slider.getParent();

		if (parent == null) {
			return;
		}

		java.awt.GridBagLayout layout = (java.awt.GridBagLayout) parent.getLayout();
		int targetRow = layout.getConstraints(slider).gridy;

		for (java.awt.Component comp : parent.getComponents()) {
			java.awt.GridBagConstraints c = layout.getConstraints(comp);

			if (c.gridy == targetRow) {
				comp.setVisible(visible);
			}
		}
	}

	private JPanel buildBlocksRow() {
		blocksPathField = buildTextField(Lang.t("placeholder.blocks"));

		blocksCountLabel = new JLabel(Lang.t("label.blocks_not_selected"));
		blocksCountLabel.setForeground(TEXT_DIM);
		blocksCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		blocksCountLabel.setAlignmentX(LEFT_ALIGNMENT);

		pickBlocksButton = buildAccentButton(Lang.t("btn.pick_blocks"), new Color(34, 85, 34), new Color(100, 210, 130));
		pickBlocksButton.addActionListener(e -> openBlockPicker());

		JButton browseBtn = buildIconButton("...");
		browseBtn.addActionListener(e -> chooseBlocks());

		JPanel fileRow = new JPanel(new BorderLayout(4, 0));
		fileRow.setOpaque(false);
		fileRow.add(blocksPathField, BorderLayout.CENTER);
		fileRow.add(browseBtn, BorderLayout.EAST);
		fileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JPanel buttonRow = new JPanel(new BorderLayout(8, 0));
		buttonRow.setOpaque(false);
		buttonRow.add(pickBlocksButton, BorderLayout.CENTER);
		buttonRow.add(blocksCountLabel, BorderLayout.EAST);
		buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(fileRow);
		wrapper.add(Box.createVerticalStrut(5));
		wrapper.add(buttonRow);

		return wrapper;
	}

	private JPanel buildSupportBlockRow() {
		supportBlockField = buildTextField(DEFAULT_SUPPORT_BLOCK);
		supportBlockField.setText(DEFAULT_SUPPORT_BLOCK);

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(supportBlockField, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private JPanel buildFileRow(JTextField field, Runnable onBrowse) {
		JButton browseBtn = buildIconButton("...");
		browseBtn.addActionListener(e -> onBrowse.run());

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(field, BorderLayout.CENTER);
		row.add(browseBtn, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private JPanel buildActionButtons() {
		convertButton = buildPrimaryButton(Lang.t("btn.convert"), ACCENT, BG);
		convertButton.addActionListener(e -> startConversion());

		exportButton = buildAccentButton(Lang.t("btn.export_nbt"), new Color(60, 45, 10), WARN);
		exportButton.setEnabled(false);
		exportButton.addActionListener(e -> startExport());

		savePreviewButton = buildAccentButton(Lang.t("btn.save_preview"), new Color(20, 50, 20), SUCCESS);
		savePreviewButton.setEnabled(false);
		savePreviewButton.addActionListener(e -> savePreview());

		convertButton.setAlignmentX(LEFT_ALIGNMENT);
		exportButton.setAlignmentX(LEFT_ALIGNMENT);
		savePreviewButton.setAlignmentX(LEFT_ALIGNMENT);

		convertButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, convertButton.getPreferredSize().height + 4));
		exportButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, exportButton.getPreferredSize().height + 4));
		savePreviewButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, savePreviewButton.getPreferredSize().height + 4));

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.add(convertButton);
		panel.add(Box.createVerticalStrut(6));
		panel.add(exportButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(savePreviewButton);

		return panel;
	}

	private JPanel buildPreviewPanel() {
		sourcePreview = new ImagePreviewPanel(Lang.t("preview.source"));
		resultPreview = new ImagePreviewPanel(Lang.t("preview.result"));

		stylePreviewPanel(sourcePreview);
		stylePreviewPanel(resultPreview);
		setupImageDropTarget(sourcePreview);

		// Левая картинка интерактивна: drag, resize за края, zoom колёсиком
		sourcePreview.setInteractive(this::scheduleConversionIfAuto);

		// Правая панель всегда показывает чёрный фон сетки
		resultPreview.setShowGridBackground(true);

		showGridCheckBox = new ModernCheckBox(Lang.t("preview.show_grid"));
		showGridCheckBox.addActionListener(e -> {
			boolean show = showGridCheckBox.isSelected();
			sourcePreview.setShowGrid(show);
			resultPreview.setShowGrid(show);
		});

		gridWidthSpinner = new ModernSpinner(new SpinnerNumberModel(1, 1, 10, 1));
		gridWidthSpinner.addChangeListener(e -> {
			float width = ((Number) gridWidthSpinner.getValue()).floatValue();
			sourcePreview.setGridStrokeWidth(width);
			resultPreview.setGridStrokeWidth(width);
		});

		gridBgColorButton = buildGridBgColorButton();

		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(BG);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.insets = new Insets(0, 0, 0, 5);

		JButton resetBtn = buildIconButton(Lang.t("adjust.reset"), 11, new Insets(2, 8, 2, 8));
		resetBtn.setToolTipText(Lang.t("preview.reset_view"));
		resetBtn.addActionListener(e -> sourcePreview.resetDisplayOffset());

		gbc.gridx = 0;
		panel.add(wrapPreviewWithButton(sourcePreview, Lang.t("preview.source"), null, null, resetBtn), gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, 5, 0, 0);
		panel.add(wrapPreviewWithButton(resultPreview, Lang.t("preview.result"), showGridCheckBox, gridWidthSpinner, gridBgColorButton), gbc);

		return panel;
	}

	private JPanel wrapPreviewWithButton(
		ImagePreviewPanel preview,
		String windowTitle,
		ModernCheckBox checkBox,
		ModernSpinner gridSpinner
	) {
		return wrapPreviewWithButton(preview, windowTitle, checkBox, gridSpinner, null);
	}

	private JPanel wrapPreviewWithButton(
		ImagePreviewPanel preview,
		String windowTitle,
		ModernCheckBox checkBox,
		ModernSpinner gridSpinner,
		JButton extraButton
	) {
		JButton expandBtn = buildIconButton("⤢", 14, new Insets(2, 5, 2, 5));
		expandBtn.setToolTipText(Lang.t("preview.open_window"));
		expandBtn.addActionListener(e -> {
			BufferedImage img = preview.getImage();
			if (img == null) {
				return;
			}
			openImageInWindow(img, windowTitle);
		});

		JLayeredPane layered = new JLayeredPane() {
			@Override
			public Dimension getPreferredSize() {
				return preview.getPreferredSize();
			}

			@Override
			public Dimension getMinimumSize() {
				return preview.getMinimumSize();
			}
		};
		layered.setOpaque(false);

		preview.setBounds(0, 0, 0, 0);
		layered.add(preview, JLayeredPane.DEFAULT_LAYER);
		layered.add(expandBtn, JLayeredPane.PALETTE_LAYER);

		layered.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				int w = layered.getWidth();
				int h = layered.getHeight();
				preview.setBounds(0, 0, w, h);

				Dimension btnSize = expandBtn.getPreferredSize();
				expandBtn.setBounds(w - btnSize.width - 4, 2, btnSize.width, btnSize.height);
			}
		});

		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);

		if (checkBox != null) {
			footer.add(checkBox, BorderLayout.WEST);
		}

		if (gridSpinner != null || extraButton != null) {
			JPanel eastGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			eastGroup.setOpaque(false);

			if (extraButton != null) {
				eastGroup.add(extraButton);
			}

			if (gridSpinner != null) {
				JLabel gridWidthLabel = dimLabel(Lang.t("preview.grid_width"));
				eastGroup.add(gridWidthLabel);
				eastGroup.add(gridSpinner);
			}

			footer.add(eastGroup, BorderLayout.EAST);
		}

		if (checkBox == null && gridSpinner == null && extraButton == null) {
			Dimension cbSize = new ModernCheckBox("").getPreferredSize();
			footer.setPreferredSize(new Dimension(0, cbSize.height));
		}

		JPanel wrapper = new JPanel(new BorderLayout(0, 4));
		wrapper.setOpaque(false);
		wrapper.add(layered, BorderLayout.CENTER);
		wrapper.add(footer, BorderLayout.SOUTH);

		return wrapper;
	}

	private JButton buildGridBgColorButton() {
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(resultPreview != null ? resultPreview.getGridBackgroundColor() : Color.BLACK);
				g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 4, 4);
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 4, 4);
				g2.dispose();
			}
		};

		btn.setPreferredSize(new Dimension(22, 22));
		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setToolTipText(Lang.t("preview.bg_color"));
		btn.addActionListener(e -> {
			Color current = resultPreview != null ? resultPreview.getGridBackgroundColor() : Color.BLACK;
			Color chosen = JColorChooser.showDialog(this, Lang.t("preview.bg_color"), current);

			if (chosen == null) {
				return;
			}

			resultPreview.setGridBackgroundColor(chosen);
			btn.repaint();
		});

		return btn;
	}

	private void openImageInWindow(BufferedImage image, String title) {
		JFrame window = new JFrame(title);
		window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JLabel imageLabel = new JLabel(new ImageIcon(image));
		imageLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JScrollPane scroll = new JScrollPane(imageLabel);
		scroll.setBackground(new Color(10, 11, 18));
		scroll.getViewport().setBackground(new Color(10, 11, 18));
		scroll.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());
		scroll.getHorizontalScrollBar().setUI(GuiApp.buildScrollBarUi());
		scroll.getVerticalScrollBar().setBackground(new Color(10, 11, 18));
		scroll.getHorizontalScrollBar().setBackground(new Color(10, 11, 18));

		window.getContentPane().setBackground(new Color(10, 11, 18));
		window.getContentPane().add(scroll);

		var screenBounds = GraphicsEnvironment
			.getLocalGraphicsEnvironment()
			.getMaximumWindowBounds();

		int maxW = (int) (screenBounds.getWidth() * 0.9);
		int maxH = (int) (screenBounds.getHeight() * 0.9);

		int winW = Math.min(image.getWidth() + 32, maxW);
		int winH = Math.min(image.getHeight() + 32, maxH);

		window.setSize(winW, winH);
		window.setLocationRelativeTo(this);
		window.setVisible(true);
	}

	private JPanel buildBottomPanel() {
		progressBar = buildProgressBar();

		logArea = new JTextArea(4, 0);
		logArea.setEditable(false);
		logArea.setBackground(new Color(10, 11, 18));
		logArea.setForeground(new Color(180, 190, 210));
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		logArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		logScroll.setPreferredSize(new Dimension(0, 95));
		logScroll.getViewport().setBackground(new Color(10, 11, 18));
		logScroll.setBackground(new Color(10, 11, 18));
		logScroll.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());
		logScroll.getHorizontalScrollBar().setUI(GuiApp.buildScrollBarUi());
		logScroll.getVerticalScrollBar().setBackground(new Color(10, 11, 18));
		logScroll.getHorizontalScrollBar().setBackground(new Color(10, 11, 18));

		JPanel bottom = new JPanel(new BorderLayout(0, 5));
		bottom.setBackground(BG);
		bottom.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		bottom.add(progressBar, BorderLayout.NORTH);
		bottom.add(logScroll, BorderLayout.CENTER);

		return bottom;
	}

	private JProgressBar buildProgressBar() {
		JProgressBar bar = new JProgressBar() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				g2.setColor(INPUT);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

				if (isIndeterminate()) {
					g2.setColor(ACCENT);
					g2.fillRoundRect(0, 0, getWidth() / 3, getHeight(), 6, 6);
				} else if (getValue() > getMinimum()) {
					int fillW = (int) ((double) (getValue() - getMinimum())
						/ (getMaximum() - getMinimum()) * getWidth());
					g2.setColor(ACCENT);
					g2.fillRoundRect(0, 0, fillW, getHeight(), 6, 6);
				}

				if (isStringPainted() && getString() != null) {
					g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
					g2.setColor(TEXT);
					FontMetrics fm = g2.getFontMetrics();
					int tx = (getWidth() - fm.stringWidth(getString())) / 2;
					int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
					g2.drawString(getString(), tx, ty);
				}

				g2.setColor(BORDER);
				g2.setStroke(new java.awt.BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

				g2.dispose();
			}
		};

		bar.setIndeterminate(false);
		bar.setStringPainted(true);
		bar.setString(Lang.t("progress.ready"));
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder());
		bar.setPreferredSize(new Dimension(0, 22));

		return bar;
	}

	// ── Бизнес-логика ──────────────────────────────────────────────────────────

	/**
	 * Throttle-обновление превью оригинала: запускает обработку мгновенно в фоновом потоке.
	 * Если воркер уже работает — ставит флаг pending, и по завершении текущего
	 * автоматически запускается ещё один с актуальными настройками.
	 */
	private void scheduleSourcePreview() {
		if (rawSourceImage == null) {
			return;
		}

		sourcePreviewPending = true;

		if (sourcePreviewRunning) {
			return;
		}

		runSourcePreviewWorker();
	}

	private void runSourcePreviewWorker() {
		sourcePreviewPending = false;
		sourcePreviewRunning = true;

		BufferedImage src = rawSourceImage;
		ImageAdjustments snapshot = buildAdjustments();

		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() {
				return ImageUtils.applyAdjustments(src, snapshot);
			}

			@Override
			protected void done() {
				try {
					sourcePreview.setImage(get());

					if (resetSourceViewOnNextImage) {
						resetSourceViewOnNextImage = false;
						sourcePreview.resetDisplayOffset();
					}
				} catch (Exception ignored) {
				}

				sourcePreviewRunning = false;

				if (sourcePreviewPending) {
					runSourcePreviewWorker();
				}
			}
		}.execute();
	}

	/** Запускает конвертацию с debounce 400ms только если включена кнопка «Авто». */
	private void scheduleConversion() {
		if (autoConvertToggle == null || !autoConvertToggle.isSelected()) {
			return;
		}

		if (conversionDebounceTimer != null) {
			conversionDebounceTimer.stop();
		}

		conversionDebounceTimer = new Timer(400, e -> startConversion());
		conversionDebounceTimer.setRepeats(false);
		conversionDebounceTimer.start();
	}

	/** Псевдоним для единообразия вызовов из алгоритма/размера. */
	private void scheduleConversionIfAuto() {
		scheduleConversion();
	}

	private void startConversion() {
		if (activeConversionWorker != null && !activeConversionWorker.isDone()) {
			activeConversionWorker.cancel(true);
		}

		File imageFile = resolveImageFile();
		File blocksFile = resolveBlocksFile();

		if (imageFile == null || blocksFile == null) {
			return;
		}

		String version = (String) versionCombo.getSelectedItem();
		int mapWidth = (int) widthSpinner.getValue();
		int mapHeight = (int) heightSpinner.getValue();
		Ditherer.Algorithm algorithm = (Ditherer.Algorithm) algorithmCombo.getSelectedItem();

		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			onConversionError(e.getMessage());
			return;
		}

		closePreviousDitherer();
		setConvertingState(true);
		log(Lang.t("log.dither_start", imageFile.getName(), mapWidth, mapHeight));

		activeConversionWorker = new ConversionWorker(
			paletteJson,
			imageFile,
			blocksFile,
			mapWidth,
			mapHeight,
			algorithm,
			buildAdjustments(),
			buildDitherSettingsFromUi(),
			buildCropSettingsFromUi(),
			this::log,
			this::onDitheringSuccess,
			this::onConversionError
		);

		activeConversionWorker.execute();
	}

	private String loadPaletteJson(String version) throws Exception {
		String cached = paletteCache.get(version);

		if (cached != null) {
			return cached;
		}

		String resourcePath = "versions/" + version + ".zip";
		String jsonEntry = version + ".json";

		try (InputStream raw = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (raw == null) {
				throw new RuntimeException("Версия '%s' не найдена в ресурсах!".formatted(version));
			}

			try (ZipInputStream zip = new ZipInputStream(raw)) {
				ZipEntry entry;

				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().equals(jsonEntry)) {
						String json = new String(zip.readAllBytes());
						paletteCache.put(version, json);
						return json;
					}

					zip.closeEntry();
				}
			}
		}

		throw new RuntimeException("Файл %s не найден в архиве версии %s!".formatted(jsonEntry, version));
	}

	private void startExport() {
		if (lastDitherer == null) {
			return;
		}

		if (activeExportWorker != null && !activeExportWorker.isDone()) {
			return;
		}

		File outDir = resolveOutDir();

		if (outDir == null) {
			return;
		}

		int mapWidth = (int) widthSpinner.getValue();
		int mapHeight = (int) heightSpinner.getValue();

		setExportingState(true);
		log(Lang.t("log.export_start", outDir.getAbsolutePath()));

		String supportBlockId = supportBlockField.getText().isBlank()
			? DEFAULT_SUPPORT_BLOCK
			: supportBlockField.getText().strip();

		activeExportWorker = new ExportWorker(
			lastDitherer,
			outDir,
			mapWidth,
			mapHeight,
			supportBlockId,
			this::log,
			this::onExportSuccess,
			this::onExportError
		);

		activeExportWorker.execute();
	}

	private void savePreview() {
		if (lastDitherer == null) {
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(Lang.t("dialog.save_preview"));
		chooser.setFileFilter(new FileNameExtensionFilter(Lang.t("filter.png"), "png"));
		chooser.setSelectedFile(new File("preview.png"));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();

		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getAbsolutePath() + ".png");
		}

		try {
			BufferedImage preview = lastDitherer.createPreview();
			ImageIO.write(preview, "PNG", file);
			log(Lang.t("log.preview_saved", file.getAbsolutePath()));
		} catch (IOException e) {
			showError(Lang.t("error.preview_save_failed", e.getMessage()));
		}
	}

	private void openSettings() {
		SettingsDialog dialog = new SettingsDialog(this);

		if (dialog.isConfirmed()) {
			Lang.load(AppPreferences.loadLocale("ru_ru"));
			savePreferences();
			dispose();
			new MainWindow();
		}
	}

	private void onDitheringSuccess(Ditherer ditherer) {
		lastDitherer = ditherer;
		resultPreview.setImage(ditherer.createPreview());
		resultPreview.setMapCount((int) widthSpinner.getValue(), (int) heightSpinner.getValue());

		// Дизеренное изображение имеет размер mapW*128 × mapH*128 — точно пропорции сетки.
		// Растягиваем его на всю сетку без полей (stretch, не fit).
		javax.swing.SwingUtilities.invokeLater(() -> resultPreview.resetDisplayOffsetStretch());

		setConvertingState(false);
		progressBar.setString(Lang.t("progress.dither_done", ditherer.getDitherTime()));
		progressBar.setForeground(SUCCESS);
		exportButton.setEnabled(true);
		savePreviewButton.setEnabled(true);
		log(Lang.t("log.dither_done", ditherer.getDitherTime()));
	}

	private void onConversionError(String message) {
		setConvertingState(false);
		progressBar.setString(Lang.t("progress.dither_error"));
		progressBar.setForeground(ERROR);
		log(Lang.t("log.error", message));
		showError(Lang.t("error.dither", message));
	}

	private void onExportSuccess() {
		setExportingState(false);
		progressBar.setString(Lang.t("progress.export_done"));
		progressBar.setForeground(SUCCESS);
		log(Lang.t("log.export_done", outPathField.getText()));
	}

	private void onExportError(String message) {
		setExportingState(false);
		progressBar.setString(Lang.t("progress.export_error"));
		progressBar.setForeground(ERROR);
		log(Lang.t("log.export_error", message));
		showError(Lang.t("error.export", message));
	}

	private void setConvertingState(boolean converting) {
		convertButton.setEnabled(!converting);
		progressBar.setIndeterminate(converting);

		if (converting) {
			progressBar.setString(Lang.t("progress.dithering"));
			progressBar.setForeground(ACCENT);
			exportButton.setEnabled(false);
			savePreviewButton.setEnabled(false);
		}
	}

	private void setExportingState(boolean exporting) {
		exportButton.setEnabled(!exporting);
		convertButton.setEnabled(!exporting);
		progressBar.setIndeterminate(exporting);

		if (exporting) {
			progressBar.setString(Lang.t("progress.exporting"));
			progressBar.setForeground(WARN);
		}
	}

	private void closePreviousDitherer() {
		if (lastDitherer == null) {
			return;
		}

		lastDitherer.close();
		lastDitherer = null;
		exportButton.setEnabled(false);
		savePreviewButton.setEnabled(false);
	}

	private void restorePreferences() {
		String savedVersion = AppPreferences.loadVersion(null);

		if (savedVersion != null) {
			versionCombo.setSelectedItem(savedVersion);
		}

		widthSpinner.setValue(AppPreferences.loadMapWidth(1));
		heightSpinner.setValue(AppPreferences.loadMapHeight(1));

		String savedAlgorithm = AppPreferences.loadAlgorithm(null);

		if (savedAlgorithm != null) {
			try {
				algorithmCombo.setSelectedItem(Ditherer.Algorithm.valueOf(savedAlgorithm));
			} catch (IllegalArgumentException ignored) {
				// неизвестный алгоритм — оставляем дефолт
			}
		}

		// Авто-режим восстанавливаем ДО слайдеров, чтобы ChangeListeners не триггерили конвертацию
		autoConvertToggle.setSelected(AppPreferences.loadAutoConvert(false));

		DitherSettings savedDither = AppPreferences.loadDitherSettings();

		if (errorRateSlider != null) {
			errorRateSlider.setValue((int) (savedDither.errorDiffusionRate() * 100));
		}

		if (noiseLevelSlider != null) {
			noiseLevelSlider.setValue((int) (savedDither.noiseLevel() * 100));
		}

		ImageAdjustments defaults = ImageAdjustments.defaults();
		brightnessSlider.setValue(AppPreferences.loadBrightness(defaults.brightness()));
		contrastSlider.setValue(AppPreferences.loadContrast(defaults.contrast()));
		saturationSlider.setValue(AppPreferences.loadSaturation(defaults.saturation()));
		gammaSlider.setValue(AppPreferences.loadGamma(defaults.gamma()));
		hueSlider.setValue(AppPreferences.loadHue(defaults.hue()));

		String imagePath = AppPreferences.loadImagePath();

		if (!imagePath.isBlank()) {
			imagePathField.setText(imagePath);
			File imageFile = new File(imagePath);

			if (imageFile.exists() && imageFile.isFile()) {
				selectedImageFile = imageFile;

				try {
					rawSourceImage = javax.imageio.ImageIO.read(imageFile);
					scheduleSourcePreview();
				} catch (java.io.IOException ignored) {
					// не критично — просто не показываем превью
				}
			}
		}

		String blocksPath = AppPreferences.loadBlocksPath();

		if (!blocksPath.isBlank()) {
			blocksPathField.setText(blocksPath);
			File blocksFile = new File(blocksPath);

			if (blocksFile.exists() && blocksFile.isFile()) {
				loadBlocksFromFile(blocksFile);
			}
		}

		outPathField.setText(AppPreferences.loadOutPath("./rendered"));
		supportBlockField.setText(AppPreferences.loadSupportBlock(DEFAULT_SUPPORT_BLOCK));
	}

	private void savePreferences() {
		AppPreferences.saveVersion((String) versionCombo.getSelectedItem());
		AppPreferences.saveMapWidth((int) widthSpinner.getValue());
		AppPreferences.saveMapHeight((int) heightSpinner.getValue());
		AppPreferences.saveAlgorithm(((Ditherer.Algorithm) algorithmCombo.getSelectedItem()).name());
		AppPreferences.saveImagePath(imagePathField.getText().strip());
		AppPreferences.saveBlocksPath(blocksPathField.getText().strip());
		AppPreferences.saveOutPath(outPathField.getText().strip());
		AppPreferences.saveSupportBlock(supportBlockField.getText().strip());
		AppPreferences.saveBrightness(brightnessSlider.getValue());
		AppPreferences.saveContrast(contrastSlider.getValue());
		AppPreferences.saveSaturation(saturationSlider.getValue());
		AppPreferences.saveGamma(gammaSlider.getValue());
		AppPreferences.saveHue(hueSlider.getValue());
		AppPreferences.saveAutoConvert(autoConvertToggle.isSelected());
		AppPreferences.saveDitherSettings(buildDitherSettingsFromUi());
	}

	private void syncSourcePreviewMapCount() {
		if (sourcePreview == null || widthSpinner == null || heightSpinner == null) {
			return;
		}

		sourcePreview.setMapCount(
			(int) widthSpinner.getValue(),
			(int) heightSpinner.getValue()
		);
	}

	private void autoFitMapCount() {
		if (rawSourceImage == null) {
			return;
		}

		int maps = (int) Math.ceil(rawSourceImage.getWidth() / 128.0);
		int mapsH = (int) Math.ceil(rawSourceImage.getHeight() / 128.0);

		widthSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, maps)));
		heightSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, mapsH)));
	}

	private CropSettings buildCropSettingsFromUi() {
		int mapWidth = (int) widthSpinner.getValue();
		int mapHeight = (int) heightSpinner.getValue();
		int targetW = mapWidth * 128;
		int targetH = mapHeight * 128;
		return sourcePreview.buildCropSettings(targetW, targetH);
	}

	private DitherSettings buildDitherSettingsFromUi() {
		double errorRate = errorRateSlider != null
				? errorRateSlider.getValue() / 100.0
				: DitherSettings.defaults().errorDiffusionRate();
		double noiseLevel = noiseLevelSlider != null
				? noiseLevelSlider.getValue() / 100.0
				: DitherSettings.defaults().noiseLevel();
		return new DitherSettings(errorRate, noiseLevel);
	}

	private void tryAutoLoadBlocks() {
		if (!enabledBlocks.isEmpty()) {
			return;
		}

		File defaultBlocks = new File("./blocks.txt");

		if (defaultBlocks.exists() && defaultBlocks.isFile()) {
			loadBlocksFromFile(defaultBlocks);
		}
	}

	private void loadBlocksFromFile(File file) {
		try {
			String content = Files.readString(file.toPath());
			enabledBlocks = new HashSet<>();

			for (String line : content.split("\n")) {
				String trimmed = line.strip();

				if (!trimmed.isBlank()) {
					enabledBlocks.add(trimmed);
				}
			}

			blocksPathField.setText(file.getAbsolutePath());
			blocksCountLabel.setText(Lang.t("label.blocks_count", enabledBlocks.size()));
			blocksCountLabel.setForeground(SUCCESS);
			log(Lang.t("log.blocks_loaded", file.getName(), enabledBlocks.size()));
			} catch (IOException e) {
				showError(Lang.t("error.blocks_load_failed", e.getMessage()));
			}
		}
	
		private void openBlockPicker() {
			syncBlocksFromFieldIfNeeded();
	
			String version = (String) versionCombo.getSelectedItem();
			File targetFile = resolveBlocksTargetFile();
	
			BlockPickerDialog dialog = new BlockPickerDialog(this, version, targetFile, enabledBlocks);
	
			if (dialog.isConfirmed()) {
				enabledBlocks = new HashSet<>(dialog.getEnabledBlocks());
				blocksPathField.setText(targetFile.getAbsolutePath());
				blocksCountLabel.setText(Lang.t("label.blocks_count", enabledBlocks.size()));
				blocksCountLabel.setForeground(SUCCESS);
				log(Lang.t("log.blocks_picked", enabledBlocks.size(), targetFile.getName()));
			}
		}
	
		private void syncBlocksFromFieldIfNeeded() {
			String path = blocksPathField.getText().strip();
	
			if (path.isBlank() || !enabledBlocks.isEmpty()) {
				return;
			}
	
			File file = new File(path);
	
			if (file.exists() && file.isFile()) {
				loadBlocksFromFile(file);
			}
		}
	
		private File resolveBlocksTargetFile() {
			String path = blocksPathField.getText().strip();
	
			if (!path.isBlank()) {
				return new File(path);
			}
	
			return new File("./blocks.txt");
		}
	
		private void chooseImage() {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(Lang.t("dialog.choose_image"));
			chooser.setFileFilter(new FileNameExtensionFilter(
				Lang.t("filter.images"), "png", "jpg", "jpeg", "bmp", "gif"
			));
	
			if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
				return;
			}
	
			loadImageFile(chooser.getSelectedFile());
		}
	
		private void chooseBlocks() {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(Lang.t("dialog.choose_blocks"));
			chooser.setFileFilter(new FileNameExtensionFilter(Lang.t("filter.txt"), "txt"));
	
			if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
				return;
			}
	
			loadBlocksFromFile(chooser.getSelectedFile());
		}
	
		private void chooseOutDir() {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(Lang.t("dialog.choose_outdir"));
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	
			if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
				return;
			}
	
			outPathField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	
		private void loadImageFile(File file) {
			try {
				BufferedImage image = ImageIO.read(file);

				if (image == null) {
					showError(Lang.t("error.image_load_failed", file.getName()));
					return;
				}

				selectedImageFile = file;
				rawSourceImage = image;
				imagePathField.setText(file.getAbsolutePath());

				resetSourceViewOnNextImage = true;

				if (resultPreview != null) {
					resultPreview.clear();
				}

				scheduleSourcePreview();
				log(Lang.t("log.image_loaded", file.getName(), image.getWidth(), image.getHeight()));
			} catch (IOException e) {
				showError(Lang.t("error.image_load_failed", e.getMessage()));
			}
		}
	
		private File resolveImageFile() {
			if (selectedImageFile != null && selectedImageFile.exists()) {
				return selectedImageFile;
			}
	
			String path = imagePathField.getText().strip();
	
			if (path.isBlank()) {
				showError(Lang.t("error.no_image"));
				return null;
			}
	
			File file = new File(path);
	
			if (!file.exists() || !file.isFile()) {
				showError(Lang.t("error.image_not_found", path));
				return null;
			}
	
			selectedImageFile = file;
	
			return file;
		}
	
		private File resolveBlocksFile() {
			String path = blocksPathField.getText().strip();
	
			if (!path.isBlank()) {
				File file = new File(path);
	
				if (file.exists() && file.isFile()) {
					return file;
				}
	
				showError(Lang.t("error.blocks_not_found", path));
				return null;
			}
	
			File defaultFile = new File("./blocks.txt");
	
			if (defaultFile.exists()) {
				return defaultFile;
			}
	
			showError(Lang.t("error.blocks_not_found", "./blocks.txt"));
			return null;
		}
	
		private File resolveOutDir() {
			String path = outPathField.getText().strip();
			File dir = path.isBlank() ? new File("./rendered") : new File(path);
	
			if (dir.exists()) {
				return dir;
			}
	
			if (!dir.mkdirs()) {
				showError(Lang.t("error.outdir_failed", dir.getAbsolutePath()));
				return null;
			}
	
			return dir;
		}
	
		private void setupImageDropTarget(ImagePreviewPanel panel) {
			new DropTarget(panel, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
				@Override
				public void drop(DropTargetDropEvent event) {
					try {
						event.acceptDrop(DnDConstants.ACTION_COPY);
						List<?> files = (List<?>) event.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
	
						if (!files.isEmpty()) {
							loadImageFile((File) files.get(0));
						}
					} catch (Exception e) {
						showError(Lang.t("error.drop_failed", e.getMessage()));
					}
				}
			});
		}

		private void pasteImageFromClipboard() {
			try {
				var transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);

				if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
					return;
				}

				BufferedImage image = (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor);

				if (image == null) {
					return;
				}

				File tempFile = Files.createTempFile("mapart_paste_", ".png").toFile();
				tempFile.deleteOnExit();
				ImageIO.write(image, "png", tempFile);
				loadImageFile(tempFile);
				log(Lang.t("log.clipboard_paste", image.getWidth(), image.getHeight()));
			} catch (Exception e) {
				showError(Lang.t("error.image_load_failed", e.getMessage()));
			}
		}

		private void log(String message) {
			logArea.append(message + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		}
	
		private void showError(String message) {
			JOptionPane.showMessageDialog(this, message, Lang.t("dialog.error_title"), JOptionPane.ERROR_MESSAGE);
		}
	
		private String[] loadVersions() {
			try (InputStream stream = getClass().getClassLoader().getResourceAsStream("versions/versions.txt")) {
				if (stream == null) {
					return new String[]{"1.21.11"};
				}
	
				String content = new String(stream.readAllBytes());
				String[] versions = content.lines()
					.map(String::strip)
					.filter(l -> !l.isBlank())
					.toArray(String[]::new);
	
				return versions.length > 0 ? versions : new String[]{"1.21.11"};
			} catch (IOException e) {
				return new String[]{"1.21.11"};
			}
		}
	
		// ── UI-фабрики ─────────────────────────────────────────────────────────────
	
		private JPanel buildCard() {
			JPanel card = new JPanel() {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(CARD);
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS * 2, CARD_RADIUS * 2);
					g2.setColor(BORDER);
					g2.setStroke(new BasicStroke(1f));
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS * 2, CARD_RADIUS * 2);
					g2.dispose();
				}
			};
	
			card.setOpaque(false);
			card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
	
			return card;
		}
	
		private JLabel buildSectionLabel(String text) {
			JLabel label = new JLabel(text.toUpperCase());
			label.setForeground(TEXT_DIM);
			label.setFont(new Font("SansSerif", Font.BOLD, 10));
			label.setAlignmentX(LEFT_ALIGNMENT);
	
			return label;
		}
	
		private JLabel dimLabel(String text) {
			JLabel label = new JLabel(text);
			label.setForeground(TEXT_DIM);
			label.setFont(new Font("SansSerif", Font.PLAIN, 12));
	
			return label;
		}
	
		private JTextField buildTextField(String placeholder) {
			JTextField field = new JTextField() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
	
					if (getText().isEmpty()) {
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setColor(TEXT_DIM);
						g2.setFont(getFont().deriveFont(Font.ITALIC));
						Insets insets = getInsets();
						g2.drawString(placeholder, insets.left, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 1);
						g2.dispose();
					}
				}
			};
	
			field.setBackground(INPUT);
			field.setForeground(TEXT);
			field.setCaretColor(ACCENT);
			field.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(5, 8, 5, 8)
			));
			field.setFont(new Font("SansSerif", Font.PLAIN, 12));
	
			return field;
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
			btn.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
	
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
			btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
	
			return btn;
		}
	
		private JButton buildIconButton(String text) {
			JButton btn = new JButton(text) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	
					if (getModel().isRollover()) {
						g2.setColor(new Color(255, 255, 255, 20));
						g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
					}
	
					g2.dispose();
					super.paintComponent(g);
				}
			};
	
			btn.setForeground(TEXT_DIM);
			btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
			btn.setFocusPainted(false);
			btn.setContentAreaFilled(false);
			btn.setBorderPainted(false);
			btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			addHoverEffect(btn);
	
			return btn;
		}
	
		private void stylePreviewPanel(ImagePreviewPanel panel) {
			panel.setBackground(CARD);
			panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(4, 4, 4, 4)
			));
		}
	
		private void addHoverEffect(JButton btn) {
			btn.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					btn.setForeground(TEXT);
				}
	
				@Override
				public void mouseExited(MouseEvent e) {
					btn.setForeground(TEXT_DIM);
				}
			});
		}
	}

class ScrollablePanel extends JPanel implements Scrollable {

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return 12;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return 60;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}
