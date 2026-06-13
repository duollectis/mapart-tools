package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Панель с анимированным раскрытием/скрытием по высоте.
 * Содержимое не сжимается — используется clip-rect для обрезки рисования.
 * Дочерние компоненты всегда занимают полную высоту, видимая область
 * плавно увеличивается/уменьшается через {@code clipHeight}.
 *
 * <p>По умолчанию создаётся в свёрнутом состоянии (высота = 0).
 * Для мгновенного раскрытия без анимации используй {@link #expandInstant()}.
 */
public class AnimatedPanel extends JPanel {

	private static final int ANIMATION_DURATION_MS = 200;

	private boolean expanded;
	private int clipHeight;
	private float alpha;

	public AnimatedPanel(LayoutManager layout) {
		super(layout);
		setOpaque(false);
		clipHeight = 0;
		alpha = 0f;
	}

	public AnimatedPanel() {
		this(new FlowLayout());
	}

	/**
	 * Анимирует раскрытие или скрытие панели по высоте.
	 * Содержимое не сжимается — clip-rect плавно меняется от 0 до fullH.
	 *
	 * @param show      {@code true} — раскрыть, {@code false} — свернуть
	 * @param onResized колбэк на каждом кадре и по завершении анимации
	 */
	public void animateVisible(boolean show, Runnable onResized) {
		if (expanded == show) {
			return;
		}

		expanded = show;

		int fullH = resolveFullHeight();
		int fromH = show ? 0 : fullH;
		int toH = show ? fullH : 0;
		float fromA = show ? 0f : 1f;
		float toA = show ? 1f : 0f;

		clipHeight = fromH;
		alpha = fromA;

		UiAnimator.animateFloat(
			0f, 1f, ANIMATION_DURATION_MS,
			t -> {
				clipHeight = Math.round(fromH + (toH - fromH) * t);
				alpha = fromA + (toA - fromA) * t;
				notifyParent();
				onResized.run();
			},
			() -> {
				clipHeight = toH;
				alpha = toA;
				notifyParent();
				onResized.run();
			}
		);
	}

	/** Мгновенно раскрывает панель без анимации. */
	public void expandInstant() {
		expanded = true;
		clipHeight = resolveFullHeight();
		alpha = 1f;
		notifyParent();
	}

	/** Мгновенно сворачивает панель без анимации. */
	public void collapseInstant() {
		expanded = false;
		clipHeight = 0;
		alpha = 0f;
		notifyParent();
	}

	public boolean isExpanded() {
		return expanded;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(super.getPreferredSize().width, clipHeight);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, clipHeight);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, clipHeight);
	}

	/**
	 * Форсирует layout с полной высотой содержимого, чтобы дочерние компоненты
	 * не сжимались во время анимации clip-rect.
	 */
	@Override
	public void doLayout() {
		int fullH = resolveFullHeight();
		int savedH = getHeight();

		if (fullH > savedH) {
			setSize(getWidth(), fullH);
			super.doLayout();
			setSize(getWidth(), savedH);
		} else {
			super.doLayout();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (clipHeight <= 0 || alpha <= 0f) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setClip(0, 0, getWidth(), clipHeight);
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		super.paintComponent(g2);
		g2.dispose();
	}

	@Override
	protected void paintChildren(Graphics g) {
		if (clipHeight <= 0 || alpha <= 0f) {
			return;
		}

		// Рисуем дочерние компоненты в offscreen-буфер, затем накладываем с альфой.
		// Прямое применение AlphaComposite к g2 не работает для Swing-компонентов —
		// каждый дочерний компонент создаёт свой Graphics и сбрасывает composite.
		int w = getWidth();
		int h = clipHeight;
		BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D bufG = buf.createGraphics();
		bufG.setClip(0, 0, w, h);
		super.paintChildren(bufG);
		bufG.dispose();

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setClip(0, 0, w, h);
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g2.drawImage(buf, 0, 0, null);
		g2.dispose();
	}

	private int resolveFullHeight() {
		Dimension saved = super.getPreferredSize();
		return saved.height > 0 ? saved.height : getLayout().preferredLayoutSize(this).height;
	}

	private void notifyParent() {
		Container parent = getParent();

		if (parent != null) {
			parent.revalidate();
			parent.repaint();
		} else {
			revalidate();
			repaint();
		}
	}
}
