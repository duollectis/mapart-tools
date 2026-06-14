package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;

import javax.swing.*;
import java.awt.*;

/**
 * JLabel с анимированным кросс-фейдом при смене текста.
 * При вызове {@link #fadeToText(String)} текущий текст плавно исчезает,
 * затем устанавливается новый и плавно появляется.
 */
public class FadingLabel extends JLabel {

	private static final int FADE_DURATION_MS = 100;

	private final AnimatedFloat alpha = new AnimatedFloat(1f);

	public FadingLabel(String text) {
		super(text);
		setOpaque(false);
	}

	/** Плавно меняет текст через fade-out → смена → fade-in. */
	public void fadeToText(String newText) {
		if (newText.equals(getText())) {
			return;
		}

		alpha.animateTo(0f, FADE_DURATION_MS, v -> repaint(), () -> {
			setText(newText);
			alpha.animateTo(1f, FADE_DURATION_MS, v -> repaint());
		});
	}

	/** Мгновенно устанавливает текст без анимации. */
	public void setTextInstant(String text) {
		alpha.set(1f);
		setText(text);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.get()));
		super.paintComponent(g2);
		g2.dispose();
	}
}
