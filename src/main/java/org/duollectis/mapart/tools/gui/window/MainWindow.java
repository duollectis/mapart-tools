package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.Lang;
import org.duollectis.mapart.tools.gui.dialog.BlockListDialog;
import org.duollectis.mapart.tools.gui.util.ThemeEventBus;
import org.duollectis.mapart.tools.gui.widget.*;
import org.duollectis.mapart.tools.gui.worker.ConversionWorker;
import org.duollectis.mapart.tools.gui.worker.ExportWorker;
import org.duollectis.mapart.tools.gui.worker.ImportWorker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

	static Color BG() { return GuiApp.theme.getBgDeep(); }
	static Color CARD() { return GuiApp.theme.getBgCard(); }
	static Color INPUT() { return GuiApp.theme.getBgInput(); }
	static Color BORDER() { return GuiApp.theme.getBorder(); }
	static Color ACCENT() { return GuiApp.theme.getAccent(); }
	static Color TEXT() { return GuiApp.theme.getText(); }
	static Color TEXT_DIM() { return GuiApp.theme.getTextDim(); }
	static Color SUCCESS() { return GuiApp.theme.getSuccess(); }
	static Color ERROR() { return GuiApp.theme.getError(); }
	static Color WARN() { return GuiApp.theme.getWarn(); }

	// ── UI-компоненты (package-private для доступа из builder-классов) ─────────

	DropDown<String> versionCombo;
	ModernSpinner widthSpinner;
	ModernSpinner heightSpinner;
	JTextField imagePathField;
	JTextField blocksPathField;
	JTextField outPathField;
	SupportBlockSettings supportSettings;
	DropDown<Object> algorithmCombo;
	DropDown<Object> colorMetricCombo;
	JPanel ditherSettingsPanel;
	StyledSlider noiseLevelSlider;
	StyledSlider errRateRSlider;
	StyledSlider errRateGSlider;
	StyledSlider errRateBSlider;
	JLabel noiseLevelLabel;
	JLabel errRateRLabel;
	JLabel errRateGLabel;
	JLabel errRateBLabel;
	ModernToggleButton errRateLinkButton;
	ModernToggleButton autoConvertToggle;
	StyledSlider brightnessSlider;
	StyledSlider contrastSlider;
	StyledSlider saturationSlider;
	StyledSlider gammaSlider;
	StyledSlider hueSlider;
	JLabel brightnessLabel;
	JLabel contrastLabel;
	JLabel saturationLabel;
	JLabel gammaLabel;
	JLabel hueLabel;
	DropDown<SchematicFormat> formatCombo;
	DropDown<Object> staircaseModeCombo;
	JButton convertButton;
	JButton exportButton;
	JButton blockListButton;
	JButton pickBlocksButton;
	JLabel blocksCountLabel;
	JProgressBar progressBar;
	AppLogPane logArea;
	ImagePreviewPanel sourcePreview;
	ImagePreviewPanel resultPreview;
	ModernToggleButton showGridButton;
	JButton gridBgColorButton;
	JButton importButton;
	StyledSlider blurSlider;
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
		ThemeEventBus.clear();

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

		((JComponent) getContentPane()).setOpaque(true);
		getContentPane().setBackground(BG());
		setLayout(new BorderLayout(0, 0));
		ThemeEventBus.register(() -> getContentPane().setBackground(BG()));

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

	private JPanel buildCenterPanel() {
		JPanel rightColumn = new JPanel(new BorderLayout(0, 0));
		rightColumn.setBackground(BG());
		rightColumn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		rightColumn.add(PreviewPanelBuilder.buildPreviewPanel(this), BorderLayout.CENTER);
		rightColumn.add(PreviewPanelBuilder.buildBottomPanel(this), BorderLayout.SOUTH);
		ThemeEventBus.register(() -> rightColumn.setBackground(BG()));

		JPanel settingsPanel = SettingsPanelBuilder.buildSettingsPanel(this);

		JPanel center = new JPanel(new BorderLayout(10, 0));
		center.setBackground(BG());
		center.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 0));
		center.add(settingsPanel, BorderLayout.WEST);
		center.add(rightColumn, BorderLayout.CENTER);
		ThemeEventBus.register(() -> center.setBackground(BG()));

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
