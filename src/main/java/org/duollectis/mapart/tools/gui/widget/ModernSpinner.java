package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Современный JSpinner с кастомным рендерингом:
 * скруглённый фон, минималистичные кнопки ▲▼, поле ввода без Nimbus-артефактов.
 */
public class ModernSpinner extends JSpinner {

	private static final int ARC = 8;

	private static Color bg() { return GuiApp.theme.getBgInput(); }
	private static Color borderColor() { return GuiApp.theme.getBorder(); }
	private static Color text() { return GuiApp.theme.getText(); }
	private static Color textDim() { return GuiApp.theme.getTextDim(); }
	private static Color btnBg() { return GuiApp.theme.getBgCard(); }
	private static Color btnHover() { return GuiApp.theme.getBorder(); }

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
		g2.setColor(bg());
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
		g2.setColor(borderColor());
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
			field.setBackground(bg());
			field.setForeground(text());
			field.setCaretColor(GuiApp.theme.getAccent());
			field.setSelectionColor(GuiApp.theme.getSelectionBg());
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
				private float hoverProgress = 0f;
				private Timer hoverTimer;

				{
					addMouseListener(new MouseAdapter() {
						@Override
						public void mouseEntered(MouseEvent e) {
							animateHover(true);
						}

						@Override
						public void mouseExited(MouseEvent e) {
							animateHover(false);
						}
					});
				}

				private void animateHover(boolean toHovered) {
					if (hoverTimer != null) {
						hoverTimer.stop();
					}

					float from = hoverProgress;
					float to = toHovered ? 1f : 0f;
					hoverTimer = UiAnimator.animateFloat(from, to, 150, progress -> {
						hoverProgress = progress;
						repaint();
					}, null);
				}

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(UiAnimator.lerp(btnBg(), btnHover(), hoverProgress));
					g2.fillRect(0, 0, getWidth(), getHeight());

					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					int w = 6;
					int h = 4;

					g2.setColor(UiAnimator.lerp(textDim(), text(), hoverProgress));
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
