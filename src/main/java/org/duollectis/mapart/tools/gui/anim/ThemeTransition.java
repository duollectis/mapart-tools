package org.duollectis.mapart.tools.gui.anim;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Плавная анимация смены темы: fade-overlay скрывает пересборку UI,
 * а параллельно идёт цветовой переход через интерполяцию {@code previousTheme → targetTheme}.
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>Делает скриншот текущего состояния окна.</li>
 *   <li>Накладывает overlay-панель с этим скриншотом поверх {@code JLayeredPane}.</li>
 *   <li>Немедленно выполняет {@code rebuildAction} (пересборка UI под overlay).</li>
 *   <li>Параллельно анимирует цветовой переход через {@link GuiApp#setColorProgress(float)}.</li>
 *   <li>Плавно убирает overlay через fade-out — пользователь видит плавный переход.</li>
 * </ol>
 */
@UtilityClass
public class ThemeTransition {

	private static final int FADE_DURATION_MS = 200;
	private static final int COLOR_DURATION_MS = 220;

	// Активный таймер цветового перехода — останавливается при повторном вызове
	private static Timer activeColorTimer;

	/**
	 * Запускает плавный цветовой переход без пересборки UI.
	 * Если предыдущая анимация ещё идёт — останавливает её и начинает новую.
	 *
	 * @param window    окно для перерисовки
	 * @param themeName имя новой темы
	 */
	public static void applyColorOnly(JFrame window, String themeName) {
		if (activeColorTimer != null) {
			activeColorTimer.stop();
		}

		GuiApp.applyTheme(themeName);
		GuiApp.setColorProgress(0f);
		UpdatableRegistry.beginThemeAnim();

		activeColorTimer = UiAnimator.animateFloat(0f, 1f, COLOR_DURATION_MS, progress -> {
			GuiApp.setColorProgress(progress);
			UpdatableRegistry.fireThemeAnimFrame();
		}, () -> {
			GuiApp.setColorProgress(1f);
			UpdatableRegistry.endThemeAnim();
			UpdatableRegistry.fireTheme(window);
			activeColorTimer = null;
		});
	}

	/**
	 * Запускает анимацию смены темы для указанного окна.
	 *
	 * @param window        окно, в котором происходит смена темы
	 * @param rebuildAction действие пересборки UI (вызывается мгновенно под overlay)
	 */
	public static void apply(JFrame window, Runnable rebuildAction) {
		JLayeredPane layeredPane = window.getLayeredPane();
		BufferedImage snapshot = captureSnapshot(layeredPane);

		FadeOverlay overlay = new FadeOverlay(snapshot);
		overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
		layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);
		layeredPane.revalidate();
		layeredPane.repaint();

		SwingUtilities.invokeLater(() -> {
			GuiApp.setColorProgress(0f);
			UpdatableRegistry.beginThemeAnim();
			rebuildAction.run();

			UiAnimator.animateFloat(0f, 1f, COLOR_DURATION_MS, progress -> {
				GuiApp.setColorProgress(progress);
				UpdatableRegistry.fireThemeAnimFrame();
			}, () -> {
				GuiApp.setColorProgress(1f);
				UpdatableRegistry.endThemeAnim();
				UpdatableRegistry.fireTheme(window);
			});

			UiAnimator.animateFloat(1f, 0f, FADE_DURATION_MS, alpha -> {
				overlay.setAlpha(alpha);
				overlay.repaint();
			}, () -> {
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

	// ── Внутренний overlay-компонент ──────────────────────────────────────────

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
