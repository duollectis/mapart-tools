package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;
import org.duollectis.mapart.tools.gui.util.AppIcon;

/**
 * Универсальная кнопка-переключатель с плавными анимациями hover и toggle.
 * Поддерживает иконку через {@code putClientProperty("appIcon", AppIcon.XXX)},
 * текст, или их комбинацию. При selected=true фон подсвечивается акцентным цветом.
 */
public class ModernToggleButton extends JToggleButton {

	private static Color bgOff() { return GuiApp.theme.getBgCard(); }

	private static Color bgOn() { return GuiApp.theme.getAccent(); }

	private static Color bgHoverOff() { return GuiApp.theme.getBtnHoverBg(); }

	private static Color bgHoverOn() { return GuiApp.theme.getAccentBright(); }

	private float hoverProgress = 0f;
	private Timer hoverTimer;
	private float toggleProgress = 0f;
	private Timer toggleTimer;
	private UiAnimator.RippleState ripple;

	public ModernToggleButton(String text) {
		super(text);
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setFont(new Font("SansSerif", Font.PLAIN, 12));
		UiAnimator.applyHandCursor(this);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				animateHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				animateHover(false);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				ripple = UiAnimator.startRipple(e.getX(), e.getY(), ModernToggleButton.this);
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

		Color bgBase = UiAnimator.lerp(bgOff(), bgOn(), toggleProgress);
		Color bgHover = UiAnimator.lerp(bgHoverOff(), bgHoverOn(), toggleProgress);
		Color bg = UiAnimator.lerp(bgBase, bgHover, hoverProgress);

		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

		UiAnimator.paintRipple(g2, ripple, getWidth(), getHeight());

		Color fg = ContrastTextRenderer.contrastLerp(bgBase, bgHover, hoverProgress);
		Object appIconProp = getClientProperty("appIcon");
		Icon icon = appIconProp instanceof AppIcon ai ? ai.colored(fg) : getIcon();
		String text = getText();
		boolean hasIcon = icon != null;
		boolean hasText = text != null && !text.isEmpty();

		if (hasIcon && !hasText) {
			int ix = (getWidth() - icon.getIconWidth()) / 2;
			int iy = (getHeight() - icon.getIconHeight()) / 2;
			icon.paintIcon(this, g2, ix, iy);
		} else if (hasIcon) {
			int gap = 4;
			FontMetrics fm = g2.getFontMetrics(getFont());
			int totalW = icon.getIconWidth() + gap + fm.stringWidth(text);
			int startX = (getWidth() - totalW) / 2;
			int iy = (getHeight() - icon.getIconHeight()) / 2;
			icon.paintIcon(this, g2, startX, iy);

			g2.setColor(fg);
			g2.setFont(getFont());
			int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
			g2.drawString(text, startX + icon.getIconWidth() + gap, ty);
		} else if (hasText) {
			g2.setColor(fg);
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			int tx = (getWidth() - fm.stringWidth(text)) / 2;
			int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
			g2.drawString(text, tx, ty);
		}

		g2.dispose();
	}

	@Override
	public Dimension getPreferredSize() {
		Icon icon = getIcon();
		Object appIconProp = getClientProperty("appIcon");
		if (appIconProp instanceof AppIcon ai) {
			icon = ai.colored(Color.BLACK);
		}

		String text = getText();
		boolean hasIcon = icon != null;
		boolean hasText = text != null && !text.isEmpty();

		if (hasIcon && !hasText) {
			return new Dimension(icon.getIconWidth() + 16, 28);
		}

		FontMetrics fm = getFontMetrics(getFont());
		int textW = hasText ? fm.stringWidth(text) : 0;
		int iconW = hasIcon ? icon.getIconWidth() + 4 : 0;
		return new Dimension(textW + iconW + 24, 28);
	}
}
