package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Кнопка-переключатель с тремя цветными кружками R/G/B.
 * Когда выключена — кружки серые (каналы объединены).
 * Когда включена — кружки красный/зелёный/синий (раздельные каналы).
 */
public class RgbChannelsButton extends JToggleButton {

	private static final int DOT_RADIUS = 5;
	private static final int DOT_GAP = 3;
	private static final int BUTTON_W = (DOT_RADIUS * 2 + DOT_GAP) * 3 - DOT_GAP + 10;
	private static final int BUTTON_H = 20;

	private static final Color COLOR_R = new Color(220, 70, 70);
	private static final Color COLOR_G = new Color(80, 185, 80);
	private static final Color COLOR_B = new Color(70, 120, 220);

	private final AnimatedFloat hoverProgress = new AnimatedFloat(0f);
	private final AnimatedFloat toggleProgress = new AnimatedFloat(0f);

	public RgbChannelsButton() {
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		UiAnimator.applyHandCursor(this);
		setPreferredSize(new Dimension(BUTTON_W, BUTTON_H));
		setMinimumSize(new Dimension(BUTTON_W, BUTTON_H));
		setMaximumSize(new Dimension(BUTTON_W, BUTTON_H));

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

		addActionListener(e -> animateToggle(isSelected()));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int totalDotsW = (DOT_RADIUS * 2 + DOT_GAP) * 3 - DOT_GAP;
		int startX = (w - totalDotsW) / 2;
		int centerY = h / 2;

		Color dimColor = GuiApp.theme.getTextDim();

		if (hoverProgress.get() > 0f) {
			Color hoverBg = UiAnimator.lerp(
				new Color(0, 0, 0, 0),
				GuiApp.theme.getBtnHoverBg(),
				hoverProgress.get() * 0.6f
			);
			g2.setColor(hoverBg);
			g2.fillRoundRect(0, 0, w, h, 8, 8);
		}

		Color[] activeColors = {COLOR_R, COLOR_G, COLOR_B};

		for (int i = 0; i < 3; i++) {
			int cx = startX + i * (DOT_RADIUS * 2 + DOT_GAP) + DOT_RADIUS;
			Color active = activeColors[i];
			Color dotColor = UiAnimator.lerp(dimColor, active, toggleProgress.get());

			g2.setColor(dotColor);
			g2.fillOval(cx - DOT_RADIUS, centerY - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
		}

		g2.dispose();
	}

	private void animateHover(boolean entering) {
		hoverProgress.animateTo(entering ? 1f : 0f, 150, v -> repaint());
	}

	private void animateToggle(boolean toOn) {
		toggleProgress.animateTo(toOn ? 1f : 0f, 200, v -> repaint());
	}

	/** Синхронизирует визуальное состояние без анимации (при восстановлении настроек). */
	public void syncVisualState() {
		toggleProgress.set(isSelected() ? 1f : 0f);
		repaint();
	}
}
