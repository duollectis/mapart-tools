package org.duollectis.mapart.tools.gui.util;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Глобальный синглтон тултипа приложения.
 * Рисуется в {@link JLayeredPane#DRAG_LAYER} корневого окна — поверх всех компонентов.
 *
 * <p>Позиционирование: если курсор находится в левой половине окна — тултип появляется
 * справа от курсора, иначе — слева. Если текст не помещается в одну строку по ширине
 * окна, выполняется перенос по словам.
 *
 * <p>Использование:
 * <pre>
 *     AppTooltip.install(button, "Подсказка");
 * </pre>
 */
@UtilityClass
public class AppTooltip {

	private static final int PADDING_H = 10;
	private static final int PADDING_V = 6;
	private static final int ARC = 6;
	private static final int OPEN_DURATION_MS = 1000;
	private static final int CLOSE_DURATION_MS = 800;
	private static final int CURSOR_OFFSET = 14;
	private static final int MAX_TOOLTIP_WIDTH = 260;
	private static final Font TOOLTIP_FONT = new Font("SansSerif", Font.PLAIN, 12);

	private static TooltipPanel activePanel;
	private static JLayeredPane activeLayer;
	private static String activeText;

	private static TooltipPanel closingPanel;
	private static JLayeredPane closingLayer;

	/**
	 * Устанавливает кастомный тултип на компонент, заменяя стандартный Swing-тултип.
	 * Тултип появляется при наведении и скрывается при уходе курсора.
	 *
	 * @param component компонент, на который вешается тултип
	 * @param text      текст тултипа
	 */
	public static void install(JComponent component, String text) {
		component.setToolTipText(null);
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				show(component, text);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				move();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hide();
			}

			@Override
			public void mousePressed(MouseEvent e) {
				hide();
			}
		});
	}

	/**
	 * Показывает тултип с текстом {@code text} рядом с курсором мыши.
	 * Если тултип уже показан с тем же текстом — только обновляет позицию.
	 *
	 * @param anchor компонент, от которого берётся {@link JLayeredPane} для рендера
	 * @param text   текст тултипа
	 */
	public static void show(Component anchor, String text) {
		if (text == null || text.isBlank()) {
			return;
		}

		JLayeredPane layeredPane = getLayeredPane(anchor);

		if (layeredPane == null) {
			return;
		}

		if (text.equals(activeText) && activePanel != null) {
			updatePosition(layeredPane);
			return;
		}

		removeActivePanel();

		activeText = text;
		activeLayer = layeredPane;

		List<String> lines = wrapText(text, layeredPane);
		FontMetrics fm = layeredPane.getFontMetrics(TOOLTIP_FONT);
		int lineH = fm.getHeight();
		int tooltipW = computeTooltipWidth(lines, fm);
		int tooltipH = lineH * lines.size() + PADDING_V * 2;

		Point pos = resolvePosition(layeredPane, tooltipW, tooltipH);
		activePanel = new TooltipPanel(lines, tooltipW, tooltipH, pos.x, pos.y);

		layeredPane.add(activePanel, JLayeredPane.DRAG_LAYER);
		layeredPane.revalidate();
		layeredPane.repaint();
	}

	/**
	 * Обновляет позицию активного тултипа по текущей позиции курсора.
	 * Вызывать из {@code mouseMoved} компонента, над которым висит тултип.
	 */
	public static void move() {
		if (activePanel != null && activeLayer != null) {
			updatePosition(activeLayer);
		}
	}

	/** Запускает анимацию закрытия тултипа. После завершения удаляет панель. */
	public static void hide() {
		if (activePanel == null) {
			return;
		}

		if (closingPanel != null && closingLayer != null) {
			closingPanel.stopAnimation();
			closingLayer.remove(closingPanel);
			closingLayer.repaint();
		}

		closingPanel = activePanel;
		closingLayer = activeLayer;

		activePanel = null;
		activeLayer = null;
		activeText = null;

		closingPanel.animateClose(() -> {
			closingLayer.remove(closingPanel);
			closingLayer.repaint();
			closingPanel = null;
			closingLayer = null;
		});
	}

	private static void removeActivePanel() {
		if (activePanel == null) {
			return;
		}

		activePanel.stopAnimation();

		if (activeLayer != null) {
			activeLayer.remove(activePanel);
			activeLayer.repaint();
		}

		activePanel = null;
		activeLayer = null;
		activeText = null;
	}

	private static void updatePosition(JLayeredPane layeredPane) {
		if (activePanel == null) {
			return;
		}

		Point pos = resolvePosition(layeredPane, activePanel.tooltipW, activePanel.tooltipH);
		activePanel.setBounds(pos.x, pos.y, activePanel.tooltipW, activePanel.tooltipH);
		layeredPane.repaint();
	}

	/**
	 * Вычисляет позицию тултипа в координатах {@code layeredPane}.
	 * Тултип показывается справа от курсора, если туда влезает.
	 * Влево уходит только если справа не хватает места, а слева места больше.
	 * По вертикали: ниже курсора, если не выходит за нижний край, иначе выше.
	 */
	private static Point resolvePosition(JLayeredPane layeredPane, int tooltipW, int tooltipH) {
	 Point mouseScreen = MouseInfo.getPointerInfo().getLocation();
	 Point layerScreen = layeredPane.getLocationOnScreen();

	 int mouseX = mouseScreen.x - layerScreen.x;
	 int mouseY = mouseScreen.y - layerScreen.y;

	 int spaceRight = layeredPane.getWidth() - mouseX - CURSOR_OFFSET;
	 int spaceLeft = mouseX - CURSOR_OFFSET;
	 int x = spaceRight >= tooltipW || spaceRight >= spaceLeft
	 		? mouseX + CURSOR_OFFSET
	 		: mouseX - tooltipW - CURSOR_OFFSET;

	 x = Math.max(4, Math.min(x, layeredPane.getWidth() - tooltipW - 4));

		int y = mouseY + CURSOR_OFFSET;

		if (y + tooltipH > layeredPane.getHeight() - 4) {
			y = mouseY - tooltipH - 4;
		}

		y = Math.max(4, y);

		return new Point(x, y);
	}

	/**
	 * Разбивает текст на строки с переносом по словам, чтобы каждая строка
	 * не превышала {@link #MAX_TOOLTIP_WIDTH} пикселей.
	 */
	private static List<String> wrapText(String text, JLayeredPane layeredPane) {
		FontMetrics fm = layeredPane.getFontMetrics(TOOLTIP_FONT);
		int maxW = Math.min(MAX_TOOLTIP_WIDTH, layeredPane.getWidth() - PADDING_H * 2 - 16);

		List<String> result = new ArrayList<>();
		String[] words = text.split(" ");
		StringBuilder current = new StringBuilder();

		for (String word : words) {
			String candidate = current.isEmpty() ? word : current + " " + word;

			if (fm.stringWidth(candidate) <= maxW) {
				current = new StringBuilder(candidate);
			} else {
				if (current.isEmpty()) {
					result.add(word);
				} else {
					result.add(current.toString());
					current = new StringBuilder(word);
				}
			}
		}

		if (!current.isEmpty()) {
			result.add(current.toString());
		}

		return result.isEmpty() ? List.of(text) : result;
	}

	private static int computeTooltipWidth(List<String> lines, FontMetrics fm) {
		int maxLineW = lines.stream()
				.mapToInt(fm::stringWidth)
				.max()
				.orElse(0);
		return maxLineW + PADDING_H * 2;
	}

	private static JLayeredPane getLayeredPane(Component component) {
		JRootPane rootPane = SwingUtilities.getRootPane(component);
		return rootPane != null ? rootPane.getLayeredPane() : null;
	}

	// ── Панель тултипа ────────────────────────────────────────────────────────

	private static final class TooltipPanel extends JPanel {

		private float animProgress = 0f;
		final int tooltipW;
		final int tooltipH;
		private final List<String> lines;
		private Timer activeTimer;

		TooltipPanel(List<String> lines, int tooltipW, int tooltipH, int x, int y) {
			super(null);
			this.lines = lines;
			this.tooltipW = tooltipW;
			this.tooltipH = tooltipH;

			setOpaque(false);
			setBounds(x, y, tooltipW, tooltipH);

			activeTimer = UiAnimator.animateFloat(0f, 1f, OPEN_DURATION_MS, progress -> {
				animProgress = easeOutQuart(progress);
				repaint();
			}, null);
		}

		void stopAnimation() {
			if (activeTimer != null) {
				activeTimer.stop();
				activeTimer = null;
			}
		}

		void animateClose(Runnable onDone) {
			stopAnimation();

			float startProgress = animProgress;

			activeTimer = UiAnimator.animateFloat(0f, 1f, CLOSE_DURATION_MS, progress -> {
				animProgress = startProgress * (1f - easeOutQuart(progress));
				repaint();
			}, onDone);
		}

		private static float easeOutQuart(float t) {
			float inv = 1f - t;
			return 1f - inv * inv * inv * inv;
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (animProgress <= 0f) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int animW = (int) (tooltipW * animProgress);
			g2.setClip(new Rectangle2D.Float(0, 0, animW, tooltipH));
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, animProgress));

			g2.setColor(GuiApp.theme.getTooltipBg());
			g2.fillRoundRect(0, 0, tooltipW, tooltipH, ARC, ARC);

			g2.setColor(GuiApp.theme.getBorder());
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(0, 0, tooltipW - 1, tooltipH - 1, ARC, ARC);

			g2.setFont(TOOLTIP_FONT);
			g2.setColor(GuiApp.theme.getText());

			FontMetrics fm = g2.getFontMetrics(TOOLTIP_FONT);
			int lineH = fm.getHeight();

			for (int i = 0; i < lines.size(); i++) {
				g2.drawString(lines.get(i), PADDING_H, PADDING_V + fm.getAscent() + i * lineH);
			}

			g2.dispose();
		}
	}
}
