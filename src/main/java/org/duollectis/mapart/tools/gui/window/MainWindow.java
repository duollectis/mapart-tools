package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.app.SingleInstanceGuard;
import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.app.AppPreferences;
import org.duollectis.mapart.tools.gui.i18n.AppLocale;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.dialog.BlockListDialog;
import org.duollectis.mapart.tools.gui.keybind.KeyBindAction;
import org.duollectis.mapart.tools.gui.keybind.KeyBindManager;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.*;
import org.duollectis.mapart.tools.gui.widget.AnimatedPanel;
import org.duollectis.mapart.tools.gui.widget.LayerListPanel;
import org.duollectis.mapart.tools.gui.widget.SliderRow;
import org.duollectis.mapart.tools.gui.worker.ConversionWorker;
import org.duollectis.mapart.tools.gui.worker.ExportWorker;
import org.duollectis.mapart.tools.gui.worker.ImportWorker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
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
	static final int SETTINGS_MIN_WIDTH = 280;
	static final int SETTINGS_MAX_WIDTH = 550;
	static final String DEFAULT_SUPPORT_BLOCK = "minecraft:stone";

	// ── Цвета (method-getters для live-смены темы) ─────────────────────────────

	public static Color BG() {return GuiApp.theme.getBgDeep();}

	public static Color CARD() {return GuiApp.theme.getBgCard();}

	public static Color INPUT() {return GuiApp.theme.getBgInput();}

	public static Color BORDER() {return GuiApp.theme.getBorder();}

	public static Color ACCENT() {return GuiApp.theme.getAccent();}

	public static Color TEXT() {return GuiApp.theme.getText();}

	public static Color TEXT_DIM() {return GuiApp.theme.getTextDim();}

	public static Color SUCCESS() {return GuiApp.theme.getSuccess();}

	public static Color ERROR() {return GuiApp.theme.getError();}

	public static Color WARN() {return GuiApp.theme.getWarn();}

	public static Color TEXT_ON_ACCENT() {return GuiApp.theme.getTextOnAccent();}

	public static Color PROGRESS_BAR() {return GuiApp.theme.getProgressBarFg() != null ? GuiApp.theme.getProgressBarFg() : GuiApp.theme.getAccent();}

	public void addHoverEffect(JButton btn) {
		actions.addHoverEffect(btn);
	}

	// ── UI-компоненты (package-private для доступа из builder-классов) ─────────

	SelectionPanel<String> versionCombo;
	MapSizeControl mapSizeControl;
	JTextField blocksPathField;
	JTextField outPathField;
	SupportBlockSettings supportSettings;
	SelectionPanel<Object> algorithmCombo;
	SelectionPanel<Object> colorMetricCombo;
	JPanel ditherSettingsPanel;
	SliderRow noiseLevelRow;
	SliderRow errRateStrengthRow;
	SliderRow errRateRRow;
	SliderRow errRateGRow;
	SliderRow errRateBRow;
	RgbChannelsButton errRateLinkButton;
	AnimatedPanel errRateStrengthPanel;
	AnimatedPanel errRateRPanel;
	AnimatedPanel errRateRgbPanel;
	ModernToggleButton autoConvertToggle;
	SliderRow brightnessRow;
	SliderRow contrastRow;
	SliderRow saturationRow;
	SliderRow gammaRow;
	SliderRow hueRow;
	SelectionPanel<SchematicFormat> formatCombo;
	SelectionPanel<Object> staircaseModeCombo;
	SelectionPanel<AppLocale> langCombo;
	SelectionPanel<Object> themeCombo;
	RippleButton convertButton;
	JButton exportButton;
	JTextField mapDatStartIdField;
	AnimatedPanel mapDatStartIdPanel;
	JButton blockListButton;
	JButton pickBlocksButton;
	JLabel blocksCountLabel;
	AnimatedProgressBar progressBar;
	AppLogPane logArea;
	ImagePreviewPanel sourcePreview;
	ImagePreviewPanel resultPreview;
	LayerListPanel layerListPanel;
	ModernToggleButton showGridButton;
	ModernToggleButton snapButton;
	JButton gridBgColorButton;
	JButton importButton;
	StyledSlider blurSlider;
	JLabel blurLabel;

	AccordionPanel appSettingsAccordion;
	AccordionPanel imageAccordion;
	AccordionPanel blocksAccordion;
	AccordionPanel ditheringAccordion;
	AccordionPanel importAccordion;
	AccordionPanel exportAccordion;

	boolean importAddToBlocks;

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
		super(UpdatableRegistry.translate("app.title"));
		actions = new MainWindowActions(this);
		prefs = new MainWindowPreferences(this);
		buildUi();
		prefs.restorePreferences();
		actions.tryAutoLoadBlocks();
		UiStateRegistry.bindWindow("main_window", this);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				prefs.savePreferences();
				setVisible(false);
				dispose();
				SingleInstanceGuard.release();
				DiscordRpc.registerShutdownHook();
				System.exit(0);
			}
		});

		setVisible(true);
	}

	private void buildUi() {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
		setMinimumSize(new Dimension(900, 560));
		setLocationRelativeTo(null);

		JPanel contentPane = new JPanel(new BorderLayout(0, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		contentPane.setOpaque(true);
		setContentPane(contentPane);
		UpdatableRegistry.setAnimWindow(this);

		add(buildCenterPanel(), BorderLayout.CENTER);

		actions.setupWindowDropTarget(getContentPane());
		actions.syncSourcePreviewMapCount();

		Map<KeyBindAction, Runnable> handlers = new EnumMap<>(KeyBindAction.class);
		handlers.put(KeyBindAction.CONVERT, actions::startConversion);
		handlers.put(KeyBindAction.OPEN_IMAGE, actions::chooseImage);
		handlers.put(KeyBindAction.PASTE_IMAGE, actions::pasteImageFromClipboard);
		handlers.put(KeyBindAction.EXPORT, actions::startExport);
		handlers.put(KeyBindAction.IMPORT, actions::startImport);
		handlers.put(KeyBindAction.SAVE_PREVIEW, actions::savePreview);
		handlers.put(KeyBindAction.MERGE_LAYERS, layerListPanel::mergeSelectedLayers);
		handlers.put(KeyBindAction.DELETE_LAYER, layerListPanel::deleteSelectedLayers);
		KeyBindManager.install(handlers);
	}

	private JPanel buildCenterPanel() {
		JPanel rightColumn = new JPanel(new BorderLayout(0, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		rightColumn.setOpaque(false);
		rightColumn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		rightColumn.add(PreviewPanelBuilder.buildPreviewPanel(this), BorderLayout.CENTER);
		rightColumn.add(PreviewPanelBuilder.buildBottomPanel(this), BorderLayout.SOUTH);

		JPanel settingsPanel = SettingsPanelBuilder.buildSettingsPanel(this);

		JPanel center = new JPanel(new BorderLayout(10, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		center.setOpaque(false);
		center.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 0));
		center.add(settingsPanel, BorderLayout.WEST);
		center.add(rightColumn, BorderLayout.CENTER);

		// Динамически ограничиваем ширину левой панели: растёт пропорционально окну,
		// но не выходит за пределы [SETTINGS_MIN_WIDTH, SETTINGS_MAX_WIDTH].
		// invokeLater гарантирует что к моменту пересчёта аккордеонов EDT уже завершил
		// layout-проход после revalidate() и settingsPanel имеет финальную ширину —
		// GridBagLayout вернёт корректный preferredSize.
		center.addComponentListener(new java.awt.event.ComponentAdapter() {
			private int lastSettingsWidth = -1;

			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				int targetWidth = (int) (center.getWidth() * 0.28);
				int clampedWidth = Math.clamp(targetWidth, SETTINGS_MIN_WIDTH, SETTINGS_MAX_WIDTH);
				settingsPanel.setPreferredSize(new Dimension(clampedWidth, 0));
				center.revalidate();

				if (clampedWidth == lastSettingsWidth) {
					return;
				}

				lastSettingsWidth = clampedWidth;
				SwingUtilities.invokeLater(() -> {
					appSettingsAccordion.refreshContentSize();
					imageAccordion.refreshContentSize();
					blocksAccordion.refreshContentSize();
					ditheringAccordion.refreshContentSize();
					importAccordion.refreshContentSize();
					exportAccordion.refreshContentSize();
				});
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
