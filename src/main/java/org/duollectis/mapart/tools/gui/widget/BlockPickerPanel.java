package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.Brightness;
import org.duollectis.mapart.tools.converter.MapColorTable;
import org.duollectis.mapart.tools.converter.StaircaseMode;
import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;
import org.duollectis.mapart.tools.gui.block.BlockIconLoader;
import org.duollectis.mapart.tools.gui.dialog.BlockVariantPickerDialog;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;
import org.duollectis.mapart.tools.utils.RGBUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Панель выбора блоков палитры, открываемая через {@link PanelNavigator}.
 * Левая колонка — список цветов палитры (свотч + счётчик включённых блоков).
 * Правая колонка — блоки выбранного цвета с иконками текстур и чекбоксами.
 * Вкладка «Опора» — выбор блоков-опор с настройкой весов.
 */
public class BlockPickerPanel extends JPanel implements NavigablePanel {

	private static final int COLOR_SWATCH_SIZE = 30;
	private static final int COLOR_ROW_HEIGHT = 46;
	private static final int BLOCK_ICON_SIZE = 32;
	private static final int BLOCK_ROW_HEIGHT = 44;
	private static final int LEFT_PANEL_WIDTH = 190;
	private static final int BRIGHTNESS_ROW_HEIGHT = 32;
	private static final int SUPPORT_COLOR_ID = -2;

	private final MainWindow window;
	private final Map<Integer, Map<Brightness, List<BlockData>>> paletteByColor;
	private final Optional<BlockIconLoader> iconLoader;
	private final Set<Brightness> allowedBrightnesses;
	private final Runnable onSaved;

	private int selectedColorId = -1;
	private Brightness selectedBrightness = Brightness.NORMAL;
	private boolean gridView = false;
	private boolean splitBrightness = false;

	private JPanel colorListPanel;
	private JPanel colorColumn;
	private BlockListPanel blockListPanel;
	private JButton blockWeightsBtn;
	private final AnimatedFloat blockFadeAnim = new AnimatedFloat(1f);
	private String filterText = "";

	private final Set<String> workingEnabled;
	private final Set<String> disabledBrightnesses = new HashSet<>();
	private final Map<String, WeightedSelector<BlockData>> workingSelectors;
	private SupportBlockSettings workingSupport;

	public BlockPickerPanel(
		MainWindow window,
		Map<Integer, Map<Brightness, List<BlockData>>> paletteByColor,
		Optional<BlockIconLoader> iconLoader,
		StaircaseMode staircaseMode,
		Set<String> enabledBlocks,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		SupportBlockSettings supportSettings,
		Runnable onSaved
	) {
		this.window = window;
		this.paletteByColor = paletteByColor;
		this.iconLoader = iconLoader;
		this.allowedBrightnesses = staircaseMode.getAllowedBrightnesses();
		this.workingEnabled = new HashSet<>(enabledBlocks);
		this.workingSelectors = new HashMap<>(blockSelectors);
		this.workingSupport = supportSettings;
		this.onSaved = onSaved;

		if (!paletteByColor.isEmpty()) {
			selectedColorId = paletteByColor.keySet().stream().min(Integer::compareTo).orElse(-1);
		}
		selectedBrightness = allowedBrightnesses.contains(Brightness.NORMAL)
			? Brightness.NORMAL
			: allowedBrightnesses.iterator().next();

		buildUi();
	}

	@Override
	public String getNavTitle() {
		return UpdatableRegistry.translate("picker.title_short");
	}

	private void buildUi() {
		setLayout(new BorderLayout());
		setOpaque(false);
		add(buildMainContent(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}


	private JPanel buildMainContent() {
		JPanel content = new JPanel(new BorderLayout());
		content.setOpaque(false);
		content.add(buildColorColumn(), BorderLayout.WEST);
		content.add(buildBlockColumn(), BorderLayout.CENTER);
		return content;
	}

	private JComponent buildColorColumn() {
		colorListPanel = new JPanel();
		colorListPanel.setLayout(new BoxLayout(colorListPanel, BoxLayout.Y_AXIS));
		colorListPanel.setBackground(MainWindow.CARD());
		colorListPanel.setOpaque(true);
		UpdatableRegistry.onThemeAnimFrame(() -> colorListPanel.setBackground(MainWindow.CARD()));
		refreshColorList();
		JScrollPane scroll = new InertialScrollPane(colorListPanel);
		scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, MainWindow.BORDER()));
		UpdatableRegistry.onThemeAnimFrame(() -> scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, MainWindow.BORDER())));
		colorColumn = new JPanel(new BorderLayout());
		colorColumn.setOpaque(false);
		int w = resolveColorColumnWidth();
		colorColumn.setPreferredSize(new Dimension(w, 0));
		colorColumn.setMinimumSize(new Dimension(w, 0));
		colorColumn.setMaximumSize(new Dimension(w, Integer.MAX_VALUE));
		colorColumn.add(scroll, BorderLayout.CENTER);
		return colorColumn;
	}

	private JPanel buildSupportColorEntry() {
		List<BlockData> candidates = paletteByColor.values().stream()
			.flatMap(byBrightness -> byBrightness.values().stream())
			.flatMap(List::stream)
			.filter(b -> !b.isNeedSupport())
			.collect(Collectors.collectingAndThen(
				Collectors.toMap(BlockData::getId, b -> b, (a, b) -> a),
				map -> map.values().stream().sorted(Comparator.comparing(BlockData::getId)).toList()
			));
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JPanel row = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				boolean selected = selectedColorId == SUPPORT_COLOR_ID;
				Color base = selected ? MainWindow.BG() : MainWindow.CARD();
				Color bg = blendColors(base, MainWindow.BG(), hoverAnim.get() * 0.4f);
				g2.setColor(bg);
				g2.fillRect(0, 0, getWidth(), getHeight());
				if (selected) {
					g2.setColor(MainWindow.ACCENT());
					g2.fillRect(0, 0, 3, getHeight());
				}
				g2.setColor(MainWindow.BORDER());
				g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
				g2.dispose();
			}
		};
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(0, COLOR_ROW_HEIGHT));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, COLOR_ROW_HEIGHT));
		row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
		UpdatableRegistry.onThemeAnimFrame(row::repaint);
		JLabel iconLabel = new JLabel(AppIcon.MAGNET.colored(16, MainWindow.ACCENT()));
		UpdatableRegistry.onThemeAnimFrame(() -> iconLabel.setIcon(AppIcon.MAGNET.colored(16, MainWindow.ACCENT())));
		int supportCount = workingSupport != null ? workingSupport.getEntries().size() : 0;
		JLabel counter = new JLabel(supportCount + "/" + candidates.size());
		counter.setFont(new Font("SansSerif", Font.PLAIN, 10));
		counter.setForeground(supportCount > 0 ? MainWindow.SUCCESS() : MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> {
			int cnt = workingSupport != null ? workingSupport.getEntries().size() : 0;
			counter.setText(cnt + "/" + candidates.size());
			counter.setForeground(cnt > 0 ? MainWindow.SUCCESS() : MainWindow.TEXT_DIM());
		});
		row.add(iconLabel, BorderLayout.WEST);
		row.add(counter, BorderLayout.EAST);
		UiAnimator.applyHandCursor(row);
		AppTooltip.install(row, UpdatableRegistry.translate("picker.configure_support"));
		row.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> row.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> row.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				selectedColorId = SUPPORT_COLOR_ID;
				refreshColorList();
				refreshBlockList();
			}
		});
		return row;
	}

	private JPanel buildBlockColumn() {
		JPanel column = new JPanel(new BorderLayout());
		column.setOpaque(false);
		blockListPanel = new BlockListPanel(blockFadeAnim);
		blockListPanel.setBackground(MainWindow.BG());
		blockListPanel.setOpaque(true);
		UpdatableRegistry.onThemeAnimFrame(() -> blockListPanel.setBackground(MainWindow.BG()));
		JScrollPane scroll = new InertialScrollPane(blockListPanel);
		scroll.getViewport().setOpaque(true);
		scroll.getViewport().setBackground(MainWindow.BG());
		UpdatableRegistry.onThemeAnimFrame(() -> scroll.getViewport().setBackground(MainWindow.BG()));
		scroll.setOpaque(true);
		scroll.setBackground(MainWindow.BG());
		UpdatableRegistry.onThemeAnimFrame(() -> scroll.setBackground(MainWindow.BG()));
		scroll.setBorder(BorderFactory.createEmptyBorder());
		JPanel topBar = new JPanel(new BorderLayout(0, 0));
		topBar.setOpaque(false);
		topBar.add(buildBlockFilterField(), BorderLayout.CENTER);
		topBar.add(buildViewToggleBar(), BorderLayout.EAST);
		column.add(topBar, BorderLayout.NORTH);
		column.add(scroll, BorderLayout.CENTER);
		refreshBlockList();
		return column;
	}

	private JPanel buildBlockFilterField() {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.CARD());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(MainWindow.BORDER());
				g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
			}
		};
		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
		UpdatableRegistry.onThemeAnimFrame(wrapper::repaint);
		JTextField field = new JTextField();
		field.setOpaque(false);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(MainWindow.BORDER(), 1, true),
			BorderFactory.createEmptyBorder(3, 8, 3, 8)
		));
		field.setFont(new Font("SansSerif", Font.PLAIN, 12));
		field.setForeground(MainWindow.TEXT());
		field.setCaretColor(MainWindow.TEXT());
		UpdatableRegistry.onThemeAnimFrame(() -> {
			field.setForeground(MainWindow.TEXT());
			field.setCaretColor(MainWindow.TEXT());
			field.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(MainWindow.BORDER(), 1, true),
				BorderFactory.createEmptyBorder(3, 8, 3, 8)
			));
		});
		String placeholder = UpdatableRegistry.translate("picker.filter_placeholder");
		field.putClientProperty("placeholder", placeholder);
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			private void onChanged() {
				filterText = field.getText().trim().toLowerCase();
				refreshBlockList();
			}
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onChanged(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onChanged(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChanged(); }
		});
		wrapper.add(field, BorderLayout.CENTER);
		return wrapper;
	}
	private JPanel buildViewToggleBar() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.CARD());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(MainWindow.BORDER());
				g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
			}
		};
		bar.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(bar::repaint);
		bar.add(buildViewToggleButton(bar));
		bar.add(buildSplitBrightnessToggleButton());
		return bar;
	}

	private JButton buildViewToggleButton(JPanel bar) {
		AnimatedFloat toggleAnim = new AnimatedFloat(0f);
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float h = toggleAnim.get();
				Color bg = blendColors(MainWindow.CARD(), MainWindow.BG(), h);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				Icon icon = gridView ? AppIcon.LIST.colored(14, MainWindow.TEXT()) : AppIcon.GRID.colored(14, MainWindow.TEXT());
				int ix = (getWidth() - 14) / 2;
				int iy = (getHeight() - 14) / 2;
				icon.paintIcon(this, g2, ix, iy);
				g2.dispose();
			}
		};
		btn.setPreferredSize(new Dimension(28, 28));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(btn::repaint);
		UiAnimator.applyHandCursor(btn);
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				toggleAnim.animateTo(1f, 150, v -> btn.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				toggleAnim.animateTo(0f, 200, v -> btn.repaint());
			}
		});
		btn.addActionListener(e -> {
			gridView = !gridView;
			refreshBlockList();
			bar.repaint();
		});
		return btn;
	}

	private JButton buildSplitBrightnessToggleButton() {
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		AnimatedFloat activeAnim = new AnimatedFloat(0f);
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float h = hoverAnim.get();
				float a = activeAnim.get();
				Color bg = blendColors(blendColors(MainWindow.CARD(), MainWindow.BG(), h), MainWindow.ACCENT(), a * 0.3f);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				int sw = 8;
				int gap = 2;
				int totalW = sw * 3 + gap * 2;
				int ox = (getWidth() - totalW) / 2;
				int oy = (getHeight() - sw) / 2;
				Color dimC = blendColors(MainWindow.TEXT_DIM(), MainWindow.ACCENT(), a);
				int baseRgb = 0x7B9B6A;
				g2.setColor(new Color(RGBUtils.scaleRGB(baseRgb, 180)));
				g2.fillRoundRect(ox, oy, sw, sw, 3, 3);
				g2.setColor(new Color(RGBUtils.scaleRGB(baseRgb, 220)));
				g2.fillRoundRect(ox + sw + gap, oy, sw, sw, 3, 3);
				g2.setColor(new Color(RGBUtils.scaleRGB(baseRgb, 255)));
				g2.fillRoundRect(ox + (sw + gap) * 2, oy, sw, sw, 3, 3);
				if (splitBrightness) {
					g2.setColor(MainWindow.ACCENT());
					g2.setStroke(new BasicStroke(1.5f));
					g2.drawLine(ox + sw + gap - 1, oy - 1, ox + sw + gap - 1, oy + sw + 1);
					g2.drawLine(ox + (sw + gap) * 2 - 1, oy - 1, ox + (sw + gap) * 2 - 1, oy + sw + 1);
				}
				g2.dispose();
			}
		};
		btn.setPreferredSize(new Dimension(42, 28));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(btn::repaint);
		UiAnimator.applyHandCursor(btn);
		AppTooltip.install(btn, UpdatableRegistry.translate("picker.split_brightness_tooltip"));
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 150, v -> btn.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 200, v -> btn.repaint());
			}
		});
		btn.addActionListener(e -> {
			splitBrightness = !splitBrightness;
			if (splitBrightness) {
				activeAnim.animateTo(1f, 200, v -> btn.repaint());
			} else {
				activeAnim.animateTo(0f, 200, v -> btn.repaint());
				selectedBrightness = allowedBrightnesses.contains(Brightness.NORMAL) ? Brightness.NORMAL : allowedBrightnesses.iterator().next();
			}
			refreshColorList();
			refreshBlockList();
		});
		return btn;
	}

	private JPanel buildBottomBar() {
		ThemedButton saveBtn = new ThemedButton(UpdatableRegistry.translate("picker.save"), ThemedButton.Style.PRIMARY, false);
		ThemedButton cancelBtn = new ThemedButton(UpdatableRegistry.translate("picker.cancel"), ThemedButton.Style.THEMED, false);
		UpdatableRegistry.registerLang("picker.save", saveBtn::setText);
		UpdatableRegistry.registerLang("picker.cancel", cancelBtn::setText);
		saveBtn.addActionListener(e -> onSave());
		cancelBtn.addActionListener(e -> window.navigateBack());
		JPanel bar = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.BG());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, MainWindow.BORDER()),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)
		));
		UpdatableRegistry.onThemeAnimFrame(() -> bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, MainWindow.BORDER()),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)
		)));
		bar.add(cancelBtn, BorderLayout.WEST);
		bar.add(saveBtn, BorderLayout.EAST);
		return bar;
	}

	private void refreshAll() {
		refreshColorList();
		refreshBlockList();
	}

	private void refreshColorList() {
		colorListPanel.removeAll();
		colorListPanel.add(buildSupportColorEntry());
		buildPaletteColorRows();
		if (colorColumn != null) {
			int w = resolveColorColumnWidth();
			colorColumn.setPreferredSize(new Dimension(w, 0));
			colorColumn.setMinimumSize(new Dimension(w, 0));
			colorColumn.setMaximumSize(new Dimension(w, Integer.MAX_VALUE));
			colorColumn.revalidate();
		}
		colorListPanel.revalidate();
		colorListPanel.repaint();
	}

	private int resolveColorColumnWidth() {
		int n = splitBrightness ? allowedBrightnesses.size() : 1;
		return Math.max(LEFT_PANEL_WIDTH, n * (COLOR_SWATCH_SIZE + 8) + Math.max(0, n - 1) * 4 + 20);
	}

	private void buildPaletteColorRows() {
		paletteByColor.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> colorListPanel.add(buildColorRow(entry.getKey(), entry.getValue())));
	}


	private long countEnabled(List<BlockData> blocks, Brightness brightness) {
		String key = brightness.name();
		return blocks.stream()
			.filter(b -> workingEnabled.contains(b.getUniqueKey() + "#" + key) || workingEnabled.contains(b.getUniqueKey()))
			.count();
	}

	private boolean isBrightnessEnabled(int colorId, Brightness brightness, Map<Brightness, List<BlockData>> byBrightness) {
		return !disabledBrightnesses.contains(colorId + "#" + brightness.name());
	}

	private Brightness resolveClickedBrightness(int mouseX, int swatchWidth, List<Brightness> brightnessList) {
		if (brightnessList.isEmpty()) return null;
		int size = COLOR_SWATCH_SIZE;
		int ox = (swatchWidth - size) / 2;
		int relX = mouseX - ox;
		if (relX < 0 || relX >= size) return null;
		int sliceW = size / brightnessList.size();
		int idx = relX / sliceW;
		idx = Math.min(idx, brightnessList.size() - 1);
		return brightnessList.get(idx);
	}

	private void toggleBrightnessBlocks(int colorId, Brightness brightness, Map<Brightness, List<BlockData>> byBrightness) {
		String key = colorId + "#" + brightness.name();
		if (disabledBrightnesses.contains(key)) {
			disabledBrightnesses.remove(key);
		} else {
			disabledBrightnesses.add(key);
		}
	}

	private JPanel buildColorRow(int colorId, Map<Brightness, List<BlockData>> byBrightness) {
		int n = splitBrightness ? allowedBrightnesses.size() : 1;
		int colWidth = n * (COLOR_SWATCH_SIZE + 8) + Math.max(0, n - 1) * 4 + 20;
		boolean[] expanded = {false};
		int accordionH = allowedBrightnesses.size() * 28 + 4;
		JPanel brPanel = new JPanel();
		brPanel.setLayout(new BoxLayout(brPanel, BoxLayout.Y_AXIS));
		brPanel.setOpaque(false);
		brPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		List<Brightness> sortedBr = allowedBrightnesses.stream().sorted(Comparator.comparingInt(Brightness::getModifier)).toList();
		sortedBr.forEach(br -> {
			List<BlockData> blocks = byBrightness.getOrDefault(br, List.of());
			brPanel.add(buildBrightnessToggleRow(colorId, br, blocks, byBrightness));
		});
		AnimatedFloat expandAnim = new AnimatedFloat(0f);
		int[] currentH = {0};
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Container parent = getParent();
				int w = (parent == null || parent.getWidth() == 0) ? LEFT_PANEL_WIDTH : parent.getWidth();
				return new Dimension(w, COLOR_ROW_HEIGHT + currentH[0]);
			}
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, COLOR_ROW_HEIGHT + currentH[0]);
			}
		};
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel mainRow = new JPanel(new BorderLayout(4, 0));
		mainRow.setOpaque(false);
		mainRow.setPreferredSize(new Dimension(0, COLOR_ROW_HEIGHT));
		mainRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, COLOR_ROW_HEIGHT));
		mainRow.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
		JPanel swatchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		swatchRow.setOpaque(false);
		swatchRow.setPreferredSize(new Dimension(colWidth, COLOR_ROW_HEIGHT));
		Runnable[] toggleAccordion = {null};
		if (splitBrightness) {
			allowedBrightnesses.forEach(br -> {
				List<BlockData> blocks = byBrightness.getOrDefault(br, List.of());
				long enabled = countEnabled(blocks, br);
				swatchRow.add(buildBrightnessSwatchButton(colorId, br, blocks, enabled));
			});
		} else {
			swatchRow.add(buildMergedSwatchButton(colorId, byBrightness, expanded, toggleAccordion));
		}
		JPanel clipWrapper = new JPanel(null) {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(0, currentH[0]);
			}
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, currentH[0]);
			}
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setClip(0, 0, getWidth(), currentH[0]);
				super.paintComponent(g2);
				g2.dispose();
			}
		};
		clipWrapper.setOpaque(false);
		brPanel.setBounds(0, 0, 1000, accordionH);
		clipWrapper.add(brPanel);
		wrapper.add(clipWrapper, BorderLayout.SOUTH);
		toggleAccordion[0] = () -> {
			expanded[0] = !expanded[0];
			if (expanded[0]) {
				expandAnim.animateTo(1f, 200, v -> {
					currentH[0] = (int)(accordionH * v);
					brPanel.setBounds(0, 0, clipWrapper.getWidth(), accordionH);
					clipWrapper.revalidate();
					wrapper.revalidate();
					colorListPanel.revalidate();
					colorListPanel.repaint();
				}, () -> {
					currentH[0] = accordionH;
					brPanel.setBounds(0, 0, clipWrapper.getWidth(), accordionH);
					wrapper.revalidate();
					colorListPanel.revalidate();
				});
			} else {
				expandAnim.animateTo(0f, 200, v -> {
					currentH[0] = (int)(accordionH * v);
					clipWrapper.revalidate();
					wrapper.revalidate();
					colorListPanel.revalidate();
					colorListPanel.repaint();
				}, () -> {
					currentH[0] = 0;
					clipWrapper.revalidate();
					wrapper.revalidate();
					colorListPanel.revalidate();
				});
			}
		};
		mainRow.add(swatchRow, BorderLayout.WEST);
		wrapper.add(mainRow, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel buildBrightnessToggleRow(int colorId, Brightness brightness, List<BlockData> blocks, Map<Brightness, List<BlockData>> byBrightness) {
		int baseRgb = resolveRgbForColorId(colorId);
		int scaledRgb = baseRgb >= 0 ? RGBUtils.scaleRGB(baseRgb, brightness.getModifier()) : -1;
		Color swatchColor = scaledRgb >= 0 ? new Color(scaledRgb) : Color.GRAY;
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JPanel row = new JPanel(new BorderLayout(6, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float h = hoverAnim.get();
				if (h > 0) {
					Color hoverBg = new Color(MainWindow.ACCENT().getRed(), MainWindow.ACCENT().getGreen(), MainWindow.ACCENT().getBlue(), (int)(h * 30));
					g2.setColor(hoverBg);
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}
				g2.dispose();
				super.paintComponent(g);
			}
		};
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(0, 26));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		JPanel mini = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean on = isBrightnessEnabled(colorId, brightness, byBrightness);
				g2.setColor(on ? swatchColor : swatchColor.darker());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
				if (!on) {
					g2.setColor(new Color(0, 0, 0, 100));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
				}
				g2.setColor(MainWindow.BORDER());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
				g2.dispose();
			}
		};
		mini.setPreferredSize(new Dimension(18, 18));
		mini.setMinimumSize(new Dimension(18, 18));
		mini.setMaximumSize(new Dimension(18, 18));
		mini.setOpaque(false);
		String brName = brightness.name().charAt(0) + brightness.name().substring(1).toLowerCase();
		JLabel label = new JLabel(brName);
		label.setFont(new Font("SansSerif", Font.PLAIN, 10));
		UpdatableRegistry.onThemeAnimFrame(() -> {
			boolean on = isBrightnessEnabled(colorId, brightness, byBrightness);
			label.setForeground(on ? MainWindow.ACCENT() : MainWindow.TEXT_DIM());
			mini.repaint();
			row.repaint();
		});
		label.setForeground(isBrightnessEnabled(colorId, brightness, byBrightness) ? MainWindow.ACCENT() : MainWindow.TEXT_DIM());
		row.add(mini, BorderLayout.WEST);
		row.add(label, BorderLayout.CENTER);
		UiAnimator.applyHandCursor(row);
		row.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> row.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> row.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleBrightnessBlocks(colorId, brightness, byBrightness);
				mini.repaint();
				label.setForeground(isBrightnessEnabled(colorId, brightness, byBrightness) ? MainWindow.ACCENT() : MainWindow.TEXT_DIM());
			}
		});
		return row;
	}
	private JPanel buildMergedSwatchButton(int colorId, Map<Brightness, List<BlockData>> byBrightness, boolean[] expanded, Runnable[] toggleAccordion) {
		int baseRgb = resolveRgbForColorId(colorId);
		List<Brightness> brightnessList = allowedBrightnesses.stream().sorted(Comparator.comparingInt(Brightness::getModifier)).toList();
		List<Color> colors = brightnessList.stream().map(br -> {
			int scaled = baseRgb >= 0 ? RGBUtils.scaleRGB(baseRgb, br.getModifier()) : -1;
			return scaled >= 0 ? new Color(scaled) : Color.GRAY;
		}).toList();
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		AnimatedFloat chevronAnim = new AnimatedFloat(0f);
		JPanel swatch = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean selected = selectedColorId == colorId;
				float hover = hoverAnim.get();
				int size = (int) (COLOR_SWATCH_SIZE + hover * 4);
				int ox = (getWidth() - size) / 2;
				int oy = (getHeight() - size) / 2;
				if (colors.size() == 1) {
					boolean brEnabled = isBrightnessEnabled(colorId, brightnessList.get(0), byBrightness);
					g2.setColor(brEnabled ? colors.get(0) : colors.get(0).darker());
					g2.fillRoundRect(ox, oy, size, size, 8, 8);
				} else {
					Shape clip = new java.awt.geom.RoundRectangle2D.Float(ox, oy, size, size, 8, 8);
					g2.setClip(clip);
					int sliceW = size / colors.size();
					for (int i = 0; i < colors.size(); i++) {
						Brightness br = brightnessList.get(i);
						boolean brEnabled = isBrightnessEnabled(colorId, br, byBrightness);
						int sx = ox + i * sliceW;
						int sw = (i == colors.size() - 1) ? size - i * sliceW : sliceW;
						g2.setColor(colors.get(i));
						g2.fillRect(sx, oy, sw, size);
						if (!brEnabled) {
							g2.setColor(new Color(0, 0, 0, 140));
							g2.fillRect(sx, oy, sw, size);
						}
					}
					g2.setClip(null);
				}
				if (selected) {
					g2.setColor(MainWindow.ACCENT());
					g2.setStroke(new BasicStroke(2f));
					g2.drawRoundRect(ox, oy, size - 1, size - 1, 8, 8);
				} else {
					g2.setColor(new Color(MainWindow.BORDER().getRed(), MainWindow.BORDER().getGreen(), MainWindow.BORDER().getBlue(), (int)(80 + hover * 80)));
					g2.setStroke(new BasicStroke(1f));
					g2.drawRoundRect(ox, oy, size - 1, size - 1, 8, 8);
				}
				long totalMerged = brightnessList.stream().flatMap(br -> byBrightness.getOrDefault(br, List.of()).stream()).map(BlockData::getId).distinct().count();
				long enabledMerged = brightnessList.stream().flatMap(br -> byBrightness.getOrDefault(br, List.of()).stream().filter(b -> workingEnabled.contains(b.getUniqueKey() + "#" + br.name()))).map(BlockData::getId).distinct().count();
				String countText = enabledMerged + "/" + totalMerged;
				g2.setFont(new Font("SansSerif", Font.BOLD, 8));
				FontMetrics fm = g2.getFontMetrics();
				int tx = ox + (size - fm.stringWidth(countText)) / 2;
				int ty = oy + size - 3;
				g2.setColor(new Color(0, 0, 0, 120));
				g2.drawString(countText, tx + 1, ty + 1);
				g2.setColor(Color.WHITE);
				g2.drawString(countText, tx, ty);
				if (selected) {
					Icon chevron = AppIcon.CHEVRON_RIGHT.colored(12, Color.WHITE);
					int iconW = chevron.getIconWidth();
					int iconH = chevron.getIconHeight();
					double cx = ox + size / 2.0;
					double cy = oy + size / 2.0;
					double angle = chevronAnim.get() * Math.PI / 2;
					Graphics2D gr = (Graphics2D) g2.create();
					gr.rotate(angle, cx, cy);
					chevron.paintIcon(null, gr, (int)(cx - iconW / 2.0), (int)(cy - iconH / 2.0));
					gr.dispose();
				}
				g2.dispose();
			}
		};
		int swatchOuter = COLOR_SWATCH_SIZE + 8;
		swatch.setPreferredSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setMinimumSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setMaximumSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(swatch::repaint);
		UiAnimator.applyHandCursor(swatch);
		swatch.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 150, v -> swatch.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 200, v -> swatch.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				if (selectedColorId == colorId) {
					if (toggleAccordion[0] != null) {
						toggleAccordion[0].run();
					}
				chevronAnim.animateTo(expanded[0] ? 1f : 0f, 200, v -> swatch.repaint());
				} else {
					selectedColorId = colorId;
					refreshColorList();
					refreshBlockList();
				}
			}
		});
		return swatch;
	}
	private JButton buildBlockWeightsButton() {
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bg = blendColors(MainWindow.CARD(), MainWindow.ACCENT(), hoverAnim.get() * 0.25f);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				Icon icon = AppIcon.WEIGHTS.colored(14, MainWindow.TEXT_DIM());
				icon.paintIcon(this, g2, (getWidth() - 14) / 2, (getHeight() - 14) / 2);
				g2.dispose();
			}
		};
		btn.setPreferredSize(new Dimension(26, 26));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setOpaque(false);
		btn.setVisible(false);
		UpdatableRegistry.onThemeAnimFrame(btn::repaint);
		UiAnimator.applyHandCursor(btn);
		AppTooltip.install(btn, UpdatableRegistry.translate("picker.weights_tooltip"));
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> btn.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> btn.repaint());
			}
		});
		btn.addActionListener(e -> {
			Map<Brightness, List<BlockData>> byBrightness = paletteByColor.get(selectedColorId);
			if (byBrightness != null) {
				openColorWeightsPicker(selectedColorId, byBrightness);
			}
		});
		return btn;
	}

	private JButton buildColorWeightsButton(int colorId, Map<Brightness, List<BlockData>> byBrightness) {
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bg = blendColors(MainWindow.CARD(), MainWindow.ACCENT(), hoverAnim.get() * 0.25f);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
				Icon icon = AppIcon.BALANCE.colored(10, MainWindow.TEXT_DIM());
				icon.paintIcon(this, g2, (getWidth() - 10) / 2, (getHeight() - 10) / 2);
				g2.dispose();
			}
		};
		btn.setPreferredSize(new Dimension(20, 14));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(btn::repaint);
		UiAnimator.applyHandCursor(btn);
		AppTooltip.install(btn, UpdatableRegistry.translate("picker.weights_tooltip"));
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> btn.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> btn.repaint());
			}
		});
		btn.addActionListener(e -> openColorWeightsPicker(colorId, byBrightness));
		return btn;
	}
	private JPanel buildBrightnessSwatchButton(int colorId, Brightness brightness, List<BlockData> blocks, long enabledCount) {
		int baseRgb = resolveRgbForColorId(colorId);
		int scaledRgb = baseRgb >= 0 ? RGBUtils.scaleRGB(baseRgb, brightness.getModifier()) : -1;
		Color swatchColor = scaledRgb >= 0 ? new Color(scaledRgb) : Color.GRAY;
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JPanel swatch = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean selected = selectedColorId == colorId && selectedBrightness == brightness;
				float hover = hoverAnim.get();
				int size = (int) (COLOR_SWATCH_SIZE + hover * 4);
				int ox = (getWidth() - size) / 2;
				int oy = (getHeight() - size) / 2;
				g2.setColor(swatchColor);
				g2.fillRoundRect(ox, oy, size, size, 8, 8);
				if (selected) {
					g2.setColor(MainWindow.ACCENT());
					g2.setStroke(new BasicStroke(2f));
					g2.drawRoundRect(ox, oy, size - 1, size - 1, 8, 8);
				} else {
					g2.setColor(new Color(MainWindow.BORDER().getRed(), MainWindow.BORDER().getGreen(), MainWindow.BORDER().getBlue(), (int)(80 + hover * 80)));
					g2.setStroke(new BasicStroke(1f));
					g2.drawRoundRect(ox, oy, size - 1, size - 1, 8, 8);
				}
				String countText = enabledCount + "/" + blocks.size();
				g2.setFont(new Font("SansSerif", Font.BOLD, 8));
				FontMetrics fm = g2.getFontMetrics();
				int tx = ox + (size - fm.stringWidth(countText)) / 2;
				int ty = oy + size - 3;
				g2.setColor(new Color(0, 0, 0, 120));
				g2.drawString(countText, tx + 1, ty + 1);
				g2.setColor(Color.WHITE);
				g2.drawString(countText, tx, ty);
				g2.dispose();
			}
		};
		int swatchOuter = COLOR_SWATCH_SIZE + 8;
		swatch.setPreferredSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setMinimumSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setMaximumSize(new Dimension(swatchOuter, swatchOuter));
		swatch.setOpaque(false);
		UpdatableRegistry.onThemeAnimFrame(swatch::repaint);
		UiAnimator.applyHandCursor(swatch);
		swatch.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 150, v -> swatch.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 200, v -> swatch.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				selectedColorId = colorId;
				selectedBrightness = brightness;
				refreshColorList();
				refreshBlockList();
			}
		});
		return swatch;
	}

	private JPanel buildSwatchPanel(Color color) {
		JPanel swatch = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.setColor(MainWindow.BORDER());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
				g2.dispose();
			}
		};
		swatch.setPreferredSize(new Dimension(COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE));
		swatch.setMinimumSize(new Dimension(COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE));
		swatch.setMaximumSize(new Dimension(COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE));
		swatch.setOpaque(false);
		return swatch;
	}
	private void refreshBlockList() {
		if (blockWeightsBtn != null) {
			blockWeightsBtn.setVisible(selectedColorId >= 0);
		}
		blockFadeAnim.animateTo(0f, 100, v -> blockListPanel.repaint(), () -> {
			blockListPanel.removeAll();
			buildPaletteBlockRows();
			blockListPanel.revalidate();
			blockListPanel.repaint();
			blockFadeAnim.set(0f);
			blockFadeAnim.animateTo(1f, 150, v -> blockListPanel.repaint());
		});
	}
	private void buildPaletteBlockRows() {
		if (selectedColorId == SUPPORT_COLOR_ID) {
			return;
		}
		if (selectedColorId < 0) {
			addNoBlocksLabel();
			return;
		}
		Map<Brightness, List<BlockData>> byBrightness = paletteByColor.get(selectedColorId);
		if (byBrightness == null) {
			addNoBlocksLabel();
			return;
		}
		if (!splitBrightness) {
			List<BlockData> allBlocks = allowedBrightnesses.stream()
				.flatMap(br -> byBrightness.getOrDefault(br, List.of()).stream())
				.collect(Collectors.collectingAndThen(
					Collectors.toMap(BlockData::getId, b -> b, (a, b) -> a),
					map -> map.values().stream().sorted(Comparator.comparing(BlockData::getId)).toList()
				));
			if (allBlocks.isEmpty()) { addNoBlocksLabel(); return; }
			List<BlockData> filteredAll = filterText.isEmpty() ? allBlocks : allBlocks.stream().filter(b -> b.getId().toLowerCase().contains(filterText)).toList();
			List<BlockData> enabledAll = filteredAll.stream().filter(b -> allowedBrightnesses.stream().anyMatch(br -> workingEnabled.contains(b.getId() + "#" + br.name()) || workingEnabled.contains(b.getUniqueKey() + "#" + br.name()))).toList();
			List<BlockData> disabledAll = filteredAll.stream().filter(b -> allowedBrightnesses.stream().noneMatch(br -> workingEnabled.contains(b.getId() + "#" + br.name()) || workingEnabled.contains(b.getUniqueKey() + "#" + br.name()))).toList();
			if (gridView) {
				blockListPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 6, 6));
				blockListPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
				if (!enabledAll.isEmpty()) {
					blockListPanel.add(buildGroupHeader("picker.group_enabled"));
					for (BlockData block : enabledAll) { blockListPanel.add(buildBlockCard(block, () -> allowedBrightnesses.stream().anyMatch(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name())); }), () -> { boolean on = allowedBrightnesses.stream().anyMatch(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name())); }); allowedBrightnesses.forEach(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); brBlocks.stream().filter(b -> b.getId().equals(block.getId())).forEach(b -> { String key = b.getUniqueKey() + "#" + br.name(); if (on) { workingEnabled.remove(key); } else { workingEnabled.add(key); } }); }); })); }
				}
				if (!disabledAll.isEmpty()) {
					blockListPanel.add(buildGroupHeader("picker.group_disabled"));
					for (BlockData block : disabledAll) { blockListPanel.add(buildBlockCard(block, () -> allowedBrightnesses.stream().anyMatch(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name())); }), () -> { boolean on = allowedBrightnesses.stream().anyMatch(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name())); }); allowedBrightnesses.forEach(br -> { List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of()); brBlocks.stream().filter(b -> b.getId().equals(block.getId())).forEach(b -> { String key = b.getUniqueKey() + "#" + br.name(); if (on) { workingEnabled.remove(key); } else { workingEnabled.add(key); } }); }); })); }
				}
			} else {
				blockListPanel.setLayout(new BoxLayout(blockListPanel, BoxLayout.Y_AXIS));
				blockListPanel.setBorder(null);
				if (!enabledAll.isEmpty()) {
					blockListPanel.add(buildGroupHeader("picker.group_enabled"));
					for (BlockData block : enabledAll) { blockListPanel.add(buildMergedBrightnessBlockRow(block, byBrightness)); }
				}
				if (!disabledAll.isEmpty()) {
					blockListPanel.add(buildGroupHeader("picker.group_disabled"));
					for (BlockData block : disabledAll) { blockListPanel.add(buildMergedBrightnessBlockRow(block, byBrightness)); }
				}
			}
			return;
		}
		List<BlockData> blocks = byBrightness.getOrDefault(selectedBrightness, List.of());
		if (blocks.isEmpty()) { addNoBlocksLabel(); return; }
		String brightnessKey = selectedBrightness.name();
		List<BlockData> filtered = filterText.isEmpty() ? blocks : blocks.stream().filter(b -> b.getUniqueKey().toLowerCase().contains(filterText) || b.getId().toLowerCase().contains(filterText)).toList();
		List<BlockData> enabled = filtered.stream().filter(b -> workingEnabled.contains(b.getUniqueKey() + "#" + brightnessKey)).toList();
		List<BlockData> disabled = filtered.stream().filter(b -> !workingEnabled.contains(b.getUniqueKey() + "#" + brightnessKey)).toList();
		if (gridView) {
			blockListPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 6, 6));
			blockListPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			if (!enabled.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_enabled"));
				for (BlockData block : enabled) { blockListPanel.add(buildBlockCard(block, () -> workingEnabled.contains(block.getUniqueKey() + "#" + selectedBrightness.name()), () -> { String key = block.getUniqueKey() + "#" + selectedBrightness.name(); if (workingEnabled.contains(key)) { workingEnabled.remove(key); } else { workingEnabled.add(key); } })); }
			}
			if (!disabled.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_disabled"));
				for (BlockData block : disabled) { blockListPanel.add(buildBlockCard(block, () -> workingEnabled.contains(block.getUniqueKey() + "#" + selectedBrightness.name()), () -> { String key = block.getUniqueKey() + "#" + selectedBrightness.name(); if (workingEnabled.contains(key)) { workingEnabled.remove(key); } else { workingEnabled.add(key); } })); }
			}
		} else {
			blockListPanel.setLayout(new BoxLayout(blockListPanel, BoxLayout.Y_AXIS));
			blockListPanel.setBorder(null);
			if (!enabled.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_enabled"));
				for (BlockData block : enabled) { blockListPanel.add(buildPaletteBlockRow(block, selectedBrightness)); }
			}
			if (!disabled.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_disabled"));
				for (BlockData block : disabled) { blockListPanel.add(buildPaletteBlockRow(block, selectedBrightness)); }
			}
		}
	}

	private JPanel buildMergedBrightnessBlockRow(BlockData block, Map<Brightness, List<BlockData>> byBrightness) {
		boolean anyEnabled = allowedBrightnesses.stream().anyMatch(br -> {
			List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of());
			return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name()));
		});
		return buildBlockRow(
			block,
			block.getId(),
			() -> allowedBrightnesses.stream().anyMatch(br -> {
				List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of());
				return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name()));
			}),
			() -> {
				boolean on = allowedBrightnesses.stream().anyMatch(br -> {
					List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of());
					return brBlocks.stream().anyMatch(b -> b.getId().equals(block.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + br.name()));
				});
				allowedBrightnesses.forEach(br -> {
					List<BlockData> brBlocks = byBrightness.getOrDefault(br, List.of());
					brBlocks.stream().filter(b -> b.getId().equals(block.getId())).forEach(b -> {
						String key = b.getUniqueKey() + "#" + br.name();
						if (on) { workingEnabled.remove(key); } else { workingEnabled.add(key); }
					});
				});
			},
			null
		);
	}





	private void buildSupportBlockRows() {
		List<BlockData> candidates = paletteByColor.values().stream()
			.flatMap(byBrightness -> byBrightness.values().stream())
			.flatMap(List::stream)
			.filter(b -> !b.isNeedSupport())
			.collect(Collectors.collectingAndThen(
				Collectors.toMap(BlockData::getId, b -> b, (a, b) -> a),
				map -> map.values().stream().sorted(Comparator.comparing(BlockData::getId)).toList()
			));
		if (candidates.isEmpty()) {
			addNoBlocksLabel();
			return;
		}
		List<BlockData> visible = filterText.isEmpty() ? candidates : candidates.stream().filter(b -> b.getId().toLowerCase().contains(filterText)).toList();
		List<BlockData> enabledSupport = visible.stream().filter(this::isSupportEnabled).toList();
		List<BlockData> disabledSupport = visible.stream().filter(b -> !isSupportEnabled(b)).toList();
		if (gridView) {
			blockListPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 6, 6));
			blockListPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			if (!enabledSupport.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_enabled"));
				for (BlockData block : enabledSupport) { blockListPanel.add(buildBlockCard(block, () -> isSupportEnabled(block), () -> toggleSupportBlock(block, !isSupportEnabled(block)))); }
			}
			if (!disabledSupport.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_disabled"));
				for (BlockData block : disabledSupport) { blockListPanel.add(buildBlockCard(block, () -> isSupportEnabled(block), () -> toggleSupportBlock(block, !isSupportEnabled(block)))); }
			}
		} else {
			blockListPanel.setLayout(new BoxLayout(blockListPanel, BoxLayout.Y_AXIS));
			blockListPanel.setBorder(null);
			if (!enabledSupport.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_enabled"));
				for (BlockData block : enabledSupport) { blockListPanel.add(buildSupportBlockRow(block)); }
			}
			if (!disabledSupport.isEmpty()) {
				blockListPanel.add(buildGroupHeader("picker.group_disabled"));
				for (BlockData block : disabledSupport) { blockListPanel.add(buildSupportBlockRow(block)); }
			}
		}
	}
	private JPanel buildGroupHeader(String langKey) {
		JPanel header = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Container parent = getParent();
				int width = (parent == null) ? super.getPreferredSize().width : parent.getWidth();
				return new Dimension(width, 28);
			}
			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(MainWindow.CARD());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		header.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 8));
		UpdatableRegistry.onThemeAnimFrame(header::repaint);
		JLabel label = new JLabel(UpdatableRegistry.translate(langKey));
		label.setFont(new Font("SansSerif", Font.BOLD, 10));
		label.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.registerLang(langKey, label::setText);
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));
		header.add(label, BorderLayout.CENTER);
		return header;
	}
	private void addNoBlocksLabel() {
		JLabel label = new JLabel(UpdatableRegistry.translate("picker.no_blocks"), SwingConstants.CENTER);
		label.setForeground(MainWindow.TEXT_DIM());
		label.setFont(new Font("SansSerif", Font.PLAIN, 12));
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(MainWindow.TEXT_DIM()));
		blockListPanel.add(Box.createVerticalStrut(20));
		blockListPanel.add(label);
	}

	private JPanel buildPaletteBlockRow(BlockData block, Brightness brightness) {
		String enabledKey = block.getUniqueKey() + "#" + brightness.name();
		JButton configBtn = buildVariantConfigButton(block, brightness);
		configBtn.setVisible(workingEnabled.contains(enabledKey));
		return buildBlockRow(
			block,
			block.getUniqueKey(),
			() -> workingEnabled.contains(enabledKey),
			() -> {
				boolean on = workingEnabled.contains(enabledKey);
				if (on) {
					workingEnabled.remove(enabledKey);
				} else {
					workingEnabled.add(enabledKey);
				}
				configBtn.setVisible(!on);
			},
			configBtn
		);
	}

	private JPanel buildSupportBlockRow(BlockData block) {
		return buildBlockRow(
			block,
			block.getId(),
			() -> isSupportEnabled(block),
			() -> toggleSupportBlock(block, !isSupportEnabled(block)),
			null
		);
	}
	private JPanel buildBlockCard(BlockData block, java.util.function.BooleanSupplier isEnabled, Runnable onToggle) {
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		int cardSize = BLOCK_ICON_SIZE + 20;
		JPanel card = new JPanel(new BorderLayout(0, 4)) {
			@Override
			protected void paintComponent(Graphics g) {
				boolean on = isEnabled.getAsBoolean();
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float h = hoverAnim.get();
				Color bg = blendColors(MainWindow.CARD(), MainWindow.BG(), h * 0.6f);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				if (on) {
					g2.setColor(MainWindow.ACCENT());
					g2.setStroke(new BasicStroke(2f));
					g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
				} else {
					g2.setColor(new Color(MainWindow.BORDER().getRed(), MainWindow.BORDER().getGreen(), MainWindow.BORDER().getBlue(), (int)(60 + h * 100)));
					g2.setStroke(new BasicStroke(1f));
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				}
				g2.dispose();
			}
		};
		card.setOpaque(false);
		card.putClientProperty("blockId", block.getId());
		card.setPreferredSize(new Dimension(cardSize, cardSize));
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
		AppTooltip.install(card, block.getId());
		UpdatableRegistry.onThemeAnimFrame(card::repaint);
		JLabel iconLabel = buildBlockIconLabel(block);
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		card.add(iconLabel, BorderLayout.CENTER);
		UiAnimator.applyHandCursor(card);
		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> card.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> card.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				onToggle.run(); refreshBlockList();
			}
		});
		return card;
	}

	private JPanel buildBlockRow(BlockData block, String label, java.util.function.BooleanSupplier isEnabled, Runnable onToggle, JComponent trailing) {
		AnimatedFloat hoverAnim = new AnimatedFloat(0f);
		JPanel row = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean on = isEnabled.getAsBoolean();
				float h = hoverAnim.get();
				Color bg = blendColors(MainWindow.CARD(), MainWindow.BG(), h * 0.5f);
				g2.setColor(bg);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(MainWindow.BORDER());
				g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
				if (on) {
					g2.setColor(MainWindow.ACCENT());
					g2.fillRect(0, 0, 3, getHeight());
				}
				g2.dispose();
			}
		};
		row.setOpaque(false);
		row.putClientProperty("blockId", block.getId());
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setPreferredSize(new Dimension(0, BLOCK_ROW_HEIGHT));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, BLOCK_ROW_HEIGHT));
		row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
		UpdatableRegistry.onThemeAnimFrame(row::repaint);
		UiAnimator.applyHandCursor(row);
		JLabel iconLabel = buildBlockIconLabel(block);
		JLabel nameLabel = new JLabel(label);
		nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		nameLabel.setForeground(MainWindow.TEXT());
		UpdatableRegistry.onThemeAnimFrame(() -> nameLabel.setForeground(MainWindow.TEXT()));
		JPanel textPanel = new JPanel(new BorderLayout());
		textPanel.setOpaque(false);
		textPanel.add(nameLabel, BorderLayout.CENTER);
		row.add(iconLabel, BorderLayout.WEST);
		row.add(textPanel, BorderLayout.CENTER);
		if (trailing != null) {
			row.add(trailing, BorderLayout.EAST);
		}
		row.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hoverAnim.animateTo(1f, 120, v -> row.repaint());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hoverAnim.animateTo(0f, 180, v -> row.repaint());
			}
			@Override
			public void mouseClicked(MouseEvent e) {
				onToggle.run(); refreshBlockList();
			}
		});
		return row;
	}

	private JPanel buildSupportConfigButton(List<BlockData> candidates) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 8));
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		ThemedButton btn = new ThemedButton(UpdatableRegistry.translate("picker.configure_support"), ThemedButton.Style.THEMED, false);
		UpdatableRegistry.registerLang("picker.configure_support", btn::setText);
		btn.addActionListener(e -> openSupportPicker(candidates));
		wrapper.add(btn, BorderLayout.CENTER);
		return wrapper;
	}

	private JLabel buildBlockIconLabel(BlockData block) {
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(BLOCK_ICON_SIZE, BLOCK_ICON_SIZE));
		label.setMinimumSize(new Dimension(BLOCK_ICON_SIZE, BLOCK_ICON_SIZE));
		label.setMaximumSize(new Dimension(BLOCK_ICON_SIZE, BLOCK_ICON_SIZE));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		iconLoader.flatMap(loader -> loader.getIcon(block)).ifPresentOrElse(
			label::setIcon,
			() -> {
				label.setText("?");
				label.setForeground(MainWindow.TEXT_DIM());
				label.setFont(new Font("SansSerif", Font.BOLD, 14));
			}
		);
		return label;
	}

	private JButton buildVariantConfigButton(BlockData block, Brightness brightness) {
		JButton btn = new JButton(AppIcon.EDIT.colored(14, MainWindow.TEXT_DIM()));
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setPreferredSize(new Dimension(28, 28));
		AppTooltip.install(btn, UpdatableRegistry.translate("variant_picker.btn_tooltip"));
		UiAnimator.applyHandCursor(btn);
		UpdatableRegistry.onThemeAnimFrame(() -> btn.setIcon(AppIcon.EDIT.colored(14, MainWindow.TEXT_DIM())));
		btn.addActionListener(e -> openVariantPicker(block, brightness));
		return btn;
	}


	private void openColorWeightsPicker(int colorId, Map<Brightness, List<BlockData>> byBrightness) {
		List<BlockData> enabledBlocks = allowedBrightnesses.stream()
			.flatMap(br -> byBrightness.getOrDefault(br, List.of()).stream()
				.filter(b -> workingEnabled.contains(b.getUniqueKey() + "#" + br.name())))
			.toList();
		if (enabledBlocks.isEmpty()) {
			return;
		}
		String selectorKey = colorId + "#" + selectedBrightness.name();
		WeightedSelector<BlockData> current = workingSelectors.getOrDefault(selectorKey, null);
		BlockVariantPickerDialog dialog = new BlockVariantPickerDialog(
			(JFrame) SwingUtilities.getWindowAncestor(this),
			enabledBlocks,
			current
		);
		if (dialog.isConfirmed()) {
			workingSelectors.put(selectorKey, dialog.buildSelector());
			for (BlockData zero : dialog.getZeroPercentBlocks()) {
				allowedBrightnesses.forEach(br -> workingEnabled.remove(zero.getUniqueKey() + "#" + br.name()));
			}
			refreshAll();
		}
	}
	private void openVariantPicker(BlockData clickedBlock, Brightness brightness) {
		String enabledKey = clickedBlock.getUniqueKey() + "#" + brightness.name();
		String selectorKey = clickedBlock.getId() + "#" + brightness.name();
		List<BlockData> variants = paletteByColor.values().stream()
			.flatMap(byBrightness -> byBrightness.getOrDefault(brightness, List.of()).stream())
			.filter(b -> b.getId().equals(clickedBlock.getId()) && workingEnabled.contains(b.getUniqueKey() + "#" + brightness.name()))
			.toList();
		if (variants.isEmpty()) {
			return;
		}
		WeightedSelector<BlockData> current = workingSelectors.containsKey(selectorKey)
			? workingSelectors.get(selectorKey)
			: workingSelectors.get(clickedBlock.getId());
		BlockVariantPickerDialog dialog = new BlockVariantPickerDialog(
			(JFrame) SwingUtilities.getWindowAncestor(this),
			variants,
			current
		);
		if (dialog.isConfirmed()) {
			workingSelectors.put(selectorKey, dialog.buildSelector());
			for (BlockData zero : dialog.getZeroPercentBlocks()) {
				workingEnabled.remove(zero.getUniqueKey() + "#" + brightness.name());
			}
			refreshAll();
		}
	}

	private void openSupportPicker(List<BlockData> candidates) {
		List<BlockData> activeSupportBlocks = candidates.stream()
			.filter(this::isSupportEnabled)
			.toList();
		if (activeSupportBlocks.isEmpty()) {
			return;
		}
		BlockVariantPickerDialog dialog = new BlockVariantPickerDialog(
			(JFrame) SwingUtilities.getWindowAncestor(this),
			activeSupportBlocks,
			workingSupport
		);
		if (dialog.isConfirmed()) {
			workingSupport = dialog.buildSupportSettings();
			refreshColorList();
		}
	}

	private boolean isSupportEnabled(BlockData block) {
		return workingSupport != null
			&& workingSupport.getEntries().stream().anyMatch(e -> e.blockId().equals(block.getId()));
	}

	private void toggleSupportBlock(BlockData block, boolean enable) {
		List<SupportBlockSettings.Entry> entries = workingSupport != null
			? new ArrayList<>(workingSupport.getEntries())
			: new ArrayList<>();
		if (enable) {
			boolean alreadyPresent = entries.stream().anyMatch(e -> e.blockId().equals(block.getId()));
			if (!alreadyPresent) {
				entries.add(new SupportBlockSettings.Entry(block.getId(), 1000));
			}
		} else {
			entries.removeIf(e -> e.blockId().equals(block.getId()));
		}
		WeightedSelector.Mode mode = workingSupport != null ? workingSupport.getMode() : WeightedSelector.Mode.RANDOM;
		workingSupport = new SupportBlockSettings(entries, mode);
	}

	private static Color blendColors(Color a, Color b, float t) {
		int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		return new Color(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, bl)));
	}
	private int resolveRgbForColorId(int colorId) {
		return MapColorTable.RGB_TO_BASE_ID.entrySet().stream()
			.filter(e -> e.getValue().equals(colorId))
			.mapToInt(Map.Entry::getKey)
			.findFirst()
			.orElse(-1);
	}

	private void onSave() {
		disabledBrightnesses.forEach(key -> {
			String[] parts = key.split("#");
			if (parts.length != 2) return;
			try {
				int cid = Integer.parseInt(parts[0]);
				Brightness br = Brightness.valueOf(parts[1]);
				Map<Brightness, List<BlockData>> byBr = paletteByColor.getOrDefault(cid, Map.of());
				byBr.getOrDefault(br, List.of()).forEach(b -> workingEnabled.remove(b.getUniqueKey() + "#" + br.name()));
			} catch (IllegalArgumentException ignored) {}
		});
		window.applyBlockPickerResult(workingEnabled, workingSelectors, workingSupport);
		if (onSaved != null) {
			onSaved.run();
		}
		window.navigateBack();
	}


	private static final class BlockListPanel extends JPanel implements javax.swing.Scrollable {
		private final AnimatedFloat fadeAnim;

		BlockListPanel(AnimatedFloat fadeAnim) { this.fadeAnim = fadeAnim; }

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(MainWindow.BG());
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.dispose();
		}

		@Override
		protected void paintChildren(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAnim.get()));
			super.paintChildren(g2);
			g2.dispose();
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 12; }
		@Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 60; }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}
}
