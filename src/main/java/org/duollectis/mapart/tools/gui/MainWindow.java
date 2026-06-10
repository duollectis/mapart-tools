package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.ColorMetric;
import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.converter.StaircaseMode;
import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainWindow extends JFrame {

	static final int WINDOW_WIDTH = 1140;
	static final int WINDOW_HEIGHT = 800;
	static final int SPINNER_MIN = 1;
	static final int SPINNER_MAX = 32;
	static final int CARD_RADIUS = 12;
	static final int SETTINGS_MIN_WIDTH = 350;
	static final int SETTINGS_MAX_WIDTH = 550;
	static final String DEFAULT_SUPPORT_BLOCK = "minecraft:stone";

	// ── Цвета (method-getters для live-смены темы) ─────────────────────────────

	static Color BG() { return GuiApp.BG_DEEP; }
	static Color CARD() { return GuiApp.BG_CARD; }
	static Color INPUT() { return GuiApp.BG_INPUT; }
	static Color BORDER() { return GuiApp.BORDER; }
	static Color ACCENT() { return GuiApp.ACCENT; }
	static Color TEXT() { return GuiApp.TEXT; }
	static Color TEXT_DIM() { return GuiApp.TEXT_DIM; }
	static Color SUCCESS() { return GuiApp.SUCCESS; }
	static Color ERROR() { return GuiApp.ERROR; }
	static Color WARN() { return GuiApp.WARN; }

	// ── UI-компоненты (package-private для доступа из builder-классов) ─────────

	ModernComboBox<String> versionCombo;
	ModernSpinner widthSpinner;
	ModernSpinner heightSpinner;
	JTextField imagePathField;
	JTextField blocksPathField;
	JTextField outPathField;
	SupportBlockSettings supportSettings;
	ModernComboBox<Object> algorithmCombo;
	ModernComboBox<Object> colorMetricCombo;
	JPanel ditherSettingsPanel;
	ModernSlider noiseLevelSlider;
	ModernSlider errRateRSlider;
	ModernSlider errRateGSlider;
	ModernSlider errRateBSlider;
	JLabel noiseLevelLabel;
	JLabel errRateRLabel;
	JLabel errRateGLabel;
	JLabel errRateBLabel;
	ModernToggleButton errRateLinkButton;
	ModernToggleButton autoConvertToggle;
	ModernSlider brightnessSlider;
	ModernSlider contrastSlider;
	ModernSlider saturationSlider;
	ModernSlider gammaSlider;
	ModernSlider hueSlider;
	JLabel brightnessLabel;
	JLabel contrastLabel;
	JLabel saturationLabel;
	JLabel gammaLabel;
	JLabel hueLabel;
	ModernComboBox<SchematicFormat> formatCombo;
	ModernComboBox<Object> staircaseModeCombo;
	JButton convertButton;
	JButton exportButton;
	JButton blockListButton;
	JButton pickBlocksButton;
	JLabel blocksCountLabel;
	JProgressBar progressBar;
	JTextArea logArea;
	ImagePreviewPanel sourcePreview;
	ImagePreviewPanel resultPreview;
	ModernCheckBox showGridCheckBox;
	ModernSpinner gridWidthSpinner;
	JButton gridBgColorButton;
	JButton importButton;
	ModernSlider blurSlider;
	JLabel blurLabel;

	// ── Состояние ──────────────────────────────────────────────────────────────

	File selectedImageFile;
	BufferedImage rawSourceImage;
	ConversionWorker activeConversionWorker;
	ExportWorker activeExportWorker;
	ImportWorker activeImportWorker;
	Ditherer lastDitherer;
	SchematicImportResult lastImportResult;
	BlockListDialog activeBlockListDialog;
	Set<String> enabledBlocks = new HashSet<>();
	Map<String, WeightedSelector<BlockData>> blockSelectors = new HashMap<>();
	volatile boolean sourcePreviewPending;
	volatile boolean sourcePreviewRunning;
	volatile boolean resetSourceViewOnNextImage;
	Timer conversionDebounceTimer;
	final Map<String, String> paletteCache = new HashMap<>();

	// ── Вспомогательные классы ─────────────────────────────────────────────────

	MainWindowActions actions;
	MainWindowPreferences prefs;

	// ── Конструктор ────────────────────────────────────────────────────────────

	public MainWindow() {
		super(Lang.t("app.title"));
		actions = new MainWindowActions(this);
		prefs = new MainWindowPreferences(this);
		buildUi(true);
		prefs.restorePreferences();
		actions.tryAutoLoadBlocks();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				prefs.savePreferences();
			}
		});
		setVisible(true);
	}

	// ── Пересборка UI (при смене темы/языка) ───────────────────────────────────

	void rebuildUi() {
		prefs.savePreferences();

		getContentPane().removeAll();
		setLayout(new BorderLayout(0, 0));

		buildUi(false);
		prefs.restorePreferences();

		revalidate();
		repaint();
	}

	// ── Построение UI ──────────────────────────────────────────────────────────

	private void buildUi(boolean isInitial) {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		if (isInitial) {
			setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
			setMinimumSize(new Dimension(920, 620));
			setLocationRelativeTo(null);
		}

		getContentPane().setBackground(BG());
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenterPanel(), BorderLayout.CENTER);

		actions.syncSourcePreviewMapCount();

		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addKeyEventDispatcher(event -> {
				if (event.getID() != KeyEvent.KEY_PRESSED || !event.isControlDown()) {
					return false;
				}

				boolean focusInTextField = event.getComponent() instanceof JTextField;

				return switch (event.getKeyCode()) {
					case KeyEvent.VK_V -> {
						if (focusInTextField) {
							yield false;
						}
						actions.pasteImageFromClipboard();
						yield true;
					}
					case KeyEvent.VK_O -> {
						if (focusInTextField) {
							yield false;
						}
						actions.chooseImage();
						yield true;
					}
					case KeyEvent.VK_ENTER -> {
						actions.startConversion();
						yield true;
					}
					case KeyEvent.VK_E -> {
						if (focusInTextField) {
							yield false;
						}
						actions.startExport();
						yield true;
					}
					case KeyEvent.VK_I -> {
						if (focusInTextField) {
							yield false;
						}
						actions.startImport();
						yield true;
					}
					case KeyEvent.VK_S -> {
						if (focusInTextField) {
							yield false;
						}
						actions.savePreview();
						yield true;
					}
					default -> false;
				};
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
				g2.setColor(BORDER());
				g2.setStroke(new BasicStroke(1f));
				g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
				g2.dispose();
			}
		};
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

		JLabel title = new JLabel("🗺  " + Lang.t("app.title"));
		title.setFont(new Font("SansSerif", Font.BOLD, 22));
		title.setForeground(ACCENT());

		JLabel subtitle = new JLabel(Lang.t("app.subtitle"));
		subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
		subtitle.setForeground(TEXT_DIM());

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(title);
		left.add(Box.createVerticalStrut(2));
		left.add(subtitle);

		JButton settingsBtn = SettingsPanelBuilder.buildIconButton(Lang.t("btn.settings"), this);
		settingsBtn.addActionListener(e -> actions.openSettings());

		header.add(left, BorderLayout.WEST);
		header.add(settingsBtn, BorderLayout.EAST);

		return header;
	}

	private JPanel buildCenterPanel() {
		JPanel rightColumn = new JPanel(new BorderLayout(0, 0));
		rightColumn.setBackground(BG());
		rightColumn.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 12));
		rightColumn.add(PreviewPanelBuilder.buildPreviewPanel(this), BorderLayout.CENTER);
		rightColumn.add(PreviewPanelBuilder.buildBottomPanel(this), BorderLayout.SOUTH);

		JPanel settingsPanel = SettingsPanelBuilder.buildSettingsPanel(this);

		JPanel center = new JPanel(new BorderLayout(10, 0));
		center.setBackground(BG());
		center.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 0));
		center.add(settingsPanel, BorderLayout.WEST);
		center.add(rightColumn, BorderLayout.CENTER);

		// Динамически ограничиваем ширину левой панели: растёт пропорционально окну,
		// но не выходит за пределы [SETTINGS_MIN_WIDTH, SETTINGS_MAX_WIDTH].
		center.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				int targetWidth = (int) (center.getWidth() * 0.28);
					int clampedWidth = Math.clamp(targetWidth, SETTINGS_MIN_WIDTH, SETTINGS_MAX_WIDTH);
					settingsPanel.setPreferredSize(new Dimension(clampedWidth, 0));
				center.revalidate();
			}
		});

		return center;
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
