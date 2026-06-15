package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.ContrastTextRenderer;
import org.duollectis.mapart.tools.gui.window.MainWindow;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Кнопка с эффектом ripple и hover-анимацией для тулбаров превью.
 * Поддерживает два режима: текстовый (label) и иконочный (icon).
 * Инкапсулирует анонимный класс с {@code paintComponent} и подписку на hover-эффект.
 */
public class RippleButton extends JButton {

	private static final int CORNER_RADIUS = 8;
	private static final int MIN_HEIGHT = 26;
	private static final float HOVER_BRIGHTEN = 1.15f;

	/**
	 * Текущая иконка для отрисовки. Может быть изменена через {@link #setCurrentIcon(AppIcon)},
	 * что позволяет динамически менять иконку (например, eye/eye_off) без потери hover-окраски.
	 */
	private AppIcon currentIcon;
	private UiAnimator.RippleState ripple;
	/** Режим акцентного фона: PLAY/STOP кнопка конвертации. */
	private boolean accentMode;
	/** Режим фона ошибки: кнопка остановки конвертации. */
	private boolean errorMode;

	/** Текстовый режим — кнопка с надписью, шрифт 11px. */
	public RippleButton(String label, MainWindow w) {
		super(label);
		currentIcon = null;
		setFont(new Font("SansSerif", Font.PLAIN, 11));
		init(w);
	}

	/** Иконочный режим — кнопка с иконкой, цвет которой адаптируется под фон. */
	public RippleButton(AppIcon icon, MainWindow w) {
		super();
		currentIcon = icon;
		init(w);
	}

	/** Иконочный режим с кастомными отступами. */
	public RippleButton(AppIcon icon, Insets insets, MainWindow w) {
		super();
		currentIcon = icon;
		init(w);
		setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
	}

	/**
	 * Меняет отображаемую иконку без потери hover-окраски.
	 * Используется для динамической смены иконок (eye/eye_off).
	 */
	public void setCurrentIcon(AppIcon icon) {
		currentIcon = icon;
		repaint();
	}

	/** Включает/выключает акцентный режим фона (для кнопки конвертации). */
	public void setAccentMode(boolean accent) {
		accentMode = accent;
		errorMode = false;
		repaint();
	}

	/** Включает режим фона ошибки (для кнопки остановки). */
	public void setErrorMode(boolean error) {
		errorMode = error;
		accentMode = false;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		float progress = hoverProgress();

		Color btnBg;
		if (accentMode) {
			Color base = GuiApp.theme.getAccent();
			Color hovered = brighten(base);
			btnBg = UiAnimator.lerp(base, hovered, progress);
		} else if (errorMode) {
			Color base = GuiApp.theme.getError();
			Color hovered = brighten(base);
			btnBg = UiAnimator.lerp(base, hovered, progress);
		} else {
			btnBg = UiAnimator.lerp(GuiApp.theme.getBgCard(), GuiApp.theme.getBtnHoverBg(), progress);
		}

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(btnBg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
		UiAnimator.paintRipple(g2, ripple, getWidth(), getHeight());
		g2.dispose();

		if (currentIcon == null) {
			setForeground(UiAnimator.lerp(GuiApp.theme.getTextDim(), GuiApp.theme.getText(), progress));
		} else {
			setIcon(currentIcon.colored(ContrastTextRenderer.contrastFor(btnBg)));
		}

		super.paintComponent(g);
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension base = super.getPreferredSize();
		return new Dimension(base.width, Math.max(base.height, MIN_HEIGHT));
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension base = super.getMinimumSize();
		return new Dimension(base.width, Math.max(base.height, MIN_HEIGHT));
	}

	private void init(MainWindow w) {
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		UiAnimator.applyHandCursor(this);
		setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				ripple = UiAnimator.startRipple(e.getX(), e.getY(), RippleButton.this);
			}
		});

		w.addHoverEffect(this);
	}

	private float hoverProgress() {
		Object value = getClientProperty("hoverProgress");
		return switch (value) {
			case AnimatedFloat af -> af.get();
			case Float f -> f;
			case null -> 0f;
			default -> 0f;
		};
	}

	private static Color brighten(Color color) {
		return new Color(
			Math.min(255, (int) (color.getRed() * HOVER_BRIGHTEN)),
			Math.min(255, (int) (color.getGreen() * HOVER_BRIGHTEN)),
			Math.min(255, (int) (color.getBlue() * HOVER_BRIGHTEN)),
			color.getAlpha()
		);
	}
}
