package org.duollectis.mapart.tools.gui;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.Brightness;
import org.duollectis.mapart.tools.converter.CropSettings;
import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.DitherSettings;
import org.duollectis.mapart.tools.converter.ImageConverter;
import org.duollectis.mapart.tools.converter.PaletteEntry;
import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
	private SupportBlockSettings supportSettings;
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
	private ModernComboBox<SchematicFormat> formatCombo;
	private JButton convertButton;
	private JButton exportButton;
	private JButton savePreviewButton;
	private JButton blockListButton;
	private JButton pickBlocksButton;
	private JLabel blocksCountLabel;
	private JProgressBar progressBar;
	private JTextArea logArea;
	private ImagePreviewPanel sourcePreview;
	private ImagePreviewPanel resultPreview;
	private ModernCheckBox showGridCheckBox;
	private ModernSpinner gridWidthSpinner;
	private JButton gridBgColorButton;
	private JButton importButton;

	private File selectedImageFile;
	private BufferedImage rawSourceImage;
	private ConversionWorker activeConversionWorker;
	private ExportWorker activeExportWorker;
	private ImportWorker activeImportWorker;
	private Ditherer lastDitherer;
	private SchematicImportResult lastImportResult;
	private BlockListDialog activeBlockListDialog;
	private Set<String> enabledBlocks = new HashSet<>();
	private Map<String, WeightedSelector<BlockData>> blockSelectors = new HashMap<>();
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

		JButton halvBtn = buildIconButton("÷2", 11, new Insets(2, 5, 2, 5));
		halvBtn.setToolTipText(Lang.t("btn.halve_maps"));
		halvBtn.addActionListener(e -> scaleMapCount(0.5));

		JButton doubleBtn = buildIconButton("×2", 11, new Insets(2, 5, 2, 5));
		doubleBtn.setToolTipText(Lang.t("btn.double_maps"));
		doubleBtn.addActionListener(e -> scaleMapCount(2.0));

		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;

		gbc.gridx = 4;
		gbc.insets = new Insets(0, 6, 0, 0);
		row.add(halvBtn, gbc);

		gbc.gridx = 5;
		gbc.insets = new Insets(0, 2, 0, 0);
		row.add(doubleBtn, gbc);

		gbc.gridx = 6;
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

		addSliderRow(
				grid,
				gbc,
				0,
				Lang.t("adjust.brightness"),
				brightnessSlider,
				brightnessLabel,
				defaults.brightness(),
				false
		);
		addSliderRow(
				grid,
				gbc,
				1,
				Lang.t("adjust.contrast"),
				contrastSlider,
				contrastLabel,
				defaults.contrast(),
				false
		);
		addSliderRow(
				grid,
				gbc,
				2,
				Lang.t("adjust.saturation"),
				saturationSlider,
				saturationLabel,
				defaults.saturation(),
				false
		);
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

		addSliderRow(
				grid,
				gbc,
				0,
				Lang.t("dither.error_rate"),
				errorRateSlider,
				errorRateLabel,
				defaultErrorRate,
				true
		);
		addSliderRow(
				grid,
				gbc,
				1,
				Lang.t("dither.noise_level"),
				noiseLevelSlider,
				noiseLevelLabel,
				defaultNoiseLevel,
				true
		);

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

		pickBlocksButton =
				buildAccentButton(Lang.t("btn.pick_blocks"), new Color(34, 85, 34), new Color(100, 210, 130));
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

		formatCombo = new ModernComboBox<>(SchematicFormat.values());
		formatCombo.addActionListener(e -> syncExportButtonLabel());

		exportButton = buildAccentButton(Lang.t("btn.export_nbt"), new Color(60, 45, 10), WARN);
		exportButton.setEnabled(false);
		exportButton.addActionListener(e -> startExport());

		savePreviewButton = buildAccentButton(Lang.t("btn.save_preview"), new Color(20, 50, 20), SUCCESS);
		savePreviewButton.setEnabled(false);
		savePreviewButton.addActionListener(e -> savePreview());

		blockListButton = buildAccentButton(Lang.t("btn.block_list"), new Color(20, 35, 60), new Color(130, 180, 240));
		blockListButton.setEnabled(false);
		blockListButton.addActionListener(e -> openBlockList());

		importButton = buildAccentButton(Lang.t("btn.import_schematic"), new Color(35, 20, 55), new Color(180, 140, 240));
		importButton.addActionListener(e -> startImport());

		convertButton.setAlignmentX(LEFT_ALIGNMENT);
		exportButton.setAlignmentX(LEFT_ALIGNMENT);
		savePreviewButton.setAlignmentX(LEFT_ALIGNMENT);
		blockListButton.setAlignmentX(LEFT_ALIGNMENT);
		importButton.setAlignmentX(LEFT_ALIGNMENT);

		convertButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, convertButton.getPreferredSize().height + 4));
		exportButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, exportButton.getPreferredSize().height + 4));
		savePreviewButton.setMaximumSize(new Dimension(
				Integer.MAX_VALUE,
				savePreviewButton.getPreferredSize().height + 4
		));
		blockListButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, blockListButton.getPreferredSize().height + 4));
		importButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, importButton.getPreferredSize().height + 4));
		formatCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JLabel formatLabel = dimLabel(Lang.t("label.format"));

		JPanel formatRow = new JPanel(new BorderLayout(6, 0));
		formatRow.setOpaque(false);
		formatRow.add(formatLabel, BorderLayout.WEST);
		formatRow.add(formatCombo, BorderLayout.CENTER);
		formatRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		formatRow.setAlignmentX(LEFT_ALIGNMENT);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.add(convertButton);
		panel.add(Box.createVerticalStrut(6));
		panel.add(formatRow);
		panel.add(Box.createVerticalStrut(4));
		panel.add(exportButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(savePreviewButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(blockListButton);
		panel.add(Box.createVerticalStrut(8));
		panel.add(importButton);

		return panel;
	}

	private void syncExportButtonLabel() {
		if (exportButton == null || formatCombo == null) {
			return;
		}

		SchematicFormat selected = (SchematicFormat) formatCombo.getSelectedItem();
		String key = selected == SchematicFormat.LITEMATIC ? "btn.export_litematic" : "btn.export_nbt";
		exportButton.setText(Lang.t(key));
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

		JButton fitBtn = buildIconButton(Lang.t("preview.btn_fit"), 11, new Insets(2, 8, 2, 8));
		fitBtn.setToolTipText(Lang.t("preview.reset_view"));
		fitBtn.addActionListener(e -> sourcePreview.resetDisplayOffset());

		JButton coverBtn = buildIconButton(Lang.t("preview.btn_cover"), 11, new Insets(2, 8, 2, 8));
		coverBtn.setToolTipText(Lang.t("preview.cover_view"));
		coverBtn.addActionListener(e -> sourcePreview.resetDisplayOffsetCover());

		JPanel sourceViewBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		sourceViewBtns.setOpaque(false);
		sourceViewBtns.add(fitBtn);
		sourceViewBtns.add(coverBtn);

		gbc.gridx = 0;
		panel.add(wrapPreviewWithButton(sourcePreview, Lang.t("preview.source"), null, null, sourceViewBtns), gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, 5, 0, 0);
		panel.add(
				wrapPreviewWithButton(
						resultPreview,
						Lang.t("preview.result"),
						showGridCheckBox,
						gridWidthSpinner,
						null,
						gridBgColorButton
				), gbc
		);

		return panel;
	}

	private JPanel wrapPreviewWithButton(
			ImagePreviewPanel preview,
			String windowTitle,
			ModernCheckBox checkBox,
			ModernSpinner gridSpinner
	) {
		return wrapPreviewWithButton(preview, windowTitle, checkBox, gridSpinner, null, new JButton[0]);
	}

	private JPanel wrapPreviewWithButton(
			ImagePreviewPanel preview,
			String windowTitle,
			ModernCheckBox checkBox,
			ModernSpinner gridSpinner,
			JComponent westComponent,
			JButton... extraButtons
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
		else if (westComponent != null) {
			footer.add(westComponent, BorderLayout.WEST);
		}

		if (gridSpinner != null || extraButtons.length > 0) {
			JPanel eastGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			eastGroup.setOpaque(false);

			for (JButton btn : extraButtons) {
				if (btn != null) {
					eastGroup.add(btn);
				}
			}

			if (gridSpinner != null) {
				JLabel gridWidthLabel = dimLabel(Lang.t("preview.grid_width"));
				eastGroup.add(gridWidthLabel);
				eastGroup.add(gridSpinner);
			}

			footer.add(eastGroup, BorderLayout.EAST);
		}

		if (checkBox == null && westComponent == null && gridSpinner == null && extraButtons.length == 0) {
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
				}
				else if (getValue() > getMinimum()) {
					int fillW = (int) ((double) (getValue() - getMinimum())
							/ (getMaximum() - getMinimum()) * getWidth()
					);
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
				}
				catch (Exception ignored) {
				}

				sourcePreviewRunning = false;

				if (sourcePreviewPending) {
					runSourcePreviewWorker();
				}
			}
		}.execute();
	}

	/**
	 * Запускает конвертацию с debounce 400ms только если включена кнопка «Авто».
	 */
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

	/**
	 * Псевдоним для единообразия вызовов из алгоритма/размера.
	 */
	private void scheduleConversionIfAuto() {
		scheduleConversion();
	}

	private void startConversion() {
		if (enabledBlocks.isEmpty()) {
			showError(Lang.t("error.no_blocks_selected"));
			return;
		}

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
		}
		catch (Exception e) {
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
				blockSelectors,
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

		SupportBlockSettings effectiveSupport = supportSettings != null
		                                        ? supportSettings
		                                        : SupportBlockSettings.single(DEFAULT_SUPPORT_BLOCK);

		SchematicFormat format = formatCombo != null
		                         ? (SchematicFormat) formatCombo.getSelectedItem()
		                         : SchematicFormat.NBT;

		activeExportWorker = new ExportWorker(
				lastDitherer,
				outDir,
				mapWidth,
				mapHeight,
				effectiveSupport,
				format,
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

		FileDialog dialog = new FileDialog(this, Lang.t("dialog.save_preview"), FileDialog.SAVE);
		dialog.setFile("preview.png");
		dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		File file = new File(dialog.getDirectory(), dialog.getFile());

		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getAbsolutePath() + ".png");
		}

		try {
			BufferedImage preview = lastDitherer.createPreview();
			ImageIO.write(preview, "PNG", file);
			log(Lang.t("log.preview_saved", file.getAbsolutePath()));
		}
		catch (IOException e) {
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
		blockListButton.setEnabled(true);
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

	private void startImport() {
		if (activeImportWorker != null && !activeImportWorker.isDone()) {
			return;
		}

		FileDialog dialog = new FileDialog(this, Lang.t("dialog.choose_schematic"), FileDialog.LOAD);
		dialog.setMultipleMode(true);
		dialog.setFilenameFilter((dir, name) -> {
			String lower = name.toLowerCase();
			return lower.endsWith(".nbt") || lower.endsWith(".litematic");
		});
		dialog.setVisible(true);

		File[] selected = dialog.getFiles();

		if (selected == null || selected.length == 0) {
			return;
		}

		List<File> schematicFiles = Arrays.asList(selected);

		int[] grid = detectGrid(schematicFiles);
		int gridWidth = grid[0];
		int gridHeight = grid[1];

		String version = (String) versionCombo.getSelectedItem();
		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			showError(Lang.t("error.import_palette_failed", e.getMessage()));
			return;
		}

		ImportOptions options = showImportOptionsDialog();

		String firstFileName = schematicFiles.size() == 1
				? schematicFiles.get(0).getName()
				: Lang.t("log.import_start_multi", schematicFiles.size());

		setImportingState(true);
		log(Lang.t("log.import_start", firstFileName));

		activeImportWorker = new ImportWorker(
				schematicFiles,
				paletteJson,
				gridWidth,
				gridHeight,
				options.xyOrder(),
				msg -> log(Lang.t("log.import_progress", msg)),
				(result, preview) -> onImportSuccess(result, preview, options.addToBlocks()),
				this::onImportError
		);

		activeImportWorker.execute();
	}

	private int[] detectGrid(List<File> files) {
		if (files.size() == 1) {
			return new int[]{1, 1};
		}

		java.util.regex.Pattern mapPattern = java.util.regex.Pattern.compile("map_(\\d+)_(\\d+)(?:\\..+)?$");
		int maxX = 0;
		int maxY = 0;

		for (File file : files) {
			java.util.regex.Matcher matcher = mapPattern.matcher(file.getName());

			if (matcher.find()) {
				maxX = Math.max(maxX, Integer.parseInt(matcher.group(1)));
				maxY = Math.max(maxY, Integer.parseInt(matcher.group(2)));
			}
		}

		if (maxX > 0 && maxY > 0) {
			return new int[]{maxX, maxY};
		}

		int autoWidth = (int) Math.ceil(Math.sqrt(files.size()));
		int autoHeight = (int) Math.ceil((double) files.size() / autoWidth);

		return new int[]{autoWidth, autoHeight};
	}

	private ImportOptions showImportOptionsDialog() {
		ModernCheckBox addBlocksCheckBox = new ModernCheckBox(Lang.t("import.add_to_palette"));
		addBlocksCheckBox.setSelected(false);
		addBlocksCheckBox.setForeground(TEXT);
		addBlocksCheckBox.setOpaque(false);

		JRadioButton xyButton = new JRadioButton(Lang.t("import.order_xy"));
		JRadioButton yxButton = new JRadioButton(Lang.t("import.order_yx"));
		xyButton.setSelected(true);
		xyButton.setOpaque(false);
		yxButton.setOpaque(false);
		xyButton.setForeground(TEXT);
		yxButton.setForeground(TEXT);

		ButtonGroup orderGroup = new ButtonGroup();
		orderGroup.add(xyButton);
		orderGroup.add(yxButton);

		JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		orderRow.setOpaque(false);
		orderRow.add(xyButton);
		orderRow.add(yxButton);

		JLabel orderLabel = new JLabel(Lang.t("import.order_label"));
		orderLabel.setForeground(TEXT_DIM);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.add(new JLabel(Lang.t("import.options_hint")));
		panel.add(Box.createVerticalStrut(6));
		panel.add(addBlocksCheckBox);
		panel.add(Box.createVerticalStrut(8));
		panel.add(orderLabel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(orderRow);

		JOptionPane.showMessageDialog(
			this,
			panel,
			Lang.t("import.options_title"),
			JOptionPane.PLAIN_MESSAGE
		);

		return new ImportOptions(addBlocksCheckBox.isSelected(), xyButton.isSelected());
	}

	private record ImportOptions(boolean addToBlocks, boolean xyOrder) {}

	private void onImportSuccess(ImportWorker.Result result, BufferedImage preview, boolean addToBlocks) {
		setImportingState(false);

		lastImportResult = result.importResult();

		sourcePreview.setImage(preview);
		resetSourceViewOnNextImage = true;
		sourcePreview.resetDisplayOffset();

		widthSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, lastImportResult.mapWidth())));
		heightSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, lastImportResult.mapHeight())));

		saveImportPreviewAsTemp(preview);

		if (addToBlocks) {
			enabledBlocks.addAll(lastImportResult.blockIds());
			syncBlocksFromFieldIfNeeded();
		}

		progressBar.setString(Lang.t("progress.import_done"));
		progressBar.setForeground(SUCCESS);
		log(Lang.t(
			"log.import_done",
			lastImportResult.mapWidth(),
			lastImportResult.mapHeight(),
			lastImportResult.blockIds().size()
		));
	}

	private void saveImportPreviewAsTemp(BufferedImage preview) {
		try {
			File tempFile = Files.createTempFile("mapart_import_", ".png").toFile();
			tempFile.deleteOnExit();
			ImageIO.write(preview, "png", tempFile);

			selectedImageFile = tempFile;
			rawSourceImage = preview;
			imagePathField.setText(tempFile.getAbsolutePath());
		}
		catch (IOException e) {
			log(Lang.t("log.error", e.getMessage()));
		}
	}

	private void onImportError(String message) {
		setImportingState(false);
		progressBar.setString(Lang.t("progress.import_error"));
		progressBar.setForeground(ERROR);
		log(Lang.t("log.error", message));
		showError(Lang.t("error.import_failed", message));
	}

	private void setImportingState(boolean importing) {
		importButton.setEnabled(!importing);
		convertButton.setEnabled(!importing);
		progressBar.setIndeterminate(importing);

		if (importing) {
			progressBar.setString(Lang.t("progress.importing"));
			progressBar.setForeground(new Color(140, 100, 220));
		}
	}

	private void setConvertingState(boolean converting) {
		convertButton.setEnabled(!converting);
		progressBar.setIndeterminate(converting);

		if (converting) {
			progressBar.setString(Lang.t("progress.dithering"));
			progressBar.setForeground(ACCENT);
			exportButton.setEnabled(false);
			savePreviewButton.setEnabled(false);
			blockListButton.setEnabled(false);
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
		blockListButton.setEnabled(false);
	}

	private void openBlockList() {
		if (lastDitherer == null) {
			return;
		}

		String version = (String) versionCombo.getSelectedItem();
		String primarySupportId = (supportSettings != null && !supportSettings.isEmpty())
		                          ? supportSettings.getEntries().getFirst().blockId()
		                          : null;

		activeBlockListDialog = new BlockListDialog(
				this,
				lastDitherer.getUsedBlockCounts(),
				version,
				primarySupportId,
				lastDitherer.getSupportBlockCount(),
				this::removeBlockAndReconvert
		);
		// Диалог немодальный — очищаем ссылку при закрытии через WindowListener
		activeBlockListDialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				activeBlockListDialog = null;
			}
		});
	}

	/**
	 * Удаляет блок из активного набора, сохраняет обновлённый файл блоков
	 * и запускает реконвертацию. По завершении обновляет открытый диалог.
	 *
	 * @param blockId идентификатор блока для удаления (например "minecraft:stone")
	 */
	private void removeBlockAndReconvert(String blockId) {
		// Удаляем все uniqueKey, соответствующие данному getId():
		// точное совпадение (блок без свойств) или начинающиеся с "blockId_" (варианты с axis/facing/etc.)
		enabledBlocks.removeIf(key -> key.equals(blockId) || key.startsWith(blockId + "_"));
		blocksCountLabel.setText(Lang.t("label.blocks_count", enabledBlocks.size()));

		File blocksFile = resolveBlocksTargetFile();
		saveBlocksToFile(blocksFile);
		blocksPathField.setText(blocksFile.getAbsolutePath());

		log(Lang.t("log.block_removed", blockId));
		startConversionForBlockList();
	}

	private void saveBlocksToFile(File file) {
		try {
			String version = (String) versionCombo.getSelectedItem();
			Map<String, BlockData> paletteBlocks = parsePaletteBlocks(version);
			String content = buildBlocksFileContent(paletteBlocks);
			Files.writeString(file.toPath(), content);
		}
		catch (IOException e) {
			showError(Lang.t("error.blocks_save_failed", e.getMessage()));
		}
	}

	/**
	 * Строит содержимое файла блоков: каждая строка — uniqueKey блока,
	 * а если для него задан нестандартный вес — добавляется комментарий с процентом.
	 * Формат: {@code minecraft:stone # 80%} или {@code minecraft:stone_axis_y # 25%}
	 */
	private String buildBlocksFileContent(Map<String, BlockData> paletteBlocks) {
		StringBuilder sb = new StringBuilder();

		for (String uniqueKey : enabledBlocks) {
			sb.append(uniqueKey);

			BlockData block = paletteBlocks.get(uniqueKey);

			if (block != null) {
				String baseId = block.getId();
				WeightedSelector<BlockData> selector = blockSelectors.get(baseId);

				if (selector != null) {
					int percent = computeBlockPercent(block, selector);
					sb.append(" # ").append(percent).append('%');
				}
			}

			sb.append('\n');
		}

		return sb.isEmpty() ? "" : sb.substring(0, sb.length() - 1);
	}

	private int computeBlockPercent(BlockData block, WeightedSelector<BlockData> selector) {
		int totalWeight = selector.getEntries().stream().mapToInt(WeightedSelector.Entry::weight).sum();

		if (totalWeight == 0) {
			return 0;
		}

		return selector.getEntries().stream()
		               .filter(e -> e.value().getUniqueKey().equals(block.getUniqueKey()))
		               .mapToInt(WeightedSelector.Entry::weight)
		               .map(w -> (int) Math.round(w * 100.0 / totalWeight))
		               .findFirst()
		               .orElse(0);
	}

	private void startConversionForBlockList() {
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
		}
		catch (Exception e) {
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
				blockSelectors,
				this::log,
				this::onDitheringSuccessFromBlockList,
				this::onConversionError
		);

		activeConversionWorker.execute();
	}

	private void onDitheringSuccessFromBlockList(Ditherer ditherer) {
		onDitheringSuccess(ditherer);

		if (activeBlockListDialog != null && activeBlockListDialog.isVisible()) {
			activeBlockListDialog.refresh(ditherer.getUsedBlockCounts(), ditherer.getSupportBlockCount());
		}
	}

	/**
	 * Восстанавливает настройки из файла. После загрузки палитры фильтрует
	 * сохранённые блоки опоры — удаляет ID которых нет в текущей версии.
	 */
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
			}
			catch (IllegalArgumentException ignored) {
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
				}
				catch (java.io.IOException ignored) {
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

		SchematicFormat savedFormat = AppPreferences.loadSchematicFormat();

		if (formatCombo != null) {
			formatCombo.setSelectedItem(savedFormat);
			syncExportButtonLabel();
		}

		String version = (String) versionCombo.getSelectedItem();
		Map<String, BlockData> paletteBlocks = parsePaletteBlocks(version);

		SupportBlockSettings loaded = AppPreferences.loadSupportSettings(DEFAULT_SUPPORT_BLOCK);

		if (loaded != null) {
			Set<String> validSupportIds = paletteBlocks.values().stream()
			                                           .filter(b -> !b.isNeedSupport())
			                                           .map(BlockData::getId)
			                                           .collect(Collectors.toSet());
			supportSettings = loaded.filtered(validSupportIds);
		}

		blockSelectors = new HashMap<>(AppPreferences.loadBlockSelectors(paletteBlocks));
	}

	private Map<String, BlockData> parsePaletteBlocks(String version) {
		try {
			String paletteJson = loadPaletteJson(version);
			Map<Integer, List<BlockData>> parsed = JsonHelper.GSON.fromJson(
					paletteJson,
					new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
			);

			return parsed.values().stream()
			             .flatMap(List::stream)
			             .collect(Collectors.toMap(BlockData::getUniqueKey, b -> b, (a, b) -> a));
		}
		catch (Exception ignored) {
			return Map.of();
		}
	}

	private Set<String> loadValidSupportIds(String version) {
		return parsePaletteBlocks(version).values().stream()
		                                  .filter(b -> !b.isNeedSupport())
		                                  .map(BlockData::getId)
		                                  .collect(Collectors.toSet());
	}

	private void savePreferences() {
		AppPreferences.saveVersion((String) versionCombo.getSelectedItem());
		AppPreferences.saveMapWidth((int) widthSpinner.getValue());
		AppPreferences.saveMapHeight((int) heightSpinner.getValue());
		AppPreferences.saveAlgorithm(((Ditherer.Algorithm) algorithmCombo.getSelectedItem()).name());
		AppPreferences.saveImagePath(imagePathField.getText().strip());
		AppPreferences.saveBlocksPath(blocksPathField.getText().strip());
		AppPreferences.saveOutPath(outPathField.getText().strip());
		AppPreferences.saveSupportSettings(supportSettings);
		AppPreferences.saveBrightness(brightnessSlider.getValue());
		AppPreferences.saveContrast(contrastSlider.getValue());
		AppPreferences.saveSaturation(saturationSlider.getValue());
		AppPreferences.saveGamma(gammaSlider.getValue());
		AppPreferences.saveHue(hueSlider.getValue());
		AppPreferences.saveAutoConvert(autoConvertToggle.isSelected());
		AppPreferences.saveDitherSettings(buildDitherSettingsFromUi());
		AppPreferences.saveBlockSelectors(blockSelectors);

		if (formatCombo != null) {
			AppPreferences.saveSchematicFormat((SchematicFormat) formatCombo.getSelectedItem());
		}
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

	private void scaleMapCount(double factor) {
		int w = (int) Math.round((int) widthSpinner.getValue() * factor);
		int h = (int) Math.round((int) heightSpinner.getValue() * factor);
		widthSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, w)));
		heightSpinner.setValue(Math.max(SPINNER_MIN, Math.min(SPINNER_MAX, h)));
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

				if (trimmed.isBlank() || trimmed.startsWith("#")) {
					continue;
				}

				int commentIdx = trimmed.indexOf('#');
				String uniqueKey = commentIdx >= 0
				                   ? trimmed.substring(0, commentIdx).strip()
				                   : trimmed;

				if (!uniqueKey.isBlank()) {
					enabledBlocks.add(uniqueKey);
				}
			}

			blocksPathField.setText(file.getAbsolutePath());
			blocksCountLabel.setText(Lang.t("label.blocks_count", enabledBlocks.size()));
			blocksCountLabel.setForeground(SUCCESS);
			log(Lang.t("log.blocks_loaded", file.getName(), enabledBlocks.size()));
		}
		catch (IOException e) {
			showError(Lang.t("error.blocks_load_failed", e.getMessage()));
		}
	}

	private void openBlockPicker() {
		syncBlocksFromFieldIfNeeded();

		String version = (String) versionCombo.getSelectedItem();
		File targetFile = resolveBlocksTargetFile();

		BlockPickerDialog dialog = new BlockPickerDialog(
				this,
				version,
				targetFile,
				enabledBlocks,
				supportSettings,
				blockSelectors
		);

		if (dialog.isConfirmed()) {
			enabledBlocks = new HashSet<>(dialog.getEnabledBlocks());
			blockSelectors = new HashMap<>(dialog.getBlockSelectors());
			supportSettings = dialog.getSupportSettings();
			savePreferences();

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
		FileDialog dialog = new FileDialog(this, Lang.t("dialog.choose_image"), FileDialog.LOAD);
		dialog.setFilenameFilter((dir, name) -> {
			String lower = name.toLowerCase();
			return lower.endsWith(".png")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".bmp")
				|| lower.endsWith(".gif");
		});
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		loadImageFile(new File(dialog.getDirectory(), dialog.getFile()));
	}

	private void chooseBlocks() {
		FileDialog dialog = new FileDialog(this, Lang.t("dialog.choose_blocks"), FileDialog.LOAD);
		dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		loadBlocksFromFile(new File(dialog.getDirectory(), dialog.getFile()));
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
		}
		catch (IOException e) {
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
		new DropTarget(
				panel, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent event) {
				try {
					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> files = (List<?>) event.getTransferable()
					                               .getTransferData(DataFlavor.javaFileListFlavor);

					if (!files.isEmpty()) {
						loadImageFile((File) files.get(0));
					}
				}
				catch (Exception e) {
					showError(Lang.t("error.drop_failed", e.getMessage()));
				}
			}
		}
		);
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
		}
		catch (Exception e) {
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
		}
		catch (IOException e) {
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
