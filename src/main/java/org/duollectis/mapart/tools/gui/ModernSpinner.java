package org.duollectis.mapart.tools.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Современный JSpinner с кастомным рендерингом:
 * скруглённый фон, минималистичные кнопки ▲▼, поле ввода без Nimbus-артефактов.
 */
public class ModernSpinner extends JSpinner {

	private static final int ARC = 8;
	private static final Color BG = GuiApp.BG_INPUT;
	private static final Color BORDER_COLOR = GuiApp.BORDER;
	private static final Color TEXT = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;
	private static final Color BTN_BG = new Color(38, 42, 60);
	private static final Color BTN_HOVER = new Color(55, 60, 85);

	public ModernSpinner(SpinnerModel model) {
		super(model);
		setOpaque(false);
		setFont(new Font("SansSerif", Font.PLAIN, 13));
		setBorder(BorderFactory.createEmptyBorder());
		setUI(new ModernSpinnerUI(this));
		setPreferredSize(new Dimension(80, 32));
		styleEditor();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(BG);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
		g2.setColor(BORDER_COLOR);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
		g2.dispose();
	}

	@Override
	protected void paintBorder(Graphics g) {
		// граница рисуется в paintComponent
	}

	private void styleEditor() {
		JComponent editor = getEditor();

		if (editor instanceof DefaultEditor de) {
			JTextField field = de.getTextField();
			field.setBackground(BG);
			field.setForeground(TEXT);
			field.setCaretColor(GuiApp.ACCENT);
			field.setSelectionColor(GuiApp.SELECTION_BG);
			field.setFont(new Font("SansSerif", Font.PLAIN, 13));
			field.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
			field.setHorizontalAlignment(JTextField.CENTER);
			field.setOpaque(true);
			field.setUI(new BasicTextFieldUI());
		}
	}

	private static class ModernSpinnerUI extends BasicSpinnerUI {

		private final JSpinner owner;

		ModernSpinnerUI(JSpinner owner) {
			this.owner = owner;
		}

		@Override
		protected JComponent createNextButton() {
			JButton btn = buildArrowButton(true);
			btn.setName("Spinner.nextButton");
			btn.addActionListener(e -> stepValue(true));
			return btn;
		}

		@Override
		protected JComponent createPreviousButton() {
			JButton btn = buildArrowButton(false);
			btn.setName("Spinner.previousButton");
			btn.addActionListener(e -> stepValue(false));
			return btn;
		}

		private void stepValue(boolean up) {
			Object next = up ? owner.getNextValue() : owner.getPreviousValue();

			if (next != null) {
				owner.setValue(next);
			}
		}

		private JButton buildArrowButton(boolean up) {
			JButton btn = new JButton() {
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
					g2.setColor(hovered ? BTN_HOVER : BTN_BG);
					g2.fillRect(0, 0, getWidth(), getHeight());

					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					int w = 6;
					int h = 4;

					g2.setColor(hovered ? TEXT : TEXT_DIM);
					g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

					if (up) {
						g2.drawLine(cx - w / 2, cy + h / 2, cx, cy - h / 2);
						g2.drawLine(cx, cy - h / 2, cx + w / 2, cy + h / 2);
					} else {
						g2.drawLine(cx - w / 2, cy - h / 2, cx, cy + h / 2);
						g2.drawLine(cx, cy + h / 2, cx + w / 2, cy - h / 2);
					}

					g2.dispose();
				}
			};

			btn.setOpaque(false);
			btn.setContentAreaFilled(false);
			btn.setBorderPainted(false);
			btn.setFocusPainted(false);
			btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			btn.setPreferredSize(new Dimension(22, 0));

			return btn;
		}
	}
}
