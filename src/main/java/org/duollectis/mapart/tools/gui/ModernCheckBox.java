package org.duollectis.mapart.tools.gui;

import javax.swing.JCheckBox;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Кастомный чекбокс в современном стиле.
 * Галочка появляется с анимацией через {@link UiAnimator#animateFloat} — прогресс
 * рисования от 0 до 1 даёт эффект «рисования» галочки, как в Telegram.
 */
public class ModernCheckBox extends JCheckBox {

	private static final int BOX_SIZE = 16;
	private static final int BOX_ARC = 4;
	private static final Color CHECK_COLOR = Color.WHITE;

	private static Color bgChecked() { return GuiApp.ACCENT; }

	private static Color bgUnchecked() { return GuiApp.BG_INPUT; }

	private static Color borderColor() { return GuiApp.BORDER; }

	private static Color textColor() { return GuiApp.TEXT; }

	private static Color textDim() { return GuiApp.TEXT_DIM; }

	/** Прогресс анимации: 0.0 = unchecked, 1.0 = checked */
	private float checkProgress;

	private Timer checkTimer;

	public ModernCheckBox(String text) {
		super(text);
		setOpaque(false);
		setFocusPainted(false);
		setFont(new Font("SansSerif", Font.PLAIN, 13));
		setForeground(textColor());
		setIconTextGap(8);
		setIcon(new CheckIcon());
		setSelectedIcon(new CheckIcon());

		checkProgress = isSelected() ? 1f : 0f;

		addActionListener(e -> animateCheck(isSelected()));
	}

	private void animateCheck(boolean toChecked) {
		if (checkTimer != null) {
			checkTimer.stop();
		}

		float from = checkProgress;
		float to = toChecked ? 1f : 0f;

		checkTimer = UiAnimator.animateFloat(from, to, 200, progress -> {
			checkProgress = progress;
			repaint();
		}, null);
	}

	private class CheckIcon implements javax.swing.Icon {

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Color bg = UiAnimator.lerp(bgUnchecked(), bgChecked(), checkProgress);
			Color border = UiAnimator.lerp(borderColor(), bgChecked().darker(), checkProgress);

			g2.setColor(bg);
			g2.fillRoundRect(x, y, BOX_SIZE, BOX_SIZE, BOX_ARC, BOX_ARC);

			g2.setColor(border);
			g2.setStroke(new BasicStroke(1.2f));
			g2.drawRoundRect(x, y, BOX_SIZE - 1, BOX_SIZE - 1, BOX_ARC, BOX_ARC);

			if (checkProgress > 0.05f) {
				drawAnimatedCheck(g2, x, y, checkProgress);
			}

			g2.dispose();
		}

		/**
		 * Рисует галочку с прогрессом от 0 до 1 — сначала первый отрезок, потом второй.
		 * Создаёт эффект «рисования» как в Telegram.
		 */
		private void drawAnimatedCheck(Graphics2D g2, int x, int y, float progress) {
			int cx = x + BOX_SIZE / 2;
			int cy = y + BOX_SIZE / 2;

			int x1 = cx - 4;
			int y1 = cy;
			int xMid = cx - 1;
			int yMid = cy + 3;
			int x2 = cx + 4;
			int y2 = cy - 3;

			g2.setColor(new Color(
				CHECK_COLOR.getRed(),
				CHECK_COLOR.getGreen(),
				CHECK_COLOR.getBlue(),
				(int) (255 * Math.min(1f, progress * 2f))
			));
			g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			if (progress < 0.5f) {
				float segProgress = progress * 2f;
				int endX = (int) (x1 + (xMid - x1) * segProgress);
				int endY = (int) (y1 + (yMid - y1) * segProgress);
				g2.drawLine(x1, y1, endX, endY);
			} else {
				g2.drawLine(x1, y1, xMid, yMid);
				float segProgress = (progress - 0.5f) * 2f;
				int endX = (int) (xMid + (x2 - xMid) * segProgress);
				int endY = (int) (yMid + (y2 - yMid) * segProgress);
				g2.drawLine(xMid, yMid, endX, endY);
			}
		}

		@Override
		public int getIconWidth() {
			return BOX_SIZE;
		}

		@Override
		public int getIconHeight() {
			return BOX_SIZE;
		}
	}

	public void setDimText(boolean dim) {
		setForeground(dim ? textDim() : textColor());
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		return new Dimension(d.width, Math.max(d.height, BOX_SIZE + 6));
	}
}
