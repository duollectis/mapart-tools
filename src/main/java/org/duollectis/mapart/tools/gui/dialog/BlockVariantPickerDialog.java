package org.duollectis.mapart.tools.gui.dialog;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;
import org.duollectis.mapart.tools.gui.widget.ThemedButton;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

/**
 * Универсальный диалог настройки процентного распределения блоков.
 * Работает в двух режимах:
 * — PALETTE (зелёный акцент): настройка вариантов блока палитры.
 * — SUPPORT (голубой акцент): настройка блоков опоры.
 * Проценты дробные (шаг 0.1), всегда в сумме дают 100%.
 * Кнопка блокировки фиксирует строку. Кнопка "Поровну" сбрасывает к равному распределению.
 */
public class BlockVariantPickerDialog extends JDialog {

	private static final int DIALOG_WIDTH = 620;
	private static final int DIALOG_HEIGHT = 420;
	private static final int ROW_HEIGHT = 40;
	private static final int PCT_COL_WIDTH = 90;
	private static final int LOCK_COL_WIDTH = 44;
	private static final int BAR_COL_WIDTH = 130;

	/** Масштаб для перевода double-процентов в int-веса (0.1% → вес 1). */
	private static final int WEIGHT_SCALE = 1000;

	private static final Color BG = GuiApp.theme.getBgDeep();
	private static final Color CARD = GuiApp.theme.getBgCard();
	private static final Color INPUT = GuiApp.theme.getBgInput();
	private static final Color BORDER = GuiApp.theme.getBorder();
	private static final Color TEXT = GuiApp.theme.getText();
	private static final Color TEXT_DIM = GuiApp.theme.getTextDim();
	private static final Color SUCCESS = GuiApp.theme.getSuccess();

	/** Голубой акцент для режима опоры. */
	private static final Color ACCENT_SUPPORT = new Color(56, 189, 248);

	private static final int MIN_UNLOCKED_FOR_LOCK = 2;

	private final List<EntryRow> rows = new ArrayList<>();
	private final Color accent;
	private final boolean supportMode;
	private EntryTableModel tableModel;
	private JRadioButton randomRadio;
	private boolean confirmed = false;

	/** Конструктор для режима палитры (зелёный акцент). */
	public BlockVariantPickerDialog(
		JFrame parent,
		List<BlockData> activeBlocks,
		WeightedSelector<BlockData> initial
	) {
		super(parent, UpdatableRegistry.translate("variant_picker.title"), true);
		this.accent = GuiApp.theme.getAccent();
		this.supportMode = false;

		initRowsFromSelector(activeBlocks, initial);
		normalizeToHundred();
		buildUi(initial != null ? initial.getMode() : WeightedSelector.Mode.RANDOM);
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setMinimumSize(new Dimension(460, 320));
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	/** Конструктор для режима опоры (голубой акцент). */
	public BlockVariantPickerDialog(
		JFrame parent,
		List<BlockData> supportBlocks,
		SupportBlockSettings initial
	) {
		super(parent, UpdatableRegistry.translate("variant_picker.title"), true);
		this.accent = ACCENT_SUPPORT;
		this.supportMode = true;

		initRowsFromSupportSettings(supportBlocks, initial);
		normalizeToHundred();
		WeightedSelector.Mode mode = initial != null ? initial.getMode() : WeightedSelector.Mode.RANDOM;
		buildUi(mode);
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setMinimumSize(new Dimension(460, 320));
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	/**
	 * Строит селектор из текущих процентов (режим палитры).
	 * Проценты масштабируются в int-веса умножением на {@link #WEIGHT_SCALE}.
	 * Строки с percent ≈ 0 исключаются — эти блоки деактивируются.
	 */
	public WeightedSelector<BlockData> buildSelector() {
		List<WeightedSelector.Entry<BlockData>> entries = rows.stream()
			.filter(r -> r.percent > 0.001)
			.map(r -> new WeightedSelector.Entry<>(r.block, (int) Math.round(r.percent * WEIGHT_SCALE)))
			.toList();

		WeightedSelector.Mode mode = randomRadio.isSelected()
			? WeightedSelector.Mode.RANDOM
			: WeightedSelector.Mode.SEQUENTIAL;

		return new WeightedSelector<>(entries, mode);
	}

	/** Строит настройки опоры из текущих процентов (режим опоры). */
	public SupportBlockSettings buildSupportSettings() {
		List<SupportBlockSettings.Entry> entries = rows.stream()
			.filter(r -> r.percent > 0.001)
			.map(r -> new SupportBlockSettings.Entry(r.block.getId(), (int) Math.round(r.percent * WEIGHT_SCALE)))
			.toList();

		WeightedSelector.Mode mode = randomRadio.isSelected()
			? WeightedSelector.Mode.RANDOM
			: WeightedSelector.Mode.SEQUENTIAL;

		return new SupportBlockSettings(entries, mode);
	}

	/** Возвращает блоки, которым был выставлен 0% — их нужно деактивировать. */
	public List<BlockData> getZeroPercentBlocks() {
		return rows.stream()
			.filter(r -> r.percent <= 0.001)
			.map(r -> r.block)
			.toList();
	}

	private void initRowsFromSelector(List<BlockData> activeBlocks, WeightedSelector<BlockData> initial) {
		if (initial == null) {
			double equalPct = activeBlocks.isEmpty() ? 0.0 : Math.round(1000.0 / activeBlocks.size()) / 10.0;
			double remainder = activeBlocks.isEmpty()
				? 0.0
				: Math.round((100.0 - equalPct * activeBlocks.size()) * 10.0) / 10.0;

			for (int i = 0; i < activeBlocks.size(); i++) {
				rows.add(new EntryRow(activeBlocks.get(i), i == 0 ? equalPct + remainder : equalPct));
			}

			return;
		}

		int totalWeight = initial.getEntries().stream()
			.filter(e -> activeBlocks.stream().anyMatch(b -> b.getUniqueKey().equals(e.value().getUniqueKey())))
			.mapToInt(WeightedSelector.Entry::weight)
			.sum();

		for (BlockData block : activeBlocks) {
			int weight = initial.getEntries().stream()
				.filter(e -> e.value().getUniqueKey().equals(block.getUniqueKey()))
				.mapToInt(WeightedSelector.Entry::weight)
				.findFirst()
				.orElse(1);

			double pct = totalWeight > 0
				? Math.round(weight * 1000.0 / totalWeight) / 10.0
				: 100.0 / activeBlocks.size();

			rows.add(new EntryRow(block, pct));
		}
	}

	private void initRowsFromSupportSettings(List<BlockData> supportBlocks, SupportBlockSettings initial) {
		if (initial == null || initial.getEntries().isEmpty()) {
			double equalPct = supportBlocks.isEmpty() ? 0.0 : 100.0 / supportBlocks.size();

			for (BlockData block : supportBlocks) {
				rows.add(new EntryRow(block, Math.round(equalPct * 10.0) / 10.0));
			}

			return;
		}

		int totalWeight = initial.getEntries().stream()
			.mapToInt(SupportBlockSettings.Entry::weight)
			.sum();

		for (BlockData block : supportBlocks) {
			int weight = initial.getEntries().stream()
				.filter(e -> e.blockId().equals(block.getId()))
				.mapToInt(SupportBlockSettings.Entry::weight)
				.findFirst()
				.orElse(1);

			double pct = totalWeight > 0
				? Math.round(weight * 1000.0 / totalWeight) / 10.0
				: 100.0 / supportBlocks.size();

			rows.add(new EntryRow(block, pct));
		}
	}

	/** Сбрасывает все строки к равному распределению и снимает все замки. */
	void resetToEqual() {
		if (rows.isEmpty()) {
			return;
		}

		rows.forEach(r -> r.locked = false);

		double equalPct = Math.round(1000.0 / rows.size()) / 10.0;
		double remainder = Math.round((100.0 - equalPct * rows.size()) * 10.0) / 10.0;

		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).percent = i == 0 ? equalPct + remainder : equalPct;
		}

		tableModel.fireTableDataChanged();
	}

	private void normalizeToHundred() {
		if (rows.isEmpty()) {
			return;
		}

		double sum = rows.stream().mapToDouble(r -> r.percent).sum();
		double diff = Math.round((100.0 - sum) * 10.0) / 10.0;
		rows.getFirst().percent = Math.round((rows.getFirst().percent + diff) * 10.0) / 10.0;
	}

	/**
	 * Перераспределяет проценты при изменении одной строки.
	 * Остаток делится пропорционально между незафиксированными строками (кроме изменяемой).
	 */
	void redistributeOnChange(int changedRow, double newPct) {
		rows.get(changedRow).percent = newPct;

		double lockedSum = 0.0;

		for (int i = 0; i < rows.size(); i++) {
			if (i != changedRow && rows.get(i).locked) {
				lockedSum += rows.get(i).percent;
			}
		}

		double remaining = Math.round((100.0 - newPct - lockedSum) * 10.0) / 10.0;

		List<EntryRow> freeOthers = new ArrayList<>();

		for (int i = 0; i < rows.size(); i++) {
			if (i != changedRow && !rows.get(i).locked) {
				freeOthers.add(rows.get(i));
			}
		}

		if (freeOthers.isEmpty()) {
			return;
		}

		double othersSum = freeOthers.stream().mapToDouble(r -> r.percent).sum();

		if (othersSum < 0.001) {
			double equalShare = Math.round(remaining / freeOthers.size() * 10.0) / 10.0;
			double rem = Math.round((remaining - equalShare * freeOthers.size()) * 10.0) / 10.0;

			for (int i = 0; i < freeOthers.size(); i++) {
				freeOthers.get(i).percent = Math.max(0.0, i == 0 ? equalShare + rem : equalShare);
			}

			return;
		}

		double distributed = 0.0;

		for (int i = 0; i < freeOthers.size() - 1; i++) {
			double share = Math.round(Math.max(0.0, freeOthers.get(i).percent * remaining / othersSum) * 10.0) / 10.0;
			freeOthers.get(i).percent = share;
			distributed += share;
		}

		freeOthers.getLast().percent = Math.max(0.0, Math.round((remaining - distributed) * 10.0) / 10.0);
	}

	/**
	 * Переключает состояние замка для строки.
	 * Блокирует переключение если незафиксированных останется < 2.
	 */
	void toggleLock(int row) {
		EntryRow entry = rows.get(row);

		if (entry.locked) {
			entry.locked = false;
			tableModel.fireTableDataChanged();
			return;
		}

		long unlockedCount = rows.stream().filter(r -> !r.locked).count();

		if (unlockedCount <= MIN_UNLOCKED_FOR_LOCK) {
			return;
		}

		entry.locked = true;
		tableModel.fireTableDataChanged();
	}

	private void buildUi(WeightedSelector.Mode initialMode) {
		getContentPane().setBackground(BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildFooter(initialMode), BorderLayout.SOUTH);
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(CARD);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
			BorderFactory.createEmptyBorder(12, 16, 12, 16)
		));

		JLabel title = new JLabel(UpdatableRegistry.translate("variant_picker.title"));
		title.setForeground(TEXT);
		title.setFont(new Font("SansSerif", Font.BOLD, 14));

		JLabel hint = new JLabel(UpdatableRegistry.translate("variant_picker.hint"));
		hint.setForeground(TEXT_DIM);
		hint.setFont(new Font("SansSerif", Font.PLAIN, 11));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);
		left.add(title);
		left.add(Box.createVerticalStrut(2));
		left.add(hint);

		header.add(left, BorderLayout.WEST);

		return header;
	}

	private JPanel buildCenter() {
		tableModel = new EntryTableModel(rows, this);
		JTable table = new JTable(tableModel);

		table.setBackground(BG);
		table.setForeground(TEXT);
		table.setGridColor(BORDER);
		table.setRowHeight(ROW_HEIGHT);
		table.setShowHorizontalLines(true);
		table.setShowVerticalLines(false);
		table.setFillsViewportHeight(true);
		table.setFont(new Font("SansSerif", Font.PLAIN, 12));
		table.getTableHeader().setBackground(CARD);
		table.getTableHeader().setForeground(TEXT_DIM);
		table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
		table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
		table.setSelectionBackground(new Color(49, 130, 206, 80));
		table.setSelectionForeground(TEXT);

		table.getColumnModel().getColumn(0).setPreferredWidth(Integer.MAX_VALUE);
		table.getColumnModel().getColumn(1).setPreferredWidth(PCT_COL_WIDTH);
		table.getColumnModel().getColumn(1).setMaxWidth(PCT_COL_WIDTH + 20);
		table.getColumnModel().getColumn(1).setMinWidth(PCT_COL_WIDTH);
		table.getColumnModel().getColumn(2).setPreferredWidth(LOCK_COL_WIDTH);
		table.getColumnModel().getColumn(2).setMaxWidth(LOCK_COL_WIDTH + 10);
		table.getColumnModel().getColumn(2).setMinWidth(LOCK_COL_WIDTH);
		table.getColumnModel().getColumn(3).setPreferredWidth(BAR_COL_WIDTH);
		table.getColumnModel().getColumn(3).setMaxWidth(BAR_COL_WIDTH + 40);
		table.getColumnModel().getColumn(3).setMinWidth(BAR_COL_WIDTH);

		table.getColumnModel().getColumn(0).setCellRenderer(new IdCellRenderer(accent));
		table.getColumnModel().getColumn(1).setCellRenderer(new PctCellRenderer());
		table.getColumnModel().getColumn(1).setCellEditor(new PctCellEditor(tableModel));
		table.getColumnModel().getColumn(2).setCellRenderer(new LockCellRenderer(this));
		table.getColumnModel().getColumn(2).setCellEditor(new LockCellEditor(this));
		table.getColumnModel().getColumn(3).setCellRenderer(new BarCellRenderer(accent));

		// Одиночный клик сразу запускает редактирование % — без двойного нажатия
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = table.columnAtPoint(e.getPoint());
				int row = table.rowAtPoint(e.getPoint());

				if (col == 1 && row >= 0 && !table.isEditing()) {
					table.editCellAt(row, col);
					table.getEditorComponent().requestFocusInWindow();
				}
			}
		});

		InertialScrollPane scroll = new InertialScrollPane(table);
		scroll.setBackground(BG);
		scroll.getViewport().setBackground(BG);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JPanel center = new JPanel(new BorderLayout(0, 0));
		center.setBackground(BG);
		center.add(scroll, BorderLayout.CENTER);

		return center;
	}

	private JPanel buildFooter(WeightedSelector.Mode initialMode) {
		randomRadio = new JRadioButton(UpdatableRegistry.translate("support_picker.mode_random"));
		JRadioButton sequentialRadio = new JRadioButton(UpdatableRegistry.translate("support_picker.mode_sequential"));

		randomRadio.setOpaque(false);
		sequentialRadio.setOpaque(false);
		randomRadio.setForeground(TEXT);
		sequentialRadio.setForeground(TEXT);
		randomRadio.setFont(new Font("SansSerif", Font.PLAIN, 12));
		sequentialRadio.setFont(new Font("SansSerif", Font.PLAIN, 12));

		ButtonGroup group = new ButtonGroup();
		group.add(randomRadio);
		group.add(sequentialRadio);

		if (initialMode == WeightedSelector.Mode.RANDOM) {
			randomRadio.setSelected(true);
		} else {
			sequentialRadio.setSelected(true);
		}

		JLabel modeLabel = new JLabel(UpdatableRegistry.translate("support_picker.mode_label"));
		modeLabel.setForeground(TEXT_DIM);
		modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		modePanel.setOpaque(false);
		modePanel.add(modeLabel);
		modePanel.add(randomRadio);
		modePanel.add(sequentialRadio);

		ThemedButton equalBtn = buildSmallButton(UpdatableRegistry.translate("variant_picker.btn_equal"));
		equalBtn.setIcon(AppIcon.BALANCE.colored(TEXT));
		equalBtn.addActionListener(e -> resetToEqual());

		ThemedButton saveBtn = buildPrimaryButton(UpdatableRegistry.translate("support_picker.btn_save"));
		saveBtn.addActionListener(e -> {
			confirmed = true;
			dispose();
		});

		ThemedButton cancelBtn = buildSmallButton(UpdatableRegistry.translate("support_picker.btn_cancel"));
		cancelBtn.addActionListener(e -> dispose());

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		btnPanel.setOpaque(false);
		btnPanel.add(equalBtn);
		btnPanel.add(cancelBtn);
		btnPanel.add(saveBtn);

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(CARD);
		footer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)
		));
		footer.add(modePanel, BorderLayout.WEST);
		footer.add(btnPanel, BorderLayout.EAST);

		return footer;
	}

	// ── Вспомогательный метод равного распределения ────────────────────────────

	private void distributeEqually(List<?> items, int count) {
		if (count == 0) {
			return;
		}

		double equalPct = Math.round(1000.0 / count) / 10.0;
		double remainder = Math.round((100.0 - equalPct * count) * 10.0) / 10.0;

		for (int i = 0; i < count; i++) {
			rows.add(new EntryRow(null, i == 0 ? equalPct + remainder : equalPct));
		}
	}

	// ── Модель таблицы ─────────────────────────────────────────────────────────

	private static final class EntryTableModel extends AbstractTableModel {

		private final List<EntryRow> rows;
		final BlockVariantPickerDialog dialog;

		private EntryTableModel(List<EntryRow> rows, BlockVariantPickerDialog dialog) {
			this.rows = rows;
			this.dialog = dialog;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int col) {
			return switch (col) {
				case 0 -> UpdatableRegistry.translate("support_picker.col_block");
				case 1 -> "%";
				default -> "";
			};
		}

		@Override
		public Object getValueAt(int row, int col) {
			EntryRow entry = rows.get(row);
			return switch (col) {
				case 0 -> entry.block.getUniqueKey();
				case 1 -> entry.percent;
				case 2 -> entry.locked;
				case 3 -> entry.percent;
				default -> null;
			};
		}

		@Override
		public void setValueAt(Object value, int row, int col) {
			if (col != 1 || !(value instanceof Double newPct)) {
				return;
			}

			double clamped = Math.max(0.0, Math.min(100.0, newPct));
			dialog.redistributeOnChange(row, clamped);
			fireTableDataChanged();
		}

		@Override
		public Class<?> getColumnClass(int col) {
			return switch (col) {
				case 0 -> String.class;
				case 2 -> Boolean.class;
				default -> Double.class;
			};
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return col == 1 || col == 2;
		}
	}

	// ── Рендереры ──────────────────────────────────────────────────────────────

	private static final class IdCellRenderer extends DefaultTableCellRenderer {

		private final Color accentColor;

		private IdCellRenderer(Color accentColor) {
			this.accentColor = accentColor;
		}

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, value, selected, focused, row, col
			);
			label.setFont(new Font("Monospaced", Font.PLAIN, 12));
			label.setForeground(selected ? TEXT : accentColor);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
			label.setOpaque(true);
			return label;
		}
	}

	private static final class PctCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			String text = value instanceof Double d ? String.format("%.1f%%", d) : "0.0%";
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, text, selected, focused, row, col
			);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setFont(new Font("Monospaced", Font.BOLD, 13));
			label.setForeground(TEXT);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setOpaque(true);
			return label;
		}
	}

	private static final class LockCellRenderer implements TableCellRenderer {

		private final BlockVariantPickerDialog dialog;

		private LockCellRenderer(BlockVariantPickerDialog dialog) {
			this.dialog = dialog;
		}

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			boolean locked = value instanceof Boolean b && b;
			long unlockedCount = dialog.rows.stream().filter(r -> !r.locked).count();
			boolean canLock = locked || unlockedCount > MIN_UNLOCKED_FOR_LOCK;

			Color iconColor = locked
				? new Color(255, 200, 50)
				: (canLock ? TEXT_DIM : GuiApp.theme.getBgInput());
			JLabel label = new JLabel();
			label.setIcon(locked ? AppIcon.LOCK.colored(iconColor) : AppIcon.UNLOCK.colored(iconColor));
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setOpaque(true);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setCursor(canLock && UiAnimator.handCursorEnabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			return label;
		}
	}

	private static final class LockCellEditor extends AbstractCellEditor implements TableCellEditor {

		private final BlockVariantPickerDialog dialog;
		private int editingRow = -1;
		private final JLabel label = new JLabel();

		private LockCellEditor(BlockVariantPickerDialog dialog) {
			this.dialog = dialog;
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setOpaque(true);
			label.setBackground(BG);
			UiAnimator.applyHandCursor(label);
			label.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (editingRow >= 0) {
						dialog.toggleLock(editingRow);
					}

					stopCellEditing();
				}
			});
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean selected, int row, int col
		) {
			editingRow = row;
			boolean locked = value instanceof Boolean b && b;
			Color iconColor = locked ? new Color(255, 200, 50) : TEXT_DIM;
			label.setIcon(locked ? AppIcon.LOCK.colored(iconColor) : AppIcon.UNLOCK.colored(iconColor));
			return label;
		}

		@Override
		public Object getCellEditorValue() {
			return editingRow >= 0 ? dialog.rows.get(editingRow).locked : false;
		}
	}

	private static final class BarCellRenderer implements TableCellRenderer {

		private final Color accentColor;

		private BarCellRenderer(Color accentColor) {
			this.accentColor = accentColor;
		}

		@Override
			public Component getTableCellRendererComponent(
				JTable table, Object value, boolean selected, boolean focused, int row, int col
			) {
				double pct = value instanceof Double d ? d : 0.0;
	
				return new JPanel() {
					{
						setOpaque(true);
						setBackground(selected ? table.getSelectionBackground() : BG);
					}
	
					@Override
					protected void paintComponent(Graphics g) {
						super.paintComponent(g);
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	
						int margin = 8;
						int barH = 10;
						int barY = (getHeight() - barH) / 2;
						int maxW = getWidth() - margin * 2;
						int fillW = (int) (maxW * pct / 100.0);
	
						g2.setColor(INPUT);
						g2.fillRoundRect(margin, barY, maxW, barH, 6, 6);
	
						if (fillW > 0) {
							g2.setColor(pct >= 50.0 ? SUCCESS : accentColor);
							g2.fillRoundRect(margin, barY, fillW, barH, 6, 6);
						}
	
						g2.setColor(BORDER);
						g2.setStroke(new BasicStroke(1f));
						g2.drawRoundRect(margin, barY, maxW, barH, 6, 6);
	
						g2.dispose();
					}
				};
			}
		}
	
		private static final class PctCellEditor extends AbstractCellEditor implements TableCellEditor {
	
			private final JSpinner spinner = new JSpinner(new SpinnerNumberModel(50.0, 0.0, 100.0, 0.1));
			private final EntryTableModel model;
			private int editingRow = -1;
			private boolean ignoreChange = false;
	
			private PctCellEditor(EntryTableModel model) {
				this.model = model;
				spinner.setBackground(INPUT);
				spinner.setFont(new Font("Monospaced", Font.PLAIN, 12));
	
				JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0");
				spinner.setEditor(editor);
				editor.getTextField().setBackground(INPUT);
				editor.getTextField().setForeground(TEXT);
				editor.getTextField().setCaretColor(model.dialog.accent);
	
				ChangeListener changeListener = e -> {
					if (ignoreChange || editingRow < 0) {
						return;
					}
	
					double newPct = (Double) spinner.getValue();
					model.dialog.rows.get(editingRow).locked = true;
					model.setValueAt(newPct, editingRow, 1);
				};
	
				spinner.addChangeListener(changeListener);
			}
	
			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean selected, int row, int col
			) {
				editingRow = row;
				ignoreChange = true;
				spinner.setValue(value instanceof Double d ? d : 50.0);
				ignoreChange = false;
	
				SwingUtilities.invokeLater(() -> {
					JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) spinner.getEditor();
					ed.getTextField().selectAll();
				});
	
				return spinner;
			}
	
			@Override
			public Object getCellEditorValue() {
				return spinner.getValue();
			}
	
			@Override
			public boolean stopCellEditing() {
				if (editingRow >= 0) {
					double newPct = (Double) spinner.getValue();
					model.dialog.rows.get(editingRow).locked = true;
					model.setValueAt(newPct, editingRow, 1);
				}
	
				return super.stopCellEditing();
			}
		}
	
		// ── UI-фабрики ─────────────────────────────────────────────────────────────
	
		private ThemedButton buildPrimaryButton(String text) {
			return new ThemedButton(text, ThemedButton.Style.PRIMARY, false);
		}

		private ThemedButton buildSmallButton(String text) {
			return new ThemedButton(text, ThemedButton.Style.THEMED, false);
		}
	
		// ── Данные строки ──────────────────────────────────────────────────────────
	
		private static final class EntryRow {
	
			BlockData block;
			double percent;
			boolean locked;
	
			EntryRow(BlockData block, double percent) {
				this.block = block;
				this.percent = percent;
				this.locked = false;
			}
		}
	}
