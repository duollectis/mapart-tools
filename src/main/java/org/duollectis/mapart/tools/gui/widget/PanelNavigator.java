package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.AppIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class PanelNavigator {

	private static final int ANIM_MS = 550;
	private static final int NAV_BAR_HEIGHT = 44;

	private final JLayeredPane layeredPane;
	private final JPanel rootPanel;
	private final Deque<JPanel> backStack = new ArrayDeque<>();

	private JPanel currentOverlay;
	private JPanel currentNavBar;
	private Timer activeAnim;
	private JPanel activeAnimCanvas;

	private PanelNavigator(JLayeredPane layeredPane, JPanel rootPanel) {
		this.layeredPane = layeredPane;
		this.rootPanel = rootPanel;
		rootPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
		layeredPane.add(rootPanel, JLayeredPane.DEFAULT_LAYER);
		layeredPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				relayout();
			}
		});
	}

	public static JPanel wrap(JPanel rootPanel, PanelNavigator[] result) {
		JLayeredPane layeredPane = new JLayeredPane();
		layeredPane.setLayout(null);
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public void doLayout() {
				super.doLayout();
				layeredPane.setBounds(0, 0, getWidth(), getHeight());
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(layeredPane, BorderLayout.CENTER);
		result[0] = new PanelNavigator(layeredPane, rootPanel);
		return wrapper;
	}

	/**
	 * Показывает новую панель с анимацией въезда справа.
	 * Добавляет панель в иерархию за экраном, делает снимок через validate+paintAll,
	 * затем анимирует только снимок — реальная панель не перерисовывается на каждом кадре.
	 * После завершения анимации реальная панель возвращается на место.
	 */
	public void push(JPanel panel, Runnable onShown) {
		stopAnim();

		if (bringToFrontIfExists(panel.getClass())) {
			finalizePanelPosition();
			return;
		}

		if (currentOverlay != null) {
			backStack.push(currentOverlay);
		}

		removeCurrentNavBar();
		currentOverlay = panel;

		int w = layeredPane.getWidth();
		int h = layeredPane.getHeight();
		int contentY = resolveContentY(panel);
		int contentH = h - contentY;

		// Добавляем за экраном для корректного layout
		panel.setBounds(-w, contentY, w, contentH);
		layeredPane.add(panel, JLayeredPane.PALETTE_LAYER);
		panel.validate();

		JPanel navBarProto = null;
		if (panel instanceof NavigablePanel nav) {
			navBarProto = buildNavBar(nav.getNavTitle());
			navBarProto.setBounds(-w, 0, w, NAV_BAR_HEIGHT);
			layeredPane.add(navBarProto, JLayeredPane.MODAL_LAYER);
			navBarProto.validate();
		}

		// Снимок после validate — компоненты имеют корректные размеры и шрифты
		BufferedImage snapshot = renderSnapshot(panel, w, contentH);
		BufferedImage navSnapshot = navBarProto != null ? renderSnapshot(navBarProto, w, NAV_BAR_HEIGHT) : null;
		BufferedImage bgSnapshot = renderSnapshot(rootPanel, w, h);

		// Убираем реальные панели за экран — анимируем только снимок
		panel.setBounds(-w * 2, contentY, w, contentH);
		if (navBarProto != null) {
			navBarProto.setBounds(-w * 2, 0, w, NAV_BAR_HEIGHT);
		}

		final BufferedImage fs = snapshot;
		final BufferedImage fns = navSnapshot;
		final BufferedImage fbg = bgSnapshot;
		final JPanel finalNavProto = navBarProto;
		final int cy = contentY;
		final int ch = contentH;

		int[] offsetX = {w};
		JPanel animCanvas = new JPanel(null) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.drawImage(fbg, 0, 0, null);
				int arc = 24;
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRoundRect(offsetX[0], cy, w, ch, arc, arc);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(offsetX[0], cy, w - 1, ch - 1, arc, arc);
				g2.drawImage(fs, offsetX[0], cy, null);
				if (fns != null) {
					g2.drawImage(fns, offsetX[0], 0, null);
				}
				g2.dispose();
			}
		};
		animCanvas.setOpaque(true);
		animCanvas.setBounds(0, 0, w, h);
		layeredPane.add(animCanvas, JLayeredPane.DRAG_LAYER);
		activeAnimCanvas = animCanvas;
		layeredPane.repaint();

		activeAnim = UiAnimator.animateProgress(ANIM_MS, t -> {
			offsetX[0] = (int) (w * (1f - UiAnimator.easeOutQuint(t)));
			animCanvas.repaint();
		}, () -> {
			layeredPane.remove(animCanvas);
			activeAnimCanvas = null;

			panel.setBounds(0, cy, w, ch);
			if (finalNavProto != null) {
				currentNavBar = finalNavProto;
				currentNavBar.setBounds(0, 0, w, NAV_BAR_HEIGHT);
			}

			layeredPane.repaint();

			if (onShown != null) {
				onShown.run();
			}
		});
	}

	public void popIfPossible() {
		if (currentOverlay != null) {
			pop(null);
		}
	}

	/**
	 * Возвращает к предыдущей панели с анимацией ухода вправо.
	 * Делает снимок уходящей панели и анимирует только его.
	 */
	public void pop(Runnable onHidden) {
		if (currentOverlay == null) {
			return;
		}

		stopAnim();
		JPanel leaving = currentOverlay;
		JPanel leavingNavBar = currentNavBar;
		int w = layeredPane.getWidth();
		int h = layeredPane.getHeight();
		int contentY = leaving.getY();
		int contentH = leaving.getHeight();

		BufferedImage snapshot = renderSnapshot(leaving, w, contentH);
		BufferedImage navSnapshot = leavingNavBar != null ? renderSnapshot(leavingNavBar, w, NAV_BAR_HEIGHT) : null;

		layeredPane.remove(leaving);
		if (leavingNavBar != null) {
			layeredPane.remove(leavingNavBar);
		}

		currentOverlay = backStack.isEmpty() ? null : backStack.pop();
		currentNavBar = resolveNavBarForCurrent();

		if (currentOverlay != null) {
			int prevContentY = resolveContentY(currentOverlay);
			currentOverlay.setBounds(0, prevContentY, w, h - prevContentY);
		}

		layeredPane.revalidate();

		BufferedImage bgSnapshot = renderSnapshot(rootPanel, w, h);

		final BufferedImage fs = snapshot;
		final BufferedImage fns = navSnapshot;
		final BufferedImage fbg = bgSnapshot;
		final int cy = contentY;
		final int ch = contentH;

		int[] offsetX = {0};
		JPanel animCanvas = new JPanel(null) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.drawImage(fbg, 0, 0, null);
				int arc = 24;
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRoundRect(offsetX[0], cy, w, ch, arc, arc);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(offsetX[0], cy, w - 1, ch - 1, arc, arc);
				g2.drawImage(fs, offsetX[0], cy, null);
				if (fns != null) {
					g2.drawImage(fns, offsetX[0], 0, null);
				}
				g2.dispose();
			}
		};
		animCanvas.setOpaque(true);
		animCanvas.setBounds(0, 0, w, h);
		layeredPane.add(animCanvas, JLayeredPane.DRAG_LAYER);
		activeAnimCanvas = animCanvas;
		layeredPane.repaint();

		activeAnim = UiAnimator.animateProgress(ANIM_MS, t -> {
			offsetX[0] = (int) (w * UiAnimator.easeOutQuint(t));
			animCanvas.repaint();
		}, () -> {
			layeredPane.remove(animCanvas);
			activeAnimCanvas = null;
			layeredPane.repaint();
			if (onHidden != null) {
				onHidden.run();
			}
		});
	}

	public void popAllImmediate() {
		stopAnim();
		for (JPanel panel : backStack) {
			layeredPane.remove(panel);
		}
		backStack.clear();
		removeCurrentNavBar();
		if (currentOverlay != null) {
			layeredPane.remove(currentOverlay);
			currentOverlay = null;
		}
		relayout();
		layeredPane.repaint();
	}

	public boolean hasOverlay() {
		return currentOverlay != null;
	}

	private boolean bringToFrontIfExists(Class<?> panelClass) {
		if (currentOverlay != null && currentOverlay.getClass() == panelClass) {
			return true;
		}
		for (JPanel panel : backStack) {
			if (panel.getClass() == panelClass) {
				popUntil(panelClass);
				return true;
			}
		}
		return false;
	}

	private void popUntil(Class<?> targetClass) {
		if (currentOverlay == null || currentOverlay.getClass() == targetClass) {
			return;
		}
		pop(() -> popUntil(targetClass));
	}

	/**
	 * Рендерит компонент в BufferedImage после validate — шрифты и цвета корректны.
	 */
	private static BufferedImage renderSnapshot(JComponent component, int w, int h) {
		if (w <= 0 || h <= 0) {
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		}
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		component.paintAll(g2);
		g2.dispose();
		return img;
	}

	private JPanel buildNavBar(String title) {
		JPanel bar = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(GuiApp.theme.getBorder());
				g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
				g2.dispose();
			}
		};
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 16));
		JButton backBtn = new JButton();
		backBtn.setIcon(AppIcon.PREV.colored(GuiApp.theme.getText()));
		backBtn.setContentAreaFilled(false);
		backBtn.setBorderPainted(false);
		backBtn.setFocusPainted(false);
		backBtn.setPreferredSize(new Dimension(NAV_BAR_HEIGHT, NAV_BAR_HEIGHT));
		backBtn.addActionListener(e -> pop(null));
		UiAnimator.applyHandCursor(backBtn);
		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(GuiApp.theme.getText());
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
		bar.add(backBtn, BorderLayout.WEST);
		bar.add(titleLabel, BorderLayout.CENTER);
		return bar;
	}

	private void removeCurrentNavBar() {
		if (currentNavBar != null) {
			layeredPane.remove(currentNavBar);
			currentNavBar = null;
		}
	}

	private JPanel resolveNavBarForCurrent() {
		if (currentOverlay instanceof NavigablePanel nav) {
			JPanel bar = buildNavBar(nav.getNavTitle());
			int w = layeredPane.getWidth();
			bar.setBounds(0, 0, w, NAV_BAR_HEIGHT);
			layeredPane.add(bar, JLayeredPane.MODAL_LAYER);
			return bar;
		}
		return null;
	}

	private int resolveContentY(JPanel panel) {
		return panel instanceof NavigablePanel ? NAV_BAR_HEIGHT : 0;
	}

	private void relayout() {
		int w = layeredPane.getWidth();
		int h = layeredPane.getHeight();
		rootPanel.setBounds(0, 0, w, h);
		if (currentOverlay != null) {
			int contentY = resolveContentY(currentOverlay);
			currentOverlay.setBounds(0, contentY, w, h - contentY);
		}
		if (currentNavBar != null) {
			currentNavBar.setBounds(0, 0, w, NAV_BAR_HEIGHT);
		}
	}

	private void finalizePanelPosition() {
		if (currentOverlay == null) {
			return;
		}

		int w = layeredPane.getWidth();
		int h = layeredPane.getHeight();
		int contentY = resolveContentY(currentOverlay);

		currentOverlay.setBounds(0, contentY, w, h - contentY);

		if (currentNavBar == null) {
			currentNavBar = resolveNavBarForCurrent();
		} else {
			currentNavBar.setBounds(0, 0, w, NAV_BAR_HEIGHT);
		}

		layeredPane.repaint();
	}

	private void stopAnim() {
		if (activeAnim != null) {
			activeAnim.stop();
			activeAnim = null;
		}

		if (activeAnimCanvas != null) {
			layeredPane.remove(activeAnimCanvas);
			activeAnimCanvas = null;
		}
	}
}
