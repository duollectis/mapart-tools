package org.duollectis.mapart.tools.gui;

import javax.swing.JToggleButton;
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
 * Кастомная кнопка-переключатель в стиле pill:
 * скруглённый фон, акцентный цвет при активации, hover-эффект.
 */
public class ModernToggleButton extends JToggleButton {

	private static final Color BG_OFF = GuiApp.BG_INPUT;
	private static final Color BG_ON = GuiApp.ACCENT;
	private static final Color BG_HOVER_OFF = new Color(40, 44, 64);
	private static final Color BG_HOVER_ON = new Color(116, 192, 252);
	private static final Color BORDER_OFF = GuiApp.BORDER;
	private static final Color BORDER_ON = GuiApp.ACCENT;
	private static final Color TEXT_OFF = GuiApp.TEXT_DIM;
	private static final Color TEXT_ON = Color.WHITE;

	private boolean hovered = false;

	public ModernToggleButton(String text) {
		super(text);
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setFont(new Font("SansSerif", Font.PLAIN, 12));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		boolean on = isSelected();
		Color bg = on
				? (hovered ? BG_HOVER_ON : BG_ON)
				: (hovered ? BG_HOVER_OFF : BG_OFF);
		Color border = on ? BORDER_ON : BORDER_OFF;
		Color fg = on ? TEXT_ON : TEXT_OFF;

		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

		g2.setColor(border);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

		g2.setColor(fg);
		g2.setFont(getFont());
		java.awt.FontMetrics fm = g2.getFontMetrics();
		int tx = (getWidth() - fm.stringWidth(getText())) / 2;
		int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
		g2.drawString(getText(), tx, ty);

		g2.dispose();
	}

	@Override
	public Dimension getPreferredSize() {
		java.awt.FontMetrics fm = getFontMetrics(getFont());
		int w = fm.stringWidth(getText()) + 24;
		return new Dimension(w, 28);
	}
}
