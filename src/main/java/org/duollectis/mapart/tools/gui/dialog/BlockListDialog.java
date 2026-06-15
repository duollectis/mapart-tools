package org.duollectis.mapart.tools.gui.dialog;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.block.BlockIconLoader;
import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;
import org.duollectis.mapart.tools.gui.widget.ModernToggleButton;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

/**
 * Диалог со списком блоков, использованных в результате дизеринга.
 * Показывает иконку, ID блока, количество пикселей и кнопку удаления.
 * Блок опоры отображается отдельной строкой в конце без кнопки удаления.
 * При удалении блока вызывает колбэк {@code onBlockRemoved} — MainWindow
 * убирает блок из палитры, сохраняет файл и запускает реконвертацию.
 * После завершения реконвертации MainWindow вызывает {@link #refresh(Map, int)}.
 */
public class BlockListDialog extends JDialog {

	private static final int DIALOG_WIDTH = 620;
	private static final int DIALOG_HEIGHT = 580;
	private static final int ROW_HEIGHT = 40;
	private static final int ICON_COL_WIDTH = 44;
	private static final int COUNT_COL_WIDTH = 160;
	private static final int DELETE_COL_WIDTH = 70;
	private static final int STACK_SIZE = 64;
	private static final int SHULKER_SLOTS = 27;

	private static final Color BG = GuiApp.theme.getBgDeep();
	private static final Color CARD = GuiApp.theme.getBgCard();
	private static final Color INPUT = GuiApp.theme.getBgInput();
	private static final Color BORDER = GuiApp.theme.getBorder();
	private static final Color ACCENT = GuiApp.theme.getAccent();
	private static final Color TEXT = GuiApp.theme.getText();
	private static final Color TEXT_DIM = GuiApp.theme.getTextDim();
	private static final Color ERROR = GuiApp.theme.getError();

	private final Consumer<String> onBlockRemoved;
	private final String version;
	private final String supportBlockId;

	private CountMode countMode = CountMode.SHULKERS;
	private BlockTableModel tableModel;
	private JTable table;
	private TableRowSorter<BlockTableModel> sorter;
	private JLabel totalLabel;
	private BlockIconLoader iconLoader;

	public BlockListDialog(
		JFrame parent,
		Map<BlockData, Integer> blockCounts,
		String version,
		String supportBlockId,
		int supportBlockCount,
		Consumer<String> onBlockRemoved
	) {
		super(parent, UpdatableRegistry.translate("blocklist.title"), false);

		this.version = version;
		this.supportBlockId = supportBlockId;
		this.onBlockRemoved = onBlockRemoved;
		this.iconLoader = BlockIconLoader.create(version).orElse(null);

		buildUi(blockCounts, supportBlockCount);
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setMinimumSize(new Dimension(440, 300));
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	/**
	 * Обновляет содержимое таблицы после реконвертации.
	 * Вызывается из MainWindow на EDT после завершения нового дизеринга.
	 *
	 * @param blockCounts       новая карта блок → количество
	 * @param supportBlockCount новое количество блоков опоры
	 */
	public void refresh(Map<BlockData, Integer> blockCounts, int supportBlockCount) {
		List<BlockRow> newRows = buildRows(blockCounts, supportBlockId, supportBlockCount);

		if (iconLoader != null) {
			for (BlockRow row : newRows) {
				row.icon = iconLoader.getIcon(row.block).orElse(null);
			}
		}

		tableModel.setRows(newRows);
		totalLabel.setText(UpdatableRegistry.translate("blocklist.total", newRows.stream().filter(r -> !r.isSupportBlock).count()));
	}

	private void buildUi(Map<BlockData, Integer> blockCounts, int supportBlockCount) {
		getContentPane().setBackground(BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(blockCounts), BorderLayout.NORTH);
		add(buildTablePanel(blockCounts, supportBlockCount), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);
	}

	private JPanel buildHeader(Map<BlockData, Integer> blockCounts) {
		JPanel header = new JPanel(new BorderLayout(10, 0));
		header.setBackground(CARD);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
			BorderFactory.createEmptyBorder(12, 16, 12, 16)
		));

		JLabel title = new JLabel(UpdatableRegistry.translate("blocklist.title"));
		title.setForeground(TEXT);
		title.setFont(new Font("SansSerif", Font.BOLD, 14));

		long uniqueCount = buildRows(blockCounts, null, 0).size();
		totalLabel = new JLabel(UpdatableRegistry.translate("blocklist.total", uniqueCount));
		totalLabel.setForeground(TEXT_DIM);
		totalLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);
		left.add(title);
		left.add(totalLabel);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		right.setOpaque(false);
		right.add(buildCountModeToggle());
		right.add(buildSearchField());

		header.add(left, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);

		return header;
	}

	private JPanel buildCountModeToggle() {
		ModernToggleButton btnItems = new ModernToggleButton(UpdatableRegistry.translate("blocklist.mode_items"));
		ModernToggleButton btnShulkers = new ModernToggleButton(UpdatableRegistry.translate("blocklist.mode_shulkers"));

		btnShulkers.setSelected(true);

		ButtonGroup group = new ButtonGroup();
		group.add(btnItems);
		group.add(btnShulkers);

		btnItems.addActionListener(e -> switchCountMode(CountMode.ITEMS));
		btnShulkers.addActionListener(e -> switchCountMode(CountMode.SHULKERS));

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		panel.setOpaque(false);
		panel.add(btnItems);
		panel.add(btnShulkers);

		return panel;
	}

	private void switchCountMode(CountMode mode) {
		countMode = mode;
		tableModel.fireTableDataChanged();
	}

	private JTextField buildSearchField() {
		JTextField field = new JTextField(16) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				if (getText().isEmpty()) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setColor(TEXT_DIM);
					g2.setFont(getFont().deriveFont(Font.ITALIC));
					g2.drawString(
						UpdatableRegistry.translate("blocklist.search"),
						getInsets().left,
						getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 1
					);
					g2.dispose();
				}
			}
		};

		field.setBackground(INPUT);
		field.setForeground(TEXT);
		field.setCaretColor(ACCENT);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		field.setFont(new Font("SansSerif", Font.PLAIN, 12));

		field.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				String text = field.getText().strip().toLowerCase();
				sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + text, 1));
			}
		});

		return field;
	}

	private JPanel buildTablePanel(Map<BlockData, Integer> blockCounts, int supportBlockCount) {
		List<BlockRow> rows = buildRows(blockCounts, supportBlockId, supportBlockCount);
		tableModel = new BlockTableModel(rows);
		table = new JTable(tableModel);

		sorter = new TableRowSorter<>(tableModel);
		// Колонка с кнопкой удаления не сортируется
		sorter.setSortable(3, false);
		table.setRowSorter(sorter);

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

		table.getColumnModel().getColumn(0).setPreferredWidth(ICON_COL_WIDTH);
		table.getColumnModel().getColumn(0).setMaxWidth(ICON_COL_WIDTH);
		table.getColumnModel().getColumn(0).setMinWidth(ICON_COL_WIDTH);
		table.getColumnModel().getColumn(1).setPreferredWidth(Integer.MAX_VALUE);
		table.getColumnModel().getColumn(2).setMinWidth(COUNT_COL_WIDTH);
		table.getColumnModel().getColumn(2).setPreferredWidth(COUNT_COL_WIDTH);
		table.getColumnModel().getColumn(2).setMaxWidth(COUNT_COL_WIDTH + 60);
		table.getColumnModel().getColumn(3).setPreferredWidth(DELETE_COL_WIDTH);
		table.getColumnModel().getColumn(3).setMaxWidth(DELETE_COL_WIDTH + 10);
		table.getColumnModel().getColumn(3).setMinWidth(DELETE_COL_WIDTH);

		table.getColumnModel().getColumn(0).setCellRenderer(new IconCellRenderer());
		table.getColumnModel().getColumn(1).setCellRenderer(new IdCellRenderer());
		table.getColumnModel().getColumn(2).setCellRenderer(new CountCellRenderer());

		DeleteButtonHandler deleteHandler = new DeleteButtonHandler();
		table.getColumnModel().getColumn(3).setCellRenderer(deleteHandler);
		table.getColumnModel().getColumn(3).setCellEditor(deleteHandler);

		loadIconsAsync(rows);

		InertialScrollPane scroll = new InertialScrollPane(table);
		scroll.setBackground(BG);
		scroll.getViewport().setBackground(BG);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(BG);
		panel.add(scroll, BorderLayout.CENTER);

		return panel;
	}

	private void loadIconsAsync(List<BlockRow> rows) {
		if (iconLoader == null) {
			return;
		}

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				for (BlockRow row : rows) {
					row.icon = iconLoader.getIcon(row.block).orElse(null);
				}
				return null;
			}

			@Override
			protected void done() {
				tableModel.fireTableDataChanged();
			}
		}.execute();
	}

	private JPanel buildFooter() {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
		footer.setBackground(CARD);
		footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
		footer.add(buildExportButton());
		footer.add(buildCloseButton());

		return footer;
	}

	private JButton buildExportButton() {
		JButton btn = new JButton(UpdatableRegistry.translate("blocklist.btn_export")) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? BORDER.darker()
					: (getModel().isRollover() ? INPUT.brighter() : INPUT);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(BORDER);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				g2.dispose();
				Color fg = ContrastTextRenderer.contrastFor(base);
				setForeground(fg);
				setIcon(AppIcon.TXT_FILE.colored(fg));
				super.paintComponent(g);
			}
		};

		btn.setForeground(ContrastTextRenderer.contrastFor(INPUT));
		btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		UiAnimator.applyHandCursor(btn);
		btn.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
		btn.setIcon(AppIcon.TXT_FILE.colored(ContrastTextRenderer.contrastFor(INPUT)));
		btn.addActionListener(e -> exportToTxt());

		return btn;
	}

	/**
	 * Экспортирует текущий список блоков в TXT-файл.
	 * Формат строки определяется активным {@link CountMode}.
	 * Блок опоры помечается суффиксом {@code [support]}.
	 */
	private void exportToTxt() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(UpdatableRegistry.translate("blocklist.export_dialog_title"));
		chooser.setFileFilter(new FileNameExtensionFilter(UpdatableRegistry.translate("filter.txt"), "txt"));
		chooser.setSelectedFile(new File("block_list.txt"));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().endsWith(".txt")) {
			file = new File(file.getAbsolutePath() + ".txt");
		}

		List<BlockRow> rows = tableModel.getAllRows();

		try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
			List<BlockRow> withShulkers = rows.stream()
				.filter(r -> (r.count + STACK_SIZE - 1) / STACK_SIZE >= SHULKER_SLOTS)
				.toList();

			List<BlockRow> withRemainders = withShulkers.stream()
				.filter(r -> (r.count + STACK_SIZE - 1) / STACK_SIZE % SHULKER_SLOTS > 0)
				.toList();

			List<BlockRow> stacksOnly = rows.stream()
				.filter(r -> {
					int stacksCeil = (r.count + STACK_SIZE - 1) / STACK_SIZE;
					return stacksCeil > 0 && stacksCeil < SHULKER_SLOTS;
				})
				.toList();

			if (!withShulkers.isEmpty()) {
				writer.println(UpdatableRegistry.translate("blocklist.export_section_shulkers"));

				for (BlockRow row : withShulkers) {
					int shulkers = (row.count + STACK_SIZE - 1) / STACK_SIZE / SHULKER_SLOTS;
					String suffix = row.isSupportBlock ? "  " + UpdatableRegistry.translate("blocklist.support_label") : "";
					writer.println(toDisplayName(row.block.getId()) + "\t" + shulkers + "sh" + suffix);
				}
			}

			if (!withRemainders.isEmpty()) {
				writer.println();
				writer.println(UpdatableRegistry.translate("blocklist.export_section_remainders"));

				for (BlockRow row : withRemainders) {
					int remainderStacks = (row.count + STACK_SIZE - 1) / STACK_SIZE % SHULKER_SLOTS;
					String suffix = row.isSupportBlock ? "  " + UpdatableRegistry.translate("blocklist.support_label") : "";
					writer.println(toDisplayName(row.block.getId()) + "\t" + remainderStacks + "s" + suffix);
				}
			}

			if (!stacksOnly.isEmpty()) {
				if (!withShulkers.isEmpty()) {
					writer.println();
				}

				writer.println(UpdatableRegistry.translate("blocklist.export_section_stacks"));

				for (BlockRow row : stacksOnly) {
					int stacksCeil = (row.count + STACK_SIZE - 1) / STACK_SIZE;
					String count = stacksCeil > 1 ? stacksCeil + "s" : String.valueOf(row.count);
					String suffix = row.isSupportBlock ? "  " + UpdatableRegistry.translate("blocklist.support_label") : "";
					writer.println(toDisplayName(row.block.getId()) + "\t" + count + suffix);
				}
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
				this,
				UpdatableRegistry.translate("error.export_txt_failed", ex.getMessage()),
				UpdatableRegistry.translate("dialog.error_title"),
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private JButton buildCloseButton() {
		JButton btn = new JButton(UpdatableRegistry.translate("blocklist.close")) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? ACCENT.darker()
					: (getModel().isRollover() ? ACCENT.brighter() : ACCENT);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				setForeground(ContrastTextRenderer.contrastFor(base));
				super.paintComponent(g);
			}
		};

		btn.setForeground(ContrastTextRenderer.contrastFor(ACCENT));
		btn.setFont(new Font("SansSerif", Font.BOLD, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		UiAnimator.applyHandCursor(btn);
		btn.setBorder(BorderFactory.createEmptyBorder(7, 20, 7, 20));
		btn.addActionListener(e -> dispose());

		return btn;
	}

	private static List<BlockRow> buildRows(
		Map<BlockData, Integer> blockCounts,
		String supportBlockId,
		int supportBlockCount
	) {
		// Группируем по getId(), суммируем количество, берём первый BlockData как представитель для иконки
		Map<String, BlockRow> grouped = new LinkedHashMap<>();

		for (Map.Entry<BlockData, Integer> entry : blockCounts.entrySet()) {
			BlockData block = entry.getKey();
			String id = block.getId();

			if (grouped.containsKey(id)) {
				grouped.get(id).addCount(entry.getValue());
			} else {
				grouped.put(id, new BlockRow(block, entry.getValue(), false));
			}
		}

		List<BlockRow> result = new ArrayList<>(grouped.values());

		if (supportBlockId != null && supportBlockCount > 0) {
			result.add(new BlockRow(new BlockData(supportBlockId), supportBlockCount, true));
		}

		return result;
	}

	private static String toDisplayName(String blockId) {
		int colonIndex = blockId.indexOf(':');
		String name = colonIndex >= 0 ? blockId.substring(colonIndex + 1) : blockId;
		String[] words = name.split("_");
		StringBuilder result = new StringBuilder();

		for (String word : words) {
			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(word.charAt(0)));
			result.append(word, 1, word.length());
		}

		return result.toString();
	}

	// ── Модель таблицы ─────────────────────────────────────────────────────────

	private static final class BlockTableModel extends AbstractTableModel {

		private List<BlockRow> rows;

		private BlockTableModel(List<BlockRow> rows) {
			this.rows = new ArrayList<>(rows);
		}

		void setRows(List<BlockRow> newRows) {
			rows = new ArrayList<>(newRows);
			fireTableDataChanged();
		}

		BlockRow getRow(int modelIndex) {
			return rows.get(modelIndex);
		}

		List<BlockRow> getAllRows() {
			return List.copyOf(rows);
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
		public String getColumnName(int column) {
			return switch (column) {
				case 0 -> "";
				case 1 -> UpdatableRegistry.translate("blocklist.col_id");
				case 2 -> UpdatableRegistry.translate("blocklist.col_count");
				case 3 -> "";
				default -> "";
			};
		}

		@Override
		public Object getValueAt(int row, int col) {
			BlockRow blockRow = rows.get(row);
			return switch (col) {
				case 0 -> blockRow.icon;
				case 1 -> blockRow.block.getId();
				case 2 -> blockRow.count;
				case 3 -> blockRow.isSupportBlock ? "" : UpdatableRegistry.translate("blocklist.btn_remove");
				default -> null;
			};
		}

		int getRawCount(int modelRow) {
			return rows.get(modelRow).count;
		}

		@Override
		public Class<?> getColumnClass(int col) {
			return switch (col) {
				case 0 -> ImageIcon.class;
				case 2 -> Integer.class;
				default -> String.class;
			};
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return col == 3 && !rows.get(row).isSupportBlock;
		}
	}

	// ── Рендереры ячеек ────────────────────────────────────────────────────────

	private static final class IconCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, null, selected, focused, row, col
			);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setIcon(value instanceof ImageIcon icon ? icon : null);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setOpaque(true);
			return label;
		}
	}

	private final class IdCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			int modelRow = table.convertRowIndexToModel(row);
			BlockRow blockRow = tableModel.getRow(modelRow);

			String displayText = blockRow.isSupportBlock
				? blockRow.block.getId() + "  " + UpdatableRegistry.translate("blocklist.support_label")
				: (String) value;

			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, displayText, selected, focused, row, col
			);
			label.setFont(new Font("Monospaced", Font.PLAIN, 12));
			label.setForeground(selected ? TEXT : (blockRow.isSupportBlock ? TEXT_DIM : ACCENT));
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
			label.setOpaque(true);
			return label;
		}
	}

	private final class CountCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			int count = value instanceof Integer c ? c : 0;
			String displayText = countMode.format(count);

			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, displayText, selected, focused, row, col
			);
			label.setHorizontalAlignment(SwingConstants.RIGHT);
			label.setFont(new Font("Monospaced", Font.BOLD, 12));
			label.setForeground(selected ? TEXT : TEXT_DIM);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
			label.setOpaque(true);
			return label;
		}
	}

	/**
	 * Совмещённый рендерер и редактор для колонки с кнопкой удаления.
	 * Клик по кнопке вызывает колбэк {@code onBlockRemoved} с ID блока.
	 */
	private final class DeleteButtonHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {

		private final JButton renderBtn = buildDeleteButton();
		private final JButton editBtn = buildDeleteButton();
		private String pendingBlockId;

		private DeleteButtonHandler() {
			editBtn.addActionListener(e -> {
				fireEditingStopped();
				if (pendingBlockId != null) {
					onBlockRemoved.accept(pendingBlockId);
				}
			});
		}

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			int modelRow = table.convertRowIndexToModel(row);

			if (tableModel.getRow(modelRow).isSupportBlock) {
				JLabel empty = new JLabel();
				empty.setBackground(selected ? table.getSelectionBackground() : BG);
				empty.setOpaque(true);
				return empty;
			}

			renderBtn.setBackground(selected ? table.getSelectionBackground() : BG);
			return renderBtn;
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean selected, int row, int col
		) {
			int modelRow = table.convertRowIndexToModel(row);
			pendingBlockId = tableModel.getRow(modelRow).block.getId();
			editBtn.setBackground(BG);
			return editBtn;
		}

		@Override
		public Object getCellEditorValue() {
			return pendingBlockId;
		}

		private JButton buildDeleteButton() {
			JButton btn = new JButton(UpdatableRegistry.translate("blocklist.btn_remove")) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					Color fill = getModel().isPressed()
						? new Color(120, 40, 40)
						: (getModel().isRollover() ? new Color(100, 30, 30) : new Color(70, 20, 20));

					g2.setColor(fill);
					g2.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 8, 6, 6);
					Color errorColor = GuiApp.theme.getError();
					g2.setColor(new Color(errorColor.getRed(), errorColor.getGreen(), errorColor.getBlue(), 80));
					g2.setStroke(new BasicStroke(1f));
					g2.drawRoundRect(2, 4, getWidth() - 5, getHeight() - 9, 6, 6);
					g2.dispose();
					super.paintComponent(g);
				}
			};

			btn.setForeground(GuiApp.theme.getError());
			btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
			btn.setFocusPainted(false);
			btn.setContentAreaFilled(false);
			btn.setBorderPainted(false);
			UiAnimator.applyHandCursor(btn);
			btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			btn.setOpaque(false);

			return btn;
		}
	}

	// ── Режим отображения количества ───────────────────────────────────────────

	enum CountMode {

		ITEMS {
			@Override
			public String format(int count) {
				return String.valueOf(count);
			}
		},

		SHULKERS {
			@Override
			public String format(int count) {
				if (count < STACK_SIZE) {
					return String.valueOf(count);
				}

				int stacksCeil = (count + STACK_SIZE - 1) / STACK_SIZE;

				if (stacksCeil < SHULKER_SLOTS) {
					return stacksCeil + "s";
				}

				int shulkers = stacksCeil / SHULKER_SLOTS;
				int remainderStacks = stacksCeil % SHULKER_SLOTS;

				return remainderStacks > 0
					? shulkers + "sh+" + remainderStacks + "s"
					: shulkers + "sh";
			}
		};

		private static final int STACK_SIZE = 64;
		private static final int SHULKER_SLOTS = 27;

		public abstract String format(int count);
	}

	// ── Данные строки ──────────────────────────────────────────────────────────

	private static final class BlockRow {

		final BlockData block;
		final boolean isSupportBlock;
		int count;
		volatile ImageIcon icon;

		BlockRow(BlockData block, int count, boolean isSupportBlock) {
			this.block = block;
			this.count = count;
			this.isSupportBlock = isSupportBlock;
		}

		void addCount(int extra) {
			count += extra;
		}
	}
}
