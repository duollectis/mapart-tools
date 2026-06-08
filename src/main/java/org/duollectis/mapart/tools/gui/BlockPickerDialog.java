package org.duollectis.mapart.tools.gui;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.utils.JsonHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Диалог визуального выбора блоков для палитры.
 * Слева — список цветов из JSON палитры с цветовым квадратом и HEX-кодом.
 * Справа — блоки выбранного цвета: режим «Иконки» (сетка) или «Названия» (чекбоксы).
 * Палитра и иконки загружаются из versions/{version}.zip в classpath.
 * Результат сохраняется в указанный файл blocks.txt.
 */
public class BlockPickerDialog extends JDialog {

	private static final int DIALOG_WIDTH = 960;
	private static final int DIALOG_HEIGHT = 660;
	private static final int COLOR_SWATCH_SIZE = 20;
	private static final int ICON_CELL_SIZE = 48;
	private static final int SHADE_LOW = 180;
	private static final int SHADE_NORMAL = 220;
	private static final int SHADE_HIGH = 255;
	/** Сентинел-значение в списке цветов, обозначающее вкладку выбора блоков-опор. */
	private static final int SUPPORT_SENTINEL = Integer.MIN_VALUE;

	private static final Color BG = GuiApp.BG_DEEP;
	private static final Color BG_CARD = GuiApp.BG_CARD;
	private static final Color BG_INPUT = GuiApp.BG_INPUT;
	private static final Color BORDER = GuiApp.BORDER;
	private static final Color ACCENT = GuiApp.ACCENT;
	private static final Color TEXT = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;
	private static final Color SELECTION_BG = GuiApp.SELECTION_BG;
	private static final Color SUCCESS = GuiApp.SUCCESS;

	private final Map<Integer, List<BlockData>> fullPalette;
	private final DefaultListModel<Integer> colorListModel = new DefaultListModel<>();
	private final Map<String, ModernCheckBox> blockCheckboxes = new LinkedHashMap<>();
	private final Set<String> enabledBlocks;
	/** Ключ — базовый blockId (без properties). Хранит настройки весов для вариантов блока. */
	private final Map<String, WeightedSelector<BlockData>> blockSelectors = new HashMap<>();
	/** Блоки, выбранные как опоры (needSupport=false). */
	private final Set<String> enabledSupportBlocks = new HashSet<>();
	/** Текущие настройки весов блоков опоры (режим + проценты). */
	private SupportBlockSettings supportSettings;
	private final BlockIconLoader iconLoader;
	private final String version;
	private boolean iconMode = true;

	private JList<Integer> colorList;
	private JPanel blocksPanel;
	private JScrollPane blocksScroll;
	private JTextField searchField;
	private JLabel colorInfoLabel;
	private JLabel selectedCountLabel;
	private ModernCheckBox hideConfiguredFilter;
	private ModernToggleButton viewToggle;
	private JButton iconVariantBtn;

	private final File targetFile;
	private boolean confirmed = false;

	public BlockPickerDialog(
		JFrame parent,
		String version,
		File targetFile,
		Set<String> initialEnabled,
		SupportBlockSettings initialSupport,
		Map<String, WeightedSelector<BlockData>> initialSelectors
	) {
		super(parent, Lang.t("picker.title", version), true);
		this.version = version;
		this.targetFile = targetFile;
		this.fullPalette = loadPalette(version);
		this.iconLoader = BlockIconLoader.create(version).orElse(null);
		this.enabledBlocks = expandInitialEnabled(initialEnabled, fullPalette);

		this.supportSettings = initialSupport;

		if (initialSupport != null) {
			Set<String> validSupportIds = fullPalette.values().stream()
				.flatMap(List::stream)
				.filter(b -> !b.isNeedSupport())
				.map(BlockData::getId)
				.collect(java.util.stream.Collectors.toSet());

			initialSupport.getEntries().stream()
				.map(e -> e.blockId())
				.filter(validSupportIds::contains)
				.forEach(enabledSupportBlocks::add);
		}

		if (initialSelectors != null) {
			blockSelectors.putAll(initialSelectors);
		}

		ToolTipManager.sharedInstance().setInitialDelay(300);

		buildUi();
		populateColorList("");
		setVisible(true);
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public Set<String> getEnabledBlocks() {
		return Set.copyOf(enabledBlocks);
	}

	/**
	 * Возвращает карту селекторов блоков: ключ — базовый blockId, значение — {@link WeightedSelector}.
	 * Содержит только те блоки, для которых пользователь явно настроил веса.
	 * Для остальных блоков используется дефолтный селектор (первый вариант).
	 */
	public Map<String, WeightedSelector<BlockData>> getBlockSelectors() {
		return Map.copyOf(blockSelectors);
	}

	/**
	 * Возвращает настройки блоков опоры с сохранёнными весами и режимом.
	 * Пустой список допустим — означает что пользователь снял все блоки опоры.
	 */
	public SupportBlockSettings getSupportSettings() {
		WeightedSelector.Mode effectiveMode = supportSettings != null
			? supportSettings.getMode()
			: WeightedSelector.Mode.SEQUENTIAL;

		if (enabledSupportBlocks.isEmpty()) {
			return new SupportBlockSettings(List.of(), effectiveMode);
		}

		if (supportSettings != null) {
			List<SupportBlockSettings.Entry> kept = supportSettings.getEntries().stream()
				.filter(e -> enabledSupportBlocks.contains(e.blockId()))
				.toList();

			List<String> newIds = enabledSupportBlocks.stream()
				.filter(id -> kept.stream().noneMatch(e -> e.blockId().equals(id)))
				.sorted()
				.toList();

			List<SupportBlockSettings.Entry> merged = new ArrayList<>(kept);
			newIds.forEach(id -> merged.add(new SupportBlockSettings.Entry(id, 100)));

			return new SupportBlockSettings(merged, effectiveMode);
		}

		List<SupportBlockSettings.Entry> entries = enabledSupportBlocks.stream()
			.sorted()
			.map(id -> new SupportBlockSettings.Entry(id, 100))
			.toList();

		return new SupportBlockSettings(entries, WeightedSelector.Mode.SEQUENTIAL);
	}

	private void buildUi() {
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setMinimumSize(new Dimension(720, 520));
		setLocationRelativeTo(getParent());
		setLayout(new BorderLayout(0, 0));
		getContentPane().setBackground(BG);

		add(buildTopBar(), BorderLayout.NORTH);
		add(buildSplitPanel(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	private JPanel buildTopBar() {
		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBackground(BG_CARD);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
			BorderFactory.createEmptyBorder(10, 16, 10, 16)
		));

		JLabel title = new JLabel("🎨 " + Lang.t("picker.title", version));
		title.setFont(new Font("SansSerif", Font.BOLD, 15));
		title.setForeground(ACCENT);

		viewToggle = new ModernToggleButton("📝 " + Lang.t("picker.view_names"));
		viewToggle.setSelected(false);
		viewToggle.setEnabled(iconLoader != null);
		viewToggle.setToolTipText(
			iconLoader == null
				? "Иконки недоступны — versions/" + version + ".zip не найден в ресурсах"
				: "Переключить вид: иконки / названия"
		);
		viewToggle.addActionListener(e -> {
			iconMode = !viewToggle.isSelected();
			viewToggle.setText(iconMode ? "📝 " + Lang.t("picker.view_names") : "🖼 " + Lang.t("picker.view_icons"));
			refreshColorInfo();
		});

		hideConfiguredFilter = new ModernCheckBox(Lang.t("picker.hide_configured"));
		hideConfiguredFilter.addActionListener(e -> onSearchChanged());

		selectedCountLabel = new JLabel();
		selectedCountLabel.setForeground(TEXT_DIM);
		selectedCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		updateSelectedCount();

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setOpaque(false);
		right.add(viewToggle);
		right.add(Box.createHorizontalStrut(14));
		right.add(hideConfiguredFilter);
		right.add(Box.createHorizontalStrut(18));
		right.add(selectedCountLabel);

		bar.add(title, BorderLayout.WEST);
		bar.add(right, BorderLayout.EAST);

		return bar;
	}

	private JPanel buildSplitPanel() {
		JPanel split = new JPanel(new BorderLayout(8, 0));
		split.setBackground(BG);
		split.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

		JPanel colorPanel = buildColorListPanel();
		colorPanel.setPreferredSize(new Dimension(300, 0));

		split.add(colorPanel, BorderLayout.WEST);
		split.add(buildBlocksPanel(), BorderLayout.CENTER);

		return split;
	}

	private JPanel buildColorListPanel() {
		searchField = buildSearchField();

		colorList = new JList<>(colorListModel);
		colorList.setBackground(BG_INPUT);
		colorList.setForeground(TEXT);
		colorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		colorList.setSelectionBackground(SELECTION_BG);
		colorList.setSelectionForeground(TEXT);
		colorList.setCellRenderer(new ColorCellRenderer());
		colorList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) {
				return;
			}

			onColorSelected(colorList.getSelectedValue());
		});

		JScrollPane scroll = buildScrollPane(colorList);

		JLabel label = new JLabel(Lang.t("picker.colors") + " (" + fullPalette.size() + ")");
		label.setForeground(TEXT_DIM);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));

		JPanel top = new JPanel(new BorderLayout(0, 5));
		top.setOpaque(false);
		top.add(label, BorderLayout.NORTH);
		top.add(searchField, BorderLayout.SOUTH);

		JPanel full = new JPanel(new BorderLayout(0, 6));
		full.setOpaque(false);
		full.add(top, BorderLayout.NORTH);
		full.add(scroll, BorderLayout.CENTER);

		return full;
	}

	private JPanel buildBlocksPanel() {
		colorInfoLabel = new JLabel("← " + Lang.t("picker.blocks"));
		colorInfoLabel.setForeground(TEXT_DIM);
		colorInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		blocksPanel = new JPanel();
		blocksPanel.setLayout(new BoxLayout(blocksPanel, BoxLayout.Y_AXIS));
		blocksPanel.setBackground(BG_INPUT);

		blocksScroll = buildScrollPane(blocksPanel);
		blocksScroll.getVerticalScrollBar().setUnitIncrement(16);
		blocksScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		blocksScroll.getViewport().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				Component view = blocksScroll.getViewport().getView();

				if (view != null) {
					view.revalidate();
					view.repaint();
				}
			}
		});

		JPanel selectAllRow = buildSelectAllRow();

		JPanel header = new JPanel(new BorderLayout(0, 5));
		header.setOpaque(false);
		header.add(colorInfoLabel, BorderLayout.NORTH);
		header.add(selectAllRow, BorderLayout.SOUTH);

		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setOpaque(false);
		panel.add(header, BorderLayout.NORTH);
		panel.add(blocksScroll, BorderLayout.CENTER);

		return panel;
	}

	private JPanel buildSelectAllRow() {
		JButton selectAll = buildSmallButton(Lang.t("picker.select_all_color"));
		JButton selectNone = buildSmallButton(Lang.t("picker.clear_all"));

		selectAll.addActionListener(e -> setAllBlocksInColor(true));
		selectNone.addActionListener(e -> setAllBlocksInColor(false));

		iconVariantBtn = buildIconVariantButton();

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.add(selectAll);
		row.add(Box.createHorizontalStrut(6));
		row.add(selectNone);
		row.add(Box.createHorizontalGlue());
		row.add(iconVariantBtn);

		return row;
	}

	private JPanel buildBottomBar() {
		JButton selectAllGlobal = buildSmallButton(Lang.t("picker.select_all"));
		JButton clearAll = buildSmallButton(Lang.t("picker.clear_all"));
		JButton save = buildPrimaryButton("💾 " + Lang.t("picker.save"), ACCENT, Color.WHITE);
		JButton cancel = buildSmallButton(Lang.t("picker.cancel"));

		selectAllGlobal.addActionListener(e -> {
			fullPalette.values().forEach(blocks -> blocks.forEach(b -> enabledBlocks.add(b.getId())));
			refreshBlocksView();
			updateSelectedCount();
		});

		clearAll.addActionListener(e -> {
			enabledBlocks.clear();
			refreshBlocksView();
			updateSelectedCount();
		});

		save.addActionListener(e -> saveAndClose());
		cancel.addActionListener(e -> dispose());

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setOpaque(false);
		left.add(selectAllGlobal);
		left.add(Box.createHorizontalStrut(6));
		left.add(clearAll);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setOpaque(false);
		right.add(cancel);
		right.add(Box.createHorizontalStrut(8));
		right.add(save);

		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(BG_CARD);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
			BorderFactory.createEmptyBorder(10, 14, 10, 14)
		));
		bar.add(left, BorderLayout.WEST);
		bar.add(right, BorderLayout.EAST);

		return bar;
	}

	private void onColorSelected(Integer colorRgb) {
		if (colorRgb == null) {
			return;
		}

		if (colorRgb == SUPPORT_SENTINEL) {
			showSupportBlocksPanel();
			return;
		}

		int r = (colorRgb >> 16) & 0xFF;
		int g = (colorRgb >> 8) & 0xFF;
		int b = colorRgb & 0xFF;
		String hex = "#%02X%02X%02X".formatted(r, g, b);

		List<BlockData> blocks = fullPalette.get(colorRgb);
		long enabled = blocks.stream().filter(bl -> enabledBlocks.contains(bl.getUniqueKey())).count();

		colorInfoLabel.setText("%s  ·  %d  ·  %d".formatted(hex, blocks.size(), enabled));

		if (iconMode) {
			rebuildIconGrid(blocks);
		} else {
			rebuildBlockCheckboxes(blocks);
		}
	}

	/**
	 * Показывает в правой панели все блоки палитры, которым не нужна опора (needSupport=false).
	 * Активные (выбранные как опора) — сверху, неактивные — снизу через сепаратор.
	 * Кнопка "⚖ Настроить распределение" активна при >= 2 выбранных блоках.
	 */
	private void showSupportBlocksPanel() {
		colorInfoLabel.setText("🧱 " + Lang.t("picker.tab_support"));

		List<BlockData> allCandidates = fullPalette.values().stream()
			.flatMap(List::stream)
			.filter(b -> !b.isNeedSupport())
			.distinct()
			.sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
			.toList();

		String filter = searchField.getText().strip().toLowerCase();

		List<BlockData> filtered = allCandidates.stream()
			.filter(b -> filter.isBlank() || b.getId().contains(filter))
			.toList();

		List<BlockData> active = filtered.stream()
			.filter(b -> enabledSupportBlocks.contains(b.getId()))
			.toList();

		List<BlockData> inactive = filtered.stream()
			.filter(b -> !enabledSupportBlocks.contains(b.getId()))
			.toList();

		if (iconVariantBtn != null) {
			iconVariantBtn.setVisible(true);
			iconVariantBtn.setEnabled(active.size() >= 2);

			for (var listener : iconVariantBtn.getActionListeners()) {
				iconVariantBtn.removeActionListener(listener);
			}

			iconVariantBtn.addActionListener(e -> openSupportDistributionDialog());
		}

		int totalSupportWeight = computeSupportTotalWeight(active);

		ScrollableGrid grid = new ScrollableGrid();
		grid.setBackground(BG_INPUT);

		active.forEach(block -> grid.add(buildSupportIconCell(block, true, computeSupportPercent(block, totalSupportWeight, active.size()))));

		if (!inactive.isEmpty() && !active.isEmpty()) {
			grid.add(buildSeparator());
		}

		inactive.forEach(block -> grid.add(buildSupportIconCell(block, false, -1)));

		blocksScroll.setViewportView(grid);
		blocksScroll.revalidate();
		blocksScroll.repaint();
	}

	private void openSupportDistributionDialog() {
		List<BlockData> activeSupport = fullPalette.values().stream()
			.flatMap(List::stream)
			.filter(b -> !b.isNeedSupport())
			.filter(b -> enabledSupportBlocks.contains(b.getId()))
			.distinct()
			.sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
			.toList();

		BlockVariantPickerDialog dialog = new BlockVariantPickerDialog(
			(JFrame) getOwner(),
			activeSupport,
			supportSettings
		);

		if (dialog.isConfirmed()) {
			supportSettings = dialog.buildSupportSettings();
			enabledSupportBlocks.clear();
			supportSettings.getEntries().forEach(e -> enabledSupportBlocks.add(e.blockId()));
			showSupportBlocksPanel();
		}
	}


	private int computeSupportTotalWeight(List<BlockData> activeBlocks) {
		if (activeBlocks.isEmpty()) {
			return 1;
		}

		if (supportSettings == null) {
			return activeBlocks.size();
		}

		return supportSettings.getEntries().stream()
			.filter(e -> activeBlocks.stream().anyMatch(b -> b.getId().equals(e.blockId())))
			.mapToInt(SupportBlockSettings.Entry::weight)
			.sum();
	}

	private int computeSupportPercent(BlockData block, int totalWeight, int activeCount) {
		if (activeCount <= 1) {
			return -1;
		}

		if (supportSettings == null) {
			return 100 / activeCount;
		}

		int blockWeight = supportSettings.getEntries().stream()
			.filter(e -> e.blockId().equals(block.getId()))
			.mapToInt(SupportBlockSettings.Entry::weight)
			.findFirst()
			.orElse(0);

		return totalWeight > 0 ? (int) Math.round(blockWeight * 100.0 / totalWeight) : 0;
	}

	private JPanel buildSupportIconCell(BlockData block, boolean active, int percent) {
		int cellSize = ICON_CELL_SIZE + 8;

		Optional<ImageIcon> icon = iconLoader != null
			? iconLoader.getIcon(block)
			: Optional.empty();

		JToggleButton btn = new JToggleButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				boolean on = isSelected();
				g2.setColor(on ? new Color(20, 50, 80) : BG_INPUT);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

				g2.setColor(on ? ACCENT : BORDER);
				g2.setStroke(new BasicStroke(on ? 2f : 1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

				if (!on) {
					g2.setColor(new Color(0, 0, 0, 80));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setSelected(active);
		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setToolTipText(block.getId());
		btn.setPreferredSize(new Dimension(cellSize, cellSize));

		if (icon.isPresent()) {
			btn.setIcon(icon.get());
		} else {
			btn.setText(shortName(block.getId()));
			btn.setFont(new Font("Monospaced", Font.PLAIN, 9));
			btn.setForeground(active ? TEXT : TEXT_DIM);
		}

		btn.addActionListener(e -> {
			if (btn.isSelected()) {
				enabledSupportBlocks.add(block.getId());
			} else {
				enabledSupportBlocks.remove(block.getId());
			}

			showSupportBlocksPanel();
		});

		JLayeredPane layered = new JLayeredPane();
		layered.setPreferredSize(new Dimension(cellSize, cellSize));
		layered.setOpaque(false);

		btn.setBounds(0, 0, cellSize, cellSize);
		layered.add(btn, JLayeredPane.DEFAULT_LAYER);

		if (percent >= 0) {
			String pctText = percent + "%";
			JLabel pctBadge = new JLabel(pctText) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(new Color(0, 0, 0, 160));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
					g2.dispose();
					super.paintComponent(g);
				}
			};

			pctBadge.setFont(new Font("SansSerif", Font.BOLD, 9));
			pctBadge.setForeground(new Color(150, 210, 255));
			pctBadge.setOpaque(false);
			pctBadge.setHorizontalAlignment(SwingConstants.CENTER);

			FontMetrics fm = pctBadge.getFontMetrics(pctBadge.getFont());
			int badgeW = fm.stringWidth(pctText) + 6;
			int badgeH = 14;
			pctBadge.setBounds((cellSize - badgeW) / 2, cellSize - badgeH - 2, badgeW, badgeH);
			layered.add(pctBadge, JLayeredPane.PALETTE_LAYER);
		}

		JPanel cell = new JPanel(new BorderLayout());
		cell.setBackground(BG_INPUT);
		cell.add(layered, BorderLayout.CENTER);

		return cell;
	}


	private void rebuildBlockCheckboxes(List<BlockData> blocks) {
		String filter = searchField.getText().strip().toLowerCase();

		List<BlockData> active = blocks.stream()
			.filter(b -> enabledBlocks.contains(b.getUniqueKey()))
			.toList();

		updateIconVariantBtn(active, blocks);

		blockCheckboxes.clear();
		blocksPanel.removeAll();
		blocksPanel.setLayout(new BoxLayout(blocksPanel, BoxLayout.Y_AXIS));

		if (blocksScroll.getViewport().getView() != blocksPanel) {
			blocksScroll.setViewportView(blocksPanel);
		}

		blocks.stream()
			.sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
			.filter(block -> filter.isBlank() || block.getId().contains(filter))
			.forEach(block -> {
				String label = block.isNeedSupport()
					? buildBlockLabel(block) + "  ⚠ требует опору"
					: buildBlockLabel(block);

				ModernCheckBox cb = new ModernCheckBox(label);
				cb.setSelected(enabledBlocks.contains(block.getUniqueKey()));
				cb.setFont(new Font("Monospaced", Font.PLAIN, 12));
				cb.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
				cb.setAlignmentX(Component.LEFT_ALIGNMENT);

				if (block.isNeedSupport()) {
					cb.setForeground(new Color(200, 160, 80));
				}

				cb.addActionListener(e -> {
					if (cb.isSelected()) {
						enabledBlocks.add(block.getUniqueKey());
					} else {
						enabledBlocks.remove(block.getUniqueKey());
					}

					updateSelectedCount();
					refreshColorInfo();
				});

				blockCheckboxes.put(block.getUniqueKey(), cb);

				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setOpaque(false);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
				row.add(cb, BorderLayout.CENTER);

				blocksPanel.add(row);
			});

		blocksPanel.add(Box.createVerticalGlue());
		blocksPanel.revalidate();
		blocksPanel.repaint();
	}

	private JButton buildIconVariantButton() {
		JButton btn = new JButton("⚖ " + Lang.t("variant_picker.btn_tooltip")) {
			private boolean hovered = false;

			{
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						hovered = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						hovered = false;
						repaint();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = isEnabled()
					? (getModel().isPressed()
						? new Color(30, 60, 100)
						: (hovered ? new Color(40, 80, 130) : new Color(25, 50, 85)))
					: new Color(30, 32, 48);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setForeground(ACCENT);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		btn.setToolTipText(Lang.t("variant_picker.hint"));
		btn.setEnabled(false);

		return btn;
	}

	/**
	 * Строит сетку иконок блоков с кнопками-переключателями.
	 * Каждая ячейка — JToggleButton с иконкой 32×32 и tooltip с id блока.
	 */
	private void rebuildIconGrid(List<BlockData> blocks) {
		String filter = searchField.getText().strip().toLowerCase();

		if (iconVariantBtn != null) {
			iconVariantBtn.setVisible(true);
		}

		blockCheckboxes.clear();

		ScrollableGrid grid = new ScrollableGrid();
		grid.setBackground(BG_INPUT);

		List<BlockData> sorted = blocks.stream()
			.sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
			.filter(block -> filter.isBlank() || block.getId().contains(filter))
			.toList();

		List<BlockData> active = sorted.stream()
			.filter(b -> enabledBlocks.contains(b.getUniqueKey()))
			.toList();

		List<BlockData> inactive = sorted.stream()
			.filter(b -> !enabledBlocks.contains(b.getUniqueKey()))
			.toList();

		updateIconVariantBtn(active, blocks);

		String baseId = active.isEmpty() ? null : active.getFirst().getId();
		WeightedSelector<BlockData> selector = baseId != null ? blockSelectors.get(baseId) : null;
		int totalWeight = computeTotalWeight(active, selector);

		active.forEach(block -> grid.add(buildIconCell(block, true, computePercent(block, selector, totalWeight, active.size()))));

		if (!inactive.isEmpty() && !active.isEmpty()) {
			grid.add(buildSeparator());
		}

		inactive.forEach(block -> grid.add(buildIconCell(block, false, -1)));

		blocksScroll.setViewportView(grid);
		blocksScroll.revalidate();
		blocksScroll.repaint();
	}

	private void updateIconVariantBtn(List<BlockData> active, List<BlockData> allBlocksInColor) {
		if (iconVariantBtn == null) {
			return;
		}

		boolean canConfigure = active.size() >= 2;
		iconVariantBtn.setEnabled(canConfigure);

		for (var listener : iconVariantBtn.getActionListeners()) {
			iconVariantBtn.removeActionListener(listener);
		}

		iconVariantBtn.addActionListener(e -> {
			String baseId = active.getFirst().getId();
			WeightedSelector<BlockData> current = blockSelectors.get(baseId);
			BlockVariantPickerDialog dialog = new BlockVariantPickerDialog(
				(JFrame) getOwner(),
				active,
				current
			);

			if (dialog.isConfirmed()) {
				blockSelectors.put(baseId, dialog.buildSelector());

				// Деактивируем блоки, которым выставили 0%
				for (BlockData zeroBlock : dialog.getZeroPercentBlocks()) {
					enabledBlocks.remove(zeroBlock.getUniqueKey());
				}

				refreshColorInfo();
			}
		});
	}

	/**
	 * Вычисляет суммарный вес всех активных блоков для нормализации процентов.
	 * Принимает уже найденный селектор группы — не ищет повторно по ID.
	 */
	private int computeTotalWeight(List<BlockData> activeBlocks, WeightedSelector<BlockData> selector) {
		if (activeBlocks.isEmpty()) {
			return 1;
		}

		if (selector != null) {
			return selector.getEntries().stream().mapToInt(WeightedSelector.Entry::weight).sum();
		}

		return activeBlocks.size();
	}

	/**
	 * Вычисляет процент распределения для конкретного блока.
	 * Принимает уже найденный селектор группы — не ищет повторно по ID.
	 * Возвращает -1 если активный блок только один (процент не нужен).
	 */
	private int computePercent(BlockData block, WeightedSelector<BlockData> selector, int totalWeight, int activeCount) {
		if (activeCount <= 1) {
			return -1;
		}

		if (selector == null) {
			return 100 / activeCount;
		}

		int blockWeight = selector.getEntries().stream()
			.filter(e -> e.value().getUniqueKey().equals(block.getUniqueKey()))
			.mapToInt(WeightedSelector.Entry::weight)
			.findFirst()
			.orElse(0);

		return totalWeight > 0 ? (int) Math.round(blockWeight * 100.0 / totalWeight) : 0;
	}

	private JPanel buildSeparator() {
		JPanel sep = new JPanel(new BorderLayout(6, 0)) {
			@Override
			public Dimension getPreferredSize() {
				// Занимает всю ширину родителя — FlowLayout перенесёт следующие элементы на новую строку
				int parentWidth = getParent() != null ? getParent().getWidth() : 300;
				return new Dimension(parentWidth - 12, 20);
			}
		};

		sep.setOpaque(false);

		JLabel label = new JLabel("─── неактивные ───");
		label.setForeground(new Color(80, 85, 110));
		label.setFont(new Font("SansSerif", Font.PLAIN, 10));
		label.setHorizontalAlignment(SwingConstants.CENTER);

		sep.add(label, BorderLayout.CENTER);

		return sep;
	}

	private JPanel buildIconCell(BlockData block, boolean active, int percent) {
		int cellSize = ICON_CELL_SIZE + 8;

		Optional<ImageIcon> icon = iconLoader != null
			? iconLoader.getIcon(block)
			: Optional.empty();

		JToggleButton btn = new JToggleButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				boolean on = isSelected();
				g2.setColor(on ? new Color(30, 65, 30) : BG_INPUT);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

				g2.setColor(on ? SUCCESS : BORDER);
				g2.setStroke(new BasicStroke(on ? 2f : 1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

				if (!on) {
					// Приглушаем неактивные блоки полупрозрачным оверлеем
					g2.setColor(new Color(0, 0, 0, 80));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setSelected(active);
		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setToolTipText(
			block.isNeedSupport()
				? buildBlockLabel(block) + "  ⚠ требует опору"
				: buildBlockLabel(block)
		);
		btn.setPreferredSize(new Dimension(cellSize, cellSize));

		if (icon.isPresent()) {
			btn.setIcon(icon.get());
		} else {
			btn.setText(shortName(block.getId()));
			btn.setFont(new Font("Monospaced", Font.PLAIN, 9));
			btn.setForeground(active ? TEXT : TEXT_DIM);
		}

		btn.addActionListener(e -> {
			if (btn.isSelected()) {
				enabledBlocks.add(block.getUniqueKey());
			} else {
				enabledBlocks.remove(block.getUniqueKey());
			}

			btn.repaint();
			updateSelectedCount();
			refreshColorInfo();
		});

		JLayeredPane layered = new JLayeredPane();
		layered.setPreferredSize(new Dimension(cellSize, cellSize));
		layered.setOpaque(false);

		btn.setBounds(0, 0, cellSize, cellSize);
		layered.add(btn, JLayeredPane.DEFAULT_LAYER);

		// Бейдж с процентом — только для активных блоков когда их > 1
		if (percent >= 0) {
			String pctText = percent + "%";
			JLabel pctBadge = new JLabel(pctText) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(new Color(0, 0, 0, 160));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
					g2.dispose();
					super.paintComponent(g);
				}
			};

			pctBadge.setFont(new Font("SansSerif", Font.BOLD, 9));
			pctBadge.setForeground(new Color(180, 230, 180));
			pctBadge.setOpaque(false);
			pctBadge.setHorizontalAlignment(SwingConstants.CENTER);

			FontMetrics fm = pctBadge.getFontMetrics(pctBadge.getFont());
			int badgeW = fm.stringWidth(pctText) + 6;
			int badgeH = 14;
			pctBadge.setBounds((cellSize - badgeW) / 2, cellSize - badgeH - 2, badgeW, badgeH);
			layered.add(pctBadge, JLayeredPane.PALETTE_LAYER);
		}

		if (block.isNeedSupport()) {
			JLabel badge = new JLabel("⚠");
			badge.setFont(new Font("SansSerif", Font.BOLD, 10));
			badge.setForeground(new Color(255, 200, 50));
			badge.setOpaque(true);
			badge.setBackground(new Color(60, 40, 0, 200));
			badge.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
			badge.setBounds(cellSize - 18, 2, 16, 14);
			layered.add(badge, JLayeredPane.PALETTE_LAYER);
		}

		JPanel cell = new JPanel(new BorderLayout());
		cell.setBackground(BG_INPUT);
		cell.add(layered, BorderLayout.CENTER);

		return cell;
	}

	private static String buildBlockLabel(BlockData block) {
		Map<String, String> props = block.getProperties();

		if (props.isEmpty()) {
			return block.getId();
		}

		String suffix = props.entrySet().stream()
			.map(e -> e.getKey() + "_" + e.getValue())
			.collect(Collectors.joining("_"));

		return block.getId() + "_" + suffix;
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		String name = colon >= 0 ? blockId.substring(colon + 1) : blockId;

		return name.length() > 8 ? name.substring(0, 8) : name;
	}

	/**
	 * Конвертирует initialEnabled из blocks.txt в набор uniqueKey.
	 * Поддерживает два формата:
	 * - старый: голый getId() (например "minecraft:bamboo_block") — добавляет первый вариант из палитры
	 * - новый: uniqueKey (например "minecraft:bamboo_block_axis_y") — добавляет как есть
	 */
	private static Set<String> expandInitialEnabled(
		Set<String> initialEnabled,
		Map<Integer, List<BlockData>> palette
	) {
		Set<String> allUniqueKeys = palette.values().stream()
			.flatMap(List::stream)
			.map(BlockData::getUniqueKey)
			.collect(Collectors.toSet());

		Set<String> result = new HashSet<>();

		for (String entry : initialEnabled) {
			if (allUniqueKeys.contains(entry)) {
				result.add(entry);
			} else {
				palette.values().stream()
					.flatMap(List::stream)
					.filter(b -> b.getId().equals(entry))
					.map(BlockData::getUniqueKey)
					.findFirst()
					.ifPresent(result::add);
			}
		}

		return result;
	}

	private void refreshBlocksView() {
		Integer selected = colorList.getSelectedValue();

		if (selected == null) {
			return;
		}

		if (selected == SUPPORT_SENTINEL) {
			showSupportBlocksPanel();
			return;
		}

		List<BlockData> blocks = fullPalette.get(selected);

		if (iconMode) {
			rebuildIconGrid(blocks);
		} else {
			blockCheckboxes.forEach((uniqueKey, cb) -> cb.setSelected(enabledBlocks.contains(uniqueKey)));
		}
	}

	private void refreshColorInfo() {
		Integer selected = colorList.getSelectedValue();

		if (selected == null) {
			return;
		}

		onColorSelected(selected);
	}

	private void setAllBlocksInColor(boolean enabled) {
		Integer colorRgb = colorList.getSelectedValue();

		if (colorRgb == null || colorRgb == SUPPORT_SENTINEL) {
			return;
		}

		List<BlockData> blocks = fullPalette.getOrDefault(colorRgb, List.of());

		if (enabled) {
			blocks.forEach(b -> enabledBlocks.add(b.getUniqueKey()));
		} else {
			blocks.forEach(b -> enabledBlocks.remove(b.getUniqueKey()));
		}

		refreshBlocksView();
		updateSelectedCount();
		refreshColorInfo();
	}

	private void onSearchChanged() {
		String filter = searchField.getText().strip().toLowerCase();
		populateColorList(filter);
		refreshColorInfo();
	}

	private void populateColorList(String filter) {
		Integer previousSelection = colorList != null ? colorList.getSelectedValue() : null;

		colorListModel.clear();
		colorListModel.addElement(SUPPORT_SENTINEL);

		boolean hideConfigured = hideConfiguredFilter.isSelected();

		fullPalette.forEach((colorRgb, blocks) -> {
			boolean hasEnabled = blocks.stream().anyMatch(bl -> enabledBlocks.contains(bl.getUniqueKey()));

			if (hideConfigured && hasEnabled) {
				return;
			}

			if (filter.isBlank()) {
				colorListModel.addElement(colorRgb);
				return;
			}

			int r = (colorRgb >> 16) & 0xFF;
			int g = (colorRgb >> 8) & 0xFF;
			int b = colorRgb & 0xFF;
			String hex = "#%02x%02x%02x".formatted(r, g, b);

			boolean hexMatch = hex.contains(filter);
			boolean blockMatch = blocks.stream().anyMatch(bl -> bl.getId().contains(filter));

			if (hexMatch || blockMatch) {
				colorListModel.addElement(colorRgb);
			}
		});

		if (previousSelection != null && colorListModel.contains(previousSelection)) {
			colorList.setSelectedValue(previousSelection, false);
		}
	}

	private void updateSelectedCount() {
		long configuredColors = fullPalette.values().stream()
			.filter(blocks -> blocks.stream().anyMatch(bl -> enabledBlocks.contains(bl.getUniqueKey())))
			.count();

		selectedCountLabel.setText(Lang.t("picker.colors_configured", configuredColors, fullPalette.size()));
	}

	private void saveAndClose() {
		if (enabledBlocks.isEmpty()) {
			JOptionPane.showMessageDialog(
				this,
				Lang.t("picker.no_blocks"),
				Lang.t("dialog.error_title"),
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		try {
			String content = enabledBlocks.stream()
				.sorted()
				.collect(Collectors.joining("\n"));

			Files.writeString(targetFile.toPath(), content);

			confirmed = true;
			dispose();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				this,
				Lang.t("error.blocks_load_failed", e.getMessage()),
				Lang.t("dialog.error_title"),
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private JButton buildSmallButton(String text) {
		JButton btn = new JButton(text) {
			private boolean hovered = false;

			{
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						hovered = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						hovered = false;
						repaint();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(hovered ? new Color(50, 55, 78) : BG_INPUT);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setForeground(TEXT);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return btn;
	}

	private JButton buildPrimaryButton(String text, Color bg, Color fg) {
		JButton btn = new JButton(text) {
			private boolean hovered = false;

			{
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						hovered = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						hovered = false;
						repaint();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(hovered ? bg.brighter() : bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setForeground(fg);
		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return btn;
	}

	private JTextField buildSearchField() {
		JTextField field = new JTextField();
		field.setBackground(BG_INPUT);
		field.setForeground(TEXT);
		field.setCaretColor(ACCENT);
		field.setSelectionColor(SELECTION_BG);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)
		));
		field.setFont(new Font("SansSerif", Font.PLAIN, 12));
		field.setToolTipText(Lang.t("picker.search"));
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { onSearchChanged(); }
			@Override public void removeUpdate(DocumentEvent e) { onSearchChanged(); }
			@Override public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
		});

		return field;
	}

	private JScrollPane buildScrollPane(Component view) {
		JScrollPane scroll = new JScrollPane(view);
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		scroll.getViewport().setBackground(BG_INPUT);
		scroll.setBackground(BG_INPUT);
		scroll.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());
		scroll.getHorizontalScrollBar().setUI(GuiApp.buildScrollBarUi());
		scroll.getVerticalScrollBar().setBackground(new Color(18, 18, 28));
		scroll.getHorizontalScrollBar().setBackground(new Color(18, 18, 28));

		return scroll;
	}

	/**
	 * Загружает палитру из versions/{version}.zip → {version}.json в classpath.
	 * Сортирует цвета по воспринимаемой яркости для удобного визуального восприятия.
	 */
	private Map<Integer, List<BlockData>> loadPalette(String version) {
		String resourcePath = "versions/" + version + ".zip";
		String jsonEntry = version + ".json";

		try (InputStream raw = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (raw == null) {
				return new HashMap<>();
			}

			String json = readEntryFromZip(raw, jsonEntry);

			if (json == null) {
				return new HashMap<>();
			}

			Map<Integer, List<BlockData>> parsed = JsonHelper.GSON.fromJson(
				json,
				new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
			);

			return parsed.entrySet().stream()
				.sorted((a, b) -> Integer.compare(luminance(a.getKey()), luminance(b.getKey())))
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					Map.Entry::getValue,
					(x, y) -> x,
					LinkedHashMap::new
				));
		} catch (IOException e) {
			return new HashMap<>();
		}
	}

	private static String readEntryFromZip(InputStream raw, String entryName) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(raw)) {
			ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(entryName)) {
					return new String(zip.readAllBytes());
				}

				zip.closeEntry();
			}
		}

		return null;
	}

	private static int luminance(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;

		return (r * 299 + g * 587 + b * 114) / 1000;
	}

	private class ColorCellRenderer implements ListCellRenderer<Integer> {

		@Override
		public Component getListCellRendererComponent(
			JList<? extends Integer> list,
			Integer colorRgb,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			if (colorRgb == SUPPORT_SENTINEL) {
				return buildSupportSentinelCell(isSelected);
			}

			JPanel cell = new JPanel(new BorderLayout(8, 0));
			cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			cell.setBackground(
				isSelected ? SELECTION_BG : (index % 2 == 0 ? BG_INPUT : new Color(26, 28, 42))
			);

			JPanel swatch = buildColorSwatch(colorRgb);

			int r = (colorRgb >> 16) & 0xFF;
			int g = (colorRgb >> 8) & 0xFF;
			int b = colorRgb & 0xFF;
			String hex = "#%02X%02X%02X".formatted(r, g, b);

			List<BlockData> blocks = fullPalette.getOrDefault(colorRgb, List.of());
			long enabledCount = blocks.stream().filter(bl -> enabledBlocks.contains(bl.getUniqueKey())).count();

			JLabel hexLabel = new JLabel(hex);
			hexLabel.setForeground(isSelected ? Color.WHITE : TEXT);
			hexLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

			JLabel countLabel = new JLabel(enabledCount + "/" + blocks.size(), SwingConstants.RIGHT);
			countLabel.setForeground(enabledCount > 0 ? SUCCESS : TEXT_DIM);
			countLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

			cell.add(swatch, BorderLayout.WEST);
			cell.add(hexLabel, BorderLayout.CENTER);
			cell.add(countLabel, BorderLayout.EAST);

			return cell;
		}

		private JPanel buildSupportSentinelCell(boolean isSelected) {
			JPanel cell = new JPanel(new BorderLayout(8, 0));
			cell.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)
			));
			cell.setBackground(isSelected ? SELECTION_BG : BG_CARD);

			JLabel icon = new JLabel("🧱");
			icon.setFont(new Font("SansSerif", Font.PLAIN, 16));

			JLabel label = new JLabel(Lang.t("picker.tab_support"));
			label.setForeground(isSelected ? Color.WHITE : ACCENT);
			label.setFont(new Font("SansSerif", Font.BOLD, 12));

			JLabel countLabel = new JLabel(enabledSupportBlocks.size() + " ✓", SwingConstants.RIGHT);
			countLabel.setForeground(enabledSupportBlocks.isEmpty() ? TEXT_DIM : SUCCESS);
			countLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

			cell.add(icon, BorderLayout.WEST);
			cell.add(label, BorderLayout.CENTER);
			cell.add(countLabel, BorderLayout.EAST);

			return cell;
		}


		private JPanel buildColorSwatch(int colorRgb) {
			int baseR = (colorRgb >> 16) & 0xFF;
			int baseG = (colorRgb >> 8) & 0xFF;
			int baseB = colorRgb & 0xFF;

			Color shadeLow = shadeColor(baseR, baseG, baseB, SHADE_LOW);
			Color shadeNormal = shadeColor(baseR, baseG, baseB, SHADE_NORMAL);
			Color shadeHigh = shadeColor(baseR, baseG, baseB, SHADE_HIGH);

			int totalWidth = COLOR_SWATCH_SIZE * 3 + 2;

			return new JPanel() {
				{
					setPreferredSize(new Dimension(totalWidth, COLOR_SWATCH_SIZE));
					setOpaque(false);
				}

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					int h = getHeight() - 4;
					int sliceW = totalWidth / 3;

					g2.setColor(shadeLow);
					g2.fillRoundRect(0, 2, sliceW, h, 3, 3);

					g2.setColor(shadeNormal);
					g2.fillRect(sliceW, 2, sliceW, h);

					g2.setColor(shadeHigh);
					g2.fillRoundRect(sliceW * 2, 2, totalWidth - sliceW * 2, h, 3, 3);

					g2.setColor(new Color(0, 0, 0, 60));
					g2.drawRoundRect(0, 2, totalWidth - 1, h - 1, 3, 3);

					g2.dispose();
				}
			};
		}
	}

	private static Color shadeColor(int r, int g, int b, int modifier) {
		return new Color(r * modifier / 255, g * modifier / 255, b * modifier / 255);
	}

	/**
		* Панель-сетка иконок с корректным переносом строк внутри {@link JScrollPane}.
		*
		* <p>Проблема стандартного {@link FlowLayout}: он вычисляет {@code preferredSize}
		* размещая все элементы в одну строку без учёта ширины контейнера.
		* Из-за этого {@link JScrollPane} видит огромную preferred-ширину и расширяет viewport
		* вместо того чтобы переносить строки.
		*
		* <p>Решение: {@code getScrollableTracksViewportWidth()=true} заставляет JScrollPane
		* растягивать панель по ширине viewport. {@code getPreferredSize()} переопределён —
		* если панель уже имеет реальную ширину, пересчитывает высоту с учётом переноса строк.
		*/
	private static class ScrollableGrid extends JPanel implements Scrollable {

		private static final int UNIT_INCREMENT = 16;
		private static final int BLOCK_INCREMENT = 100;

		ScrollableGrid() {
			super(new FlowLayout(FlowLayout.LEFT, 6, 6));
		}

		/**
		 * Пересчитывает высоту с учётом реальной ширины панели, чтобы FlowLayout
		 * корректно переносил строки при любом изменении размера окна.
		 */
		@Override
		public Dimension getPreferredSize() {
			int panelWidth = getWidth();

			if (panelWidth == 0) {
				return super.getPreferredSize();
			}

			FlowLayout layout = (FlowLayout) getLayout();
			int hgap = layout.getHgap();
			int vgap = layout.getVgap();
			Insets insets = getInsets();
			int maxWidth = panelWidth - insets.left - insets.right;

			int rowWidth = 0;
			int rowHeight = 0;
			int totalHeight = insets.top + insets.bottom + vgap;

			for (int i = 0; i < getComponentCount(); i++) {
				Component child = getComponent(i);

				if (!child.isVisible()) {
					continue;
				}

				Dimension size = child.getPreferredSize();

				if (rowWidth > 0 && rowWidth + hgap + size.width > maxWidth) {
					totalHeight += rowHeight + vgap;
 					rowWidth = 0;
					rowHeight = 0;
				}

				rowWidth += (rowWidth > 0 ? hgap : 0) + size.width;
				rowHeight = Math.max(rowHeight, size.height);
			}

			totalHeight += rowHeight;

			return new Dimension(panelWidth, totalHeight);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return UNIT_INCREMENT;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return BLOCK_INCREMENT;
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

}
