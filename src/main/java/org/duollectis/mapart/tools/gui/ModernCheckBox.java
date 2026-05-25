package org.duollectis.mapart.tools.gui;

import javax.swing.JCheckBox;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Кастомный чекбокс в современном стиле:
 * скруглённый квадрат-индикатор с галочкой, без стандартного Swing-рендеринга.
 */
public class ModernCheckBox extends JCheckBox {

	private static final int BOX_SIZE = 16;
	private static final int BOX_ARC = 4;
	private static final Color BG_CHECKED = GuiApp.ACCENT;
	private static final Color BG_UNCHECKED = GuiApp.BG_INPUT;
	private static final Color BORDER_COLOR = GuiApp.BORDER;
	private static final Color CHECK_COLOR = Color.WHITE;
	private static final Color TEXT_COLOR = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;

	public ModernCheckBox(String text) {
		super(text);
		setOpaque(false);
		setFocusPainted(false);
		setFont(new Font("SansSerif", Font.PLAIN, 13));
		setForeground(TEXT_COLOR);
		setIconTextGap(8);
		setIcon(new CheckIcon());
		setSelectedIcon(new CheckIcon());
	}

	private class CheckIcon implements javax.swing.Icon {

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			boolean checked = isSelected();

			g2.setColor(checked ? BG_CHECKED : BG_UNCHECKED);
			g2.fillRoundRect(x, y, BOX_SIZE, BOX_SIZE, BOX_ARC, BOX_ARC);

			g2.setColor(checked ? BG_CHECKED.darker() : BORDER_COLOR);
			g2.setStroke(new BasicStroke(1.2f));
			g2.drawRoundRect(x, y, BOX_SIZE - 1, BOX_SIZE - 1, BOX_ARC, BOX_ARC);

			if (checked) {
				g2.setColor(CHECK_COLOR);
				g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				int cx = x + BOX_SIZE / 2;
				int cy = y + BOX_SIZE / 2;
				g2.drawLine(cx - 4, cy, cx - 1, cy + 3);
				g2.drawLine(cx - 1, cy + 3, cx + 4, cy - 3);
			}

			g2.dispose();
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
		setForeground(dim ? TEXT_DIM : TEXT_COLOR);
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		return new Dimension(d.width, Math.max(d.height, BOX_SIZE + 6));
	}
}
