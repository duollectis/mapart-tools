package org.duollectis.mapart.tools.gui.dialog;

import org.duollectis.mapart.tools.converter.SupportBlockSettings;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;

/**
 * Диалог выбора блоков-опор с весами и режимом распределения.
 * Позволяет добавить несколько блоков, задать каждому вес (процент заполнения)
 * и выбрать режим: случайный или последовательный.
 */
public class SupportBlockPickerDialog extends JDialog {

	private static final int DIALOG_WIDTH = 520;
	private static final int DIALOG_HEIGHT = 420;
	private static final int ROW_HEIGHT = 36;
	private static final int WEIGHT_COL_WIDTH = 80;
	private static final int DELETE_COL_WIDTH = 70;
	private static final int DEFAULT_WEIGHT = 100;

	private static final Color BG = GuiApp.theme.getBgDeep();
	private static final Color CARD = GuiApp.theme.getBgCard();
	private static final Color INPUT = GuiApp.theme.getBgInput();
	private static final Color BORDER = GuiApp.theme.getBorder();
	private static final Color ACCENT = GuiApp.theme.getAccent();
	private static final Color TEXT = GuiApp.theme.getText();
	private static final Color TEXT_DIM = GuiApp.theme.getTextDim();
	private static final Color ERROR = GuiApp.theme.getError();

	private final List<EntryRow> rows = new ArrayList<>();
	private EntryTableModel tableModel;
	private JRadioButton randomRadio;
	private boolean confirmed = false;

	public SupportBlockPickerDialog(JFrame parent, SupportBlockSettings initial) {
		super(parent, UpdatableRegistry.translate("support_picker.title"), true);

		if (initial != null) {
			for (SupportBlockSettings.Entry entry : initial.getEntries()) {
				rows.add(new EntryRow(entry.blockId(), entry.weight()));
			}
		}

		buildUi(initial);
		setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		setMinimumSize(new Dimension(400, 320));
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	/**
	 * Возвращает настройки блоков опоры, выбранные пользователем.
	 * Вызывать только если {@link #isConfirmed()} == true.
	 */
	public SupportBlockSettings buildSettings() {
		List<SupportBlockSettings.Entry> entries = rows.stream()
			.map(r -> new SupportBlockSettings.Entry(r.blockId, r.weight))
			.toList();

		WeightedSelector.Mode mode = randomRadio.isSelected()
			? WeightedSelector.Mode.RANDOM
			: WeightedSelector.Mode.SEQUENTIAL;

		return new SupportBlockSettings(entries, mode);
	}

	private void buildUi(SupportBlockSettings initial) {
		getContentPane().setBackground(BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildFooter(initial), BorderLayout.SOUTH);
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(CARD);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
			BorderFactory.createEmptyBorder(12, 16, 12, 16)
		));

		JLabel title = new JLabel(UpdatableRegistry.translate("support_picker.title"));
		title.setForeground(TEXT);
		title.setFont(new Font("SansSerif", Font.BOLD, 14));

		JLabel hint = new JLabel(UpdatableRegistry.translate("support_picker.hint"));
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
		tableModel = new EntryTableModel(rows);
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
		table.getColumnModel().getColumn(1).setPreferredWidth(WEIGHT_COL_WIDTH);
		table.getColumnModel().getColumn(1).setMaxWidth(WEIGHT_COL_WIDTH + 20);
		table.getColumnModel().getColumn(1).setMinWidth(WEIGHT_COL_WIDTH);
		table.getColumnModel().getColumn(2).setPreferredWidth(DELETE_COL_WIDTH);
		table.getColumnModel().getColumn(2).setMaxWidth(DELETE_COL_WIDTH + 10);
		table.getColumnModel().getColumn(2).setMinWidth(DELETE_COL_WIDTH);

		table.getColumnModel().getColumn(0).setCellRenderer(new IdCellRenderer());
		table.getColumnModel().getColumn(1).setCellRenderer(new WeightCellRenderer());
		table.getColumnModel().getColumn(1).setCellEditor(new WeightCellEditor());

		DeleteButtonHandler deleteHandler = new DeleteButtonHandler(table);
		table.getColumnModel().getColumn(2).setCellRenderer(deleteHandler);
		table.getColumnModel().getColumn(2).setCellEditor(deleteHandler);

		InertialScrollPane scroll = new InertialScrollPane(table);
		scroll.setBackground(BG);
		scroll.getViewport().setBackground(BG);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JPanel addRow = buildAddRow(table);

		JPanel center = new JPanel(new BorderLayout(0, 0));
		center.setBackground(BG);
		center.add(scroll, BorderLayout.CENTER);
		center.add(addRow, BorderLayout.SOUTH);

		return center;
	}

	private JPanel buildAddRow(JTable table) {
		JTextField idField = new JTextField();
		idField.setBackground(INPUT);
		idField.setForeground(TEXT);
		idField.setCaretColor(ACCENT);
		idField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		idField.setFont(new Font("Monospaced", Font.PLAIN, 12));

		JButton addBtn = buildSmallButton(UpdatableRegistry.translate("support_picker.btn_add"));
		addBtn.addActionListener(e -> {
			String id = idField.getText().strip();

			if (id.isBlank()) {
				return;
			}

			rows.add(new EntryRow(id, DEFAULT_WEIGHT));
			tableModel.fireTableDataChanged();
			idField.setText("");
			redistributeWeights();
			tableModel.fireTableDataChanged();
		});

		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(CARD);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
			BorderFactory.createEmptyBorder(8, 12, 8, 12)
		));
		panel.add(idField, BorderLayout.CENTER);
		panel.add(addBtn, BorderLayout.EAST);

		return panel;
	}

	private JPanel buildFooter(SupportBlockSettings initial) {
		WeightedSelector.Mode initialMode = initial != null
			? initial.getMode()
			: WeightedSelector.Mode.SEQUENTIAL;

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

		JButton saveBtn = buildPrimaryButton(UpdatableRegistry.translate("support_picker.btn_save"));
		saveBtn.addActionListener(e -> {
			if (rows.isEmpty()) {
				return;
			}

			confirmed = true;
			dispose();
		});

		JButton cancelBtn = buildSmallButton(UpdatableRegistry.translate("support_picker.btn_cancel"));
		cancelBtn.addActionListener(e -> dispose());

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		btnPanel.setOpaque(false);
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

	/** Равномерно перераспределяет веса между всеми блоками. */
	private void redistributeWeights() {
		if (rows.isEmpty()) {
			return;
		}

		int base = 100 / rows.size();
		int remainder = 100 % rows.size();

		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).weight = base + (i < remainder ? 1 : 0);
		}
	}

	// ── Модель таблицы ─────────────────────────────────────────────────────────

	private static final class EntryTableModel extends AbstractTableModel {

		private final List<EntryRow> rows;

		private EntryTableModel(List<EntryRow> rows) {
			this.rows = rows;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public String getColumnName(int col) {
			return switch (col) {
				case 0 -> UpdatableRegistry.translate("support_picker.col_block");
				case 1 -> UpdatableRegistry.translate("support_picker.col_weight");
				case 2 -> "";
				default -> "";
			};
		}

		@Override
		public Object getValueAt(int row, int col) {
			EntryRow entry = rows.get(row);
			return switch (col) {
				case 0 -> entry.blockId;
				case 1 -> entry.weight;
				case 2 -> UpdatableRegistry.translate("blocklist.btn_remove");
				default -> null;
			};
		}

		@Override
		public void setValueAt(Object value, int row, int col) {
			if (col == 1 && value instanceof Integer weight) {
				rows.get(row).weight = weight;
				fireTableCellUpdated(row, col);
			}
		}

		@Override
		public Class<?> getColumnClass(int col) {
			return switch (col) {
				case 1 -> Integer.class;
				default -> String.class;
			};
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return col == 1 || col == 2;
		}
	}

	// ── Рендереры ──────────────────────────────────────────────────────────────

	private static final class IdCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, value, selected, focused, row, col
			);
			label.setFont(new Font("Monospaced", Font.PLAIN, 12));
			label.setForeground(selected ? TEXT : ACCENT);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
			label.setOpaque(true);
			return label;
		}
	}

	private static final class WeightCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean selected, boolean focused, int row, int col
		) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, value + "%", selected, focused, row, col
			);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setFont(new Font("Monospaced", Font.BOLD, 12));
			label.setForeground(selected ? TEXT : TEXT_DIM);
			label.setBackground(selected ? table.getSelectionBackground() : BG);
			label.setOpaque(true);
			return label;
		}
	}

	private static final class WeightCellEditor extends AbstractCellEditor implements TableCellEditor {

		private final JSpinner spinner = new JSpinner(new SpinnerNumberModel(100, 1, 9999, 1));

		private WeightCellEditor() {
			spinner.setBackground(INPUT);
			spinner.setFont(new Font("Monospaced", Font.PLAIN, 12));
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setBackground(INPUT);
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setForeground(TEXT);
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setCaretColor(ACCENT);
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean selected, int row, int col
		) {
			spinner.setValue(value instanceof Integer v ? v : 100);
			return spinner;
		}

		@Override
		public Object getCellEditorValue() {
			return spinner.getValue();
		}
	}

	private final class DeleteButtonHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {

		private final JButton renderBtn = buildDeleteButton();
		private final JButton editBtn = buildDeleteButton();
		private final JTable table;
		private int pendingRow = -1;

		private DeleteButtonHandler(JTable table) {
			this.table = table;
			editBtn.addActionListener(e -> {
				fireEditingStopped();

				if (pendingRow >= 0 && pendingRow < rows.size()) {
					rows.remove(pendingRow);
					tableModel.fireTableDataChanged();
					redistributeWeights();
					tableModel.fireTableDataChanged();
				}
			});
		}

		@Override
		public Component getTableCellRendererComponent(
			JTable t, Object value, boolean selected, boolean focused, int row, int col
		) {
			renderBtn.setBackground(selected ? t.getSelectionBackground() : BG);
			return renderBtn;
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable t, Object value, boolean selected, int row, int col
		) {
			pendingRow = row;
			editBtn.setBackground(BG);
			return editBtn;
		}

		@Override
		public Object getCellEditorValue() {
			return null;
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
			btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			btn.setOpaque(false);

			return btn;
		}
	}

	// ── UI-фабрики ─────────────────────────────────────────────────────────────

	private JButton buildPrimaryButton(String text) {
		JButton btn = new JButton(text) {
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
			btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			btn.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));

		return btn;
	}

	private JButton buildSmallButton(String text) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? GuiApp.theme.getBtnHoverBg().darker()
					: (getModel().isRollover() ? GuiApp.theme.getBtnHoverBg() : GuiApp.theme.getBgInput());
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(BORDER);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				g2.dispose();
				setForeground(ContrastTextRenderer.contrastFor(base));
				super.paintComponent(g);
			}
		};
	
			btn.setForeground(ContrastTextRenderer.contrastFor(GuiApp.theme.getBgInput()));
			btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));

		return btn;
	}

	// ── Данные строки ──────────────────────────────────────────────────────────

	private static final class EntryRow {

		String blockId;
		int weight;

		EntryRow(String blockId, int weight) {
			this.blockId = blockId;
			this.weight = weight;
		}
	}
}
