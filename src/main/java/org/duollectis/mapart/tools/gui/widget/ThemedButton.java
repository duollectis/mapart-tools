package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import java.awt.*;

/**
 * Кнопка с тремя визуальными стилями для панели настроек.
 * Инкапсулирует анонимный класс с {@code paintComponent}, настройку внешнего вида
 * и подписку на смену темы — устраняет дублирование в {@code SettingsWidgetFactory}.
 */
public class ThemedButton extends JButton {

	public enum Style {
		/** Залитая акцентным цветом, жирный шрифт 13px, отступы 8/16, высота 40. */
		PRIMARY,
		/** Акцентная рамка + полупрозрачная заливка, шрифт 12px, отступы 6/12, высота 36. */
		ACCENT,
		/** Заливка INPUT + рамка BORDER, шрифт 12px, отступы 6/12, высота 36. */
		THEMED
	}

	private static final int CORNER_RADIUS = 10;
	private static final int PRIMARY_MAX_HEIGHT = 40;
	private static final int SECONDARY_MAX_HEIGHT = 36;
	private static final int ACCENT_FILL_ALPHA = 30;
	private static final float HOVER_BRIGHTEN = 1.15f;
	private static final float HOVER_DARKEN = 0.88f;

	private final Style style;

	public ThemedButton(String text, Style style, MainWindow w) {
		super(text);
		this.style = style;

		applyStaticProperties();
		applyThemeColors();

		UpdatableRegistry.onThemeAnimFrame(() -> {
			applyThemeColors();
			repaint();
		});

		w.addHoverEffect(this);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		float t = hoverProgress();

		switch (style) {
			case PRIMARY -> {
				Color base = MainWindow.ACCENT();
				Color hovered = brighten(base);
				paintPrimary(g2);
				setForeground(ContrastTextRenderer.contrastLerp(base, hovered, t));
			}
			case ACCENT -> {
				paintAccent(g2);
				Color accent = MainWindow.ACCENT();
				setForeground(UiAnimator.lerp(accent, brighten(accent), t));
			}
			case THEMED -> {
				Color base = MainWindow.INPUT();
				Color hovered = darken(base);
				paintThemed(g2);
				setForeground(ContrastTextRenderer.contrastLerp(base, hovered, t));
			}
		}

		g2.dispose();
		super.paintComponent(g);
	}

	private Color paintPrimary(Graphics2D g2) {
		Color base = MainWindow.ACCENT();
		Color hovered = brighten(base);
		float t = hoverProgress();
		Color bg = UiAnimator.lerp(base, hovered, t);
		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
		return bg;
	}

	private Color paintAccent(Graphics2D g2) {
		Color base = MainWindow.ACCENT();
		Color hovered = brighten(base);
		float t = hoverProgress();
		Color fill = UiAnimator.lerp(base, hovered, t);
		g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), ACCENT_FILL_ALPHA));
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
		g2.setColor(fill);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS);
		return fill;
	}

	private Color paintThemed(Graphics2D g2) {
		Color base = MainWindow.INPUT();
		Color hovered = darken(base);
		float t = hoverProgress();
		Color bg = UiAnimator.lerp(base, hovered, t);
		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
		g2.setColor(MainWindow.BORDER());
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS);
		return bg;
	}

	private void applyStaticProperties() {
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		if (style == Style.PRIMARY) {
			setFont(new Font("SansSerif", Font.BOLD, 13));
			setMargin(new Insets(8, 16, 8, 16));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, PRIMARY_MAX_HEIGHT));
		} else {
			setFont(new Font("SansSerif", Font.PLAIN, 12));
			setMargin(new Insets(6, 12, 6, 12));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, SECONDARY_MAX_HEIGHT));
		}
	}

	private float hoverProgress() {
		Object val = getClientProperty("hoverProgress");
		return val instanceof Float f ? f : 0f;
	}

	private static Color brighten(Color color) {
		return new Color(
			Math.min(255, (int) (color.getRed() * HOVER_BRIGHTEN)),
			Math.min(255, (int) (color.getGreen() * HOVER_BRIGHTEN)),
			Math.min(255, (int) (color.getBlue() * HOVER_BRIGHTEN)),
			color.getAlpha()
		);
	}

	private static Color darken(Color color) {
		return new Color(
			(int) (color.getRed() * HOVER_DARKEN),
			(int) (color.getGreen() * HOVER_DARKEN),
			(int) (color.getBlue() * HOVER_DARKEN),
			color.getAlpha()
		);
	}

	private void applyThemeColors() {
		// Начальный цвет до первого paintComponent — используем статичный цвет темы
		Color fg = switch (style) {
			case PRIMARY -> MainWindow.TEXT_ON_ACCENT();
			case ACCENT -> MainWindow.ACCENT();
			case THEMED -> MainWindow.TEXT();
		};
		setForeground(fg);
	}
}
