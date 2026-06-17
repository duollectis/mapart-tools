package org.duollectis.mapart.tools.gui.anim;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Плавная анимация смены языка: overlay-снимок скрывает мгновенную замену текстов,
 * затем плавно исчезает, открывая уже обновлённый UI.
 *
 * <p>Использует тот же подход что и {@link ThemeTransition} — overlay добавляется
 * в {@code JLayeredPane} на {@code DRAG_LAYER}, что гарантирует отрисовку поверх
 * всех дочерних компонентов при любом частичном {@code repaint()}.
 */
@UtilityClass
public class LangTransition {

	private static final int FADE_DURATION_MS = 180;

	/**
	 * Применяет смену языка с анимацией fade-out через overlay на {@code JLayeredPane}.
	 *
	 * @param window корневое окно приложения
	 */
	public static void apply(JFrame window) {
		JLayeredPane layeredPane = window.getLayeredPane();

		UpdatableRegistry.beginThemeAnim();
		BufferedImage snapshot = captureSnapshot(layeredPane);

		FadeOverlay overlay = new FadeOverlay(snapshot);
		overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
		layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);
		layeredPane.revalidate();
		layeredPane.repaint();

		SwingUtilities.invokeLater(() -> {
			UpdatableRegistry.fireLang();

			UiAnimator.animateFloat(1f, 0f, FADE_DURATION_MS, alpha -> {
				overlay.setAlpha(alpha);
				overlay.repaint();
			}, () -> {
				UpdatableRegistry.endThemeAnim();
				layeredPane.remove(overlay);
				layeredPane.revalidate();
				layeredPane.repaint();
			});
		});
	}

	private static BufferedImage captureSnapshot(JLayeredPane layeredPane) {
		int width = Math.max(1, layeredPane.getWidth());
		int height = Math.max(1, layeredPane.getHeight());
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();
		layeredPane.paintAll(g2);
		g2.dispose();
		return image;
	}

	private static final class FadeOverlay extends JPanel {

		private final BufferedImage snapshot;
		private float alpha = 1f;

		FadeOverlay(BufferedImage snapshot) {
			this.snapshot = snapshot;
			setOpaque(false);
		}

		void setAlpha(float alpha) {
			this.alpha = alpha;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			g2.drawImage(snapshot, 0, 0, null);
			g2.dispose();
		}
	}
}
