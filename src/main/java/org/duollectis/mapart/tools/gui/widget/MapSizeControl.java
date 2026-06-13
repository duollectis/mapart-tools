package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Компактный виджет выбора размера карт в формате «W [−][1][+] × H [−][1][+]».
 * Поле значения — редактируемый {@link JTextField} с числовой валидацией.
 * Кнопки шага — {@link RippleButton} с hover/ripple анимацией.
 */
public class MapSizeControl extends JPanel {

	private static final int MIN_VALUE = 1;
	private static final int MAX_VALUE = 32;
	private static final int BTN_W = 22;
	private static final int VAL_W = 32;
	private static final int COUNTER_W = BTN_W + VAL_W + BTN_W;
	private static final int H = 26;
	private static final int GAP = 4;
	private static final int LBL_W = 12;

	private static final int TOTAL_W = LBL_W + GAP + COUNTER_W + GAP + LBL_W + GAP + LBL_W + GAP + COUNTER_W;

	private int width = 1;
	private int height = 1;

	private final List<IntConsumer> widthListeners = new ArrayList<>();
	private final List<IntConsumer> heightListeners = new ArrayList<>();

	private final CounterWidget widthCounter;
	private final CounterWidget heightCounter;

	public MapSizeControl(MainWindow mainWindow) {
		setLayout(null);
		setOpaque(false);

		Dimension fixed = new Dimension(TOTAL_W, H);
		setPreferredSize(fixed);
		setMinimumSize(fixed);
		setMaximumSize(fixed);

		int x = 0;

		JLabel wLabel = buildDimLabel("W");
		wLabel.setBounds(x, 0, LBL_W, H);
		add(wLabel);
		x += LBL_W + GAP;

		widthCounter = new CounterWidget(
			mainWindow,
			() -> width,
			v -> {
				width = v;
				widthListeners.forEach(l -> l.accept(v));
			}
		);
		widthCounter.setBounds(x, 0, COUNTER_W, H);
		add(widthCounter);
		x += COUNTER_W + GAP;

		JLabel xLabel = buildDimLabel("×");
		xLabel.setBounds(x, 0, LBL_W, H);
		add(xLabel);
		x += LBL_W + GAP;

		JLabel hLabel = buildDimLabel("H");
		hLabel.setBounds(x, 0, LBL_W, H);
		add(hLabel);
		x += LBL_W + GAP;

		heightCounter = new CounterWidget(
			mainWindow,
			() -> height,
			v -> {
				height = v;
				heightListeners.forEach(l -> l.accept(v));
			}
		);
		heightCounter.setBounds(x, 0, COUNTER_W, H);
		add(heightCounter);
	}

	public int getMapWidth() {
		return width;
	}

	public int getMapHeight() {
		return height;
	}

	public void setMapWidth(int value) {
		int clamped = Math.clamp(value, MIN_VALUE, MAX_VALUE);
		width = clamped;
		widthCounter.setValue(clamped);
		widthListeners.forEach(l -> l.accept(clamped));
	}

	public void setMapHeight(int value) {
		int clamped = Math.clamp(value, MIN_VALUE, MAX_VALUE);
		height = clamped;
		heightCounter.setValue(clamped);
		heightListeners.forEach(l -> l.accept(clamped));
	}

	public void addWidthChangeListener(IntConsumer listener) {
		widthListeners.add(listener);
	}

	public void addHeightChangeListener(IntConsumer listener) {
		heightListeners.add(listener);
	}

	private static JLabel buildDimLabel(String text) {
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		label.setForeground(GuiApp.theme.getTextDim());
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(GuiApp.theme.getTextDim()));
		return label;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Внутренний виджет одного счётчика
	// ─────────────────────────────────────────────────────────────────────────

	private static final class CounterWidget extends JPanel {

		private final IntSupplier getter;
		private final IntConsumer setter;
		private final JTextField valueField;
		private final RippleButton minusBtn;
		private final RippleButton plusBtn;

		CounterWidget(MainWindow mainWindow, IntSupplier getter, IntConsumer setter) {
			this.getter = getter;
			this.setter = setter;

			setLayout(null);
			setOpaque(false);

			minusBtn = new RippleButton("−", mainWindow);
			minusBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
			minusBtn.setBorder(BorderFactory.createEmptyBorder());
			minusBtn.setBounds(0, 0, BTN_W, H);

			valueField = buildValueField();
			valueField.setBounds(BTN_W, 0, VAL_W, H);

			plusBtn = new RippleButton("+", mainWindow);
			plusBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
			plusBtn.setBorder(BorderFactory.createEmptyBorder());
			plusBtn.setBounds(BTN_W + VAL_W, 0, BTN_W, H);

			minusBtn.addActionListener(e -> step(-1));
			plusBtn.addActionListener(e -> step(1));

			add(minusBtn);
			add(valueField);
			add(plusBtn);

			refreshButtonStates();
		}

		void setValue(int value) {
			valueField.setText(String.valueOf(value));
			refreshButtonStates();
		}

		private void step(int delta) {
			int newValue = Math.clamp(getter.getAsInt() + delta, MIN_VALUE, MAX_VALUE);
			setter.accept(newValue);
			valueField.setText(String.valueOf(newValue));
			refreshButtonStates();
		}

		private void commitFieldValue() {
			String text = valueField.getText().trim();

			if (text.isEmpty()) {
				valueField.setText(String.valueOf(getter.getAsInt()));
				return;
			}

			try {
				int parsed = Integer.parseInt(text);
				int clamped = Math.clamp(parsed, MIN_VALUE, MAX_VALUE);
				setter.accept(clamped);
				valueField.setText(String.valueOf(clamped));
				refreshButtonStates();
			} catch (NumberFormatException ignored) {
				valueField.setText(String.valueOf(getter.getAsInt()));
			}
		}

		private void refreshButtonStates() {
			minusBtn.setEnabled(getter.getAsInt() > MIN_VALUE);
			plusBtn.setEnabled(getter.getAsInt() < MAX_VALUE);
		}

		private JTextField buildValueField() {
			JTextField field = new JTextField(String.valueOf(getter.getAsInt()), 2) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(GuiApp.theme.getBgInput());
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
					g2.setColor(GuiApp.theme.getBorder());
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
					g2.dispose();
					super.paintComponent(g);
				}
			};

			field.setFont(new Font("SansSerif", Font.BOLD, 11));
			field.setForeground(GuiApp.theme.getText());
			field.setOpaque(false);
			field.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
			field.setHorizontalAlignment(SwingConstants.CENTER);

			// Разрешаем вводить только цифры (максимум 2 символа)
			((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
						throws BadLocationException {
					String current = fb.getDocument().getText(0, fb.getDocument().getLength());

					if (string == null || !string.matches("\\d+")) {
						return;
					}

					if (current.length() + string.length() > 2) {
						return;
					}

					super.insertString(fb, offset, string, attr);
				}

				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
						throws BadLocationException {
					String current = fb.getDocument().getText(0, fb.getDocument().getLength());
					String remaining = current.substring(0, offset) + current.substring(offset + length);

					if (text == null || !text.matches("\\d*")) {
						return;
					}

					if (remaining.length() + text.length() > 2) {
						return;
					}

					super.replace(fb, offset, length, text, attrs);
				}
			});

			field.addActionListener(e -> commitFieldValue());
			field.addFocusListener(new java.awt.event.FocusAdapter() {
				@Override
				public void focusLost(java.awt.event.FocusEvent e) {
					commitFieldValue();
				}
			});

			UpdatableRegistry.onThemeAnimFrame(() -> {
				field.setForeground(GuiApp.theme.getText());
				field.repaint();
			});

			return field;
		}
	}
}
