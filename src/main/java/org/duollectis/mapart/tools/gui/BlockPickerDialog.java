package org.duollectis.mapart.tools.gui;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.utils.JsonHelper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	private final File targetFile;
	private boolean confirmed = false;

	public BlockPickerDialog(JFrame parent, String version, File targetFile, Set<String> initialEnabled) {
		super(parent, Lang.t("picker.title", version), true);
		this.version = version;
		this.targetFile = targetFile;
		this.enabledBlocks = new HashSet<>(initialEnabled);
		this.fullPalette = loadPalette(version);
		this.iconLoader = BlockIconLoader.create(version).orElse(null);

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

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.add(selectAll);
		row.add(Box.createHorizontalStrut(6));
		row.add(selectNone);
		row.add(Box.createHorizontalGlue());

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

		int r = (colorRgb >> 16) & 0xFF;
		int g = (colorRgb >> 8) & 0xFF;
		int b = colorRgb & 0xFF;
		String hex = "#%02X%02X%02X".formatted(r, g, b);

		List<BlockData> blocks = fullPalette.get(colorRgb);
		long enabled = blocks.stream().filter(bl -> enabledBlocks.contains(bl.getId())).count();

		colorInfoLabel.setText("%s  ·  %d  ·  %d".formatted(hex, blocks.size(), enabled));

		if (iconMode) {
			rebuildIconGrid(blocks);
		} else {
			rebuildBlockCheckboxes(blocks);
		}
	}

	private void rebuildBlockCheckboxes(List<BlockData> blocks) {
		String filter = searchField.getText().strip().toLowerCase();

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
						? block.getId() + "  ⚠ требует опору"
						: block.getId();

				ModernCheckBox cb = new ModernCheckBox(label);
				cb.setSelected(enabledBlocks.contains(block.getId()));
				cb.setFont(new Font("Monospaced", Font.PLAIN, 12));
				cb.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
				cb.setAlignmentX(Component.LEFT_ALIGNMENT);

				if (block.isNeedSupport()) {
					cb.setForeground(new Color(200, 160, 80));
				}

				cb.addActionListener(e -> {
					if (cb.isSelected()) {
						enabledBlocks.add(block.getId());
					} else {
						enabledBlocks.remove(block.getId());
					}

					updateSelectedCount();
					refreshColorInfo();
				});

				blockCheckboxes.put(block.getId(), cb);
				blocksPanel.add(cb);
			});

		blocksPanel.add(Box.createVerticalGlue());
		blocksPanel.revalidate();
		blocksPanel.repaint();
	}

	/**
	 * Строит сетку иконок блоков с кнопками-переключателями.
	 * Каждая ячейка — JToggleButton с иконкой 32×32 и tooltip с id блока.
	 */
	private void rebuildIconGrid(List<BlockData> blocks) {
		String filter = searchField.getText().strip().toLowerCase();

		blockCheckboxes.clear();

		ScrollableGrid grid = new ScrollableGrid();
		grid.setBackground(BG_INPUT);

		blocks.stream()
			.sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
			.filter(block -> filter.isBlank() || block.getId().contains(filter))
			.forEach(block -> grid.add(buildIconCell(block)));

		blocksScroll.setViewportView(grid);
		blocksScroll.revalidate();
		blocksScroll.repaint();
	}

	private JPanel buildIconCell(BlockData block) {
		boolean selected = enabledBlocks.contains(block.getId());
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

				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setSelected(selected);
		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setToolTipText(block.isNeedSupport() ? block.getId() + "  ⚠ требует опору" : block.getId());
		btn.setPreferredSize(new Dimension(cellSize, cellSize));

		if (icon.isPresent()) {
			btn.setIcon(icon.get());
		} else {
			btn.setText(shortName(block.getId()));
			btn.setFont(new Font("Monospaced", Font.PLAIN, 9));
			btn.setForeground(TEXT_DIM);
		}

		btn.addActionListener(e -> {
			if (btn.isSelected()) {
				enabledBlocks.add(block.getId());
			} else {
				enabledBlocks.remove(block.getId());
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

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		String name = colon >= 0 ? blockId.substring(colon + 1) : blockId;

		return name.length() > 8 ? name.substring(0, 8) : name;
	}

	private void refreshBlocksView() {
		Integer selected = colorList.getSelectedValue();

		if (selected == null) {
			return;
		}

		List<BlockData> blocks = fullPalette.get(selected);

		if (iconMode) {
			rebuildIconGrid(blocks);
		} else {
			blockCheckboxes.forEach((id, cb) -> cb.setSelected(enabledBlocks.contains(id)));
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

		if (colorRgb == null) {
			return;
		}

		List<BlockData> blocks = fullPalette.getOrDefault(colorRgb, List.of());

		if (enabled) {
			blocks.forEach(b -> enabledBlocks.add(b.getId()));
		} else {
			blocks.forEach(b -> enabledBlocks.remove(b.getId()));
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
		colorListModel.clear();
		boolean hideConfigured = hideConfiguredFilter.isSelected();

		fullPalette.forEach((colorRgb, blocks) -> {
			boolean hasEnabled = blocks.stream().anyMatch(bl -> enabledBlocks.contains(bl.getId()));

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
	}

	private void updateSelectedCount() {
		selectedCountLabel.setText(Lang.t("label.blocks_count", enabledBlocks.size()));
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
			long enabledCount = blocks.stream().filter(bl -> enabledBlocks.contains(bl.getId())).count();

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
