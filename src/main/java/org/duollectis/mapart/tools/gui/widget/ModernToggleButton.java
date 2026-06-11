package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Кастомная кнопка-переключатель в стиле pill:
 * скруглённый фон, акцентный цвет при активации.
 * Переход on/off анимирован через {@link UiAnimator#animateFloat} с easeOutCubic.
 */
public class ModernToggleButton extends JToggleButton {

	private static Color bgOff() { return GuiApp.theme.getBgInput(); }

	private static Color bgOn() { return GuiApp.theme.getAccent(); }

	private static Color bgHoverOff() { return GuiApp.theme.getBgCard(); }

	private static Color bgHoverOn() { return GuiApp.theme.getAccentBright(); }

	private static Color borderOff() { return GuiApp.theme.getBorder(); }

	private static Color borderOn() { return GuiApp.theme.getAccent(); }

	private static Color textOff() { return GuiApp.theme.getTextDim(); }

	private boolean hovered = false;

	/** Прогресс анимации toggle: 0.0 = off, 1.0 = on */
	private float toggleProgress;

	private Timer toggleTimer;

	public ModernToggleButton(String text) {
		super(text);
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setFont(new Font("SansSerif", Font.PLAIN, 12));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		toggleProgress = 0f;

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

		addActionListener(e -> animateToggle(isSelected()));
	}

	/**
		* Синхронизирует визуальный прогресс анимации с текущим состоянием кнопки
		* без анимации — нужно вызывать после программного setSelected().
		*/
	public void syncVisualState() {
		toggleProgress = isSelected() ? 1f : 0f;
		repaint();
	}

	private void animateToggle(boolean toOn) {
		if (toggleTimer != null) {
			toggleTimer.stop();
		}

		float from = toggleProgress;
		float to = toOn ? 1f : 0f;

		toggleTimer = UiAnimator.animateFloat(from, to, 180, progress -> {
			toggleProgress = progress;
			repaint();
		}, null);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Color bgFrom = hovered ? bgHoverOff() : bgOff();
		Color bgTo = hovered ? bgHoverOn() : bgOn();
		Color bg = UiAnimator.lerp(bgFrom, bgTo, toggleProgress);
		Color border = UiAnimator.lerp(borderOff(), borderOn(), toggleProgress);
		Color fg = UiAnimator.lerp(textOff(), GuiApp.theme.getTextOnAccent(), toggleProgress);

		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

		g2.setColor(border);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

		g2.setColor(fg);
		g2.setFont(getFont());
		FontMetrics fm = g2.getFontMetrics();
		int tx = (getWidth() - fm.stringWidth(getText())) / 2;
		int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
		g2.drawString(getText(), tx, ty);

		g2.dispose();
	}

	@Override
	public Dimension getPreferredSize() {
		FontMetrics fm = getFontMetrics(getFont());
		int w = fm.stringWidth(getText()) + 24;
		return new Dimension(w, 28);
	}
}
