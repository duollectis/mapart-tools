package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.anim.UiAnimator;

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
	private boolean layoutInProgress;

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

	// Отключаем оптимизированное рисование: Swing будет перерисовывать
	// дочерние компоненты через этот контейнер (с его clip), а не напрямую.
	// Без этого RepaintManager рисует слайдеры за пределами clip-rect.
	@Override
	public boolean isOptimizedDrawingEnabled() {
		return false;
	}

	// Во время layout-прохода возвращаем полную высоту содержимого,
	// чтобы layout-менеджер правильно расставил дочерние компоненты.
	// В остальное время — реальный clipHeight, чтобы не триггерить
	// repaint за пределами компонента через RepaintManager.
	@Override
	public int getHeight() {
		return layoutInProgress ? resolveFullHeight() : super.getHeight();
	}

	// GridBagLayout использует getSize() (а не getHeight()) при layoutContainer.
	// Переопределяем чтобы GridBagLayout видел полную высоту и не сжимал компоненты.
	@Override
	public Dimension getSize() {
		return layoutInProgress
				? new Dimension(super.getWidth(), resolveFullHeight())
				: super.getSize();
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

	// Родительский BoxLayout/FullWidthLayout может выделить меньше места чем fullH
	// (пока аккордеон ещё анимируется). Игнорируем высоту от родителя и всегда
	// устанавливаем реальный размер = fullH, чтобы GridBagLayout внутри не сжимал
	// дочерние компоненты. Clip в paintChildren обрезает видимую область до clipHeight.
	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, resolveFullHeight());
	}

	/**
	 * Форсирует layout с полной высотой содержимого через флаг {@code layoutInProgress}.
	 * Во время вызова {@code super.doLayout()} метод {@code getHeight()} возвращает
	 * полную высоту, поэтому layout-менеджер правильно расставляет дочерние компоненты.
	 * Реальный размер компонента при этом не меняется — нет лишних repaint/invalidate.
	 */
	@Override
	public void doLayout() {
		layoutInProgress = true;

		try {
			super.doLayout();
		} finally {
			layoutInProgress = false;
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

		// Пересекаем clipHeight с clip из переданного Graphics (в координатах этого компонента).
		// Это гарантирует что AnimatedPanel не выйдет за пределы contentWrapper
		// даже когда тот анимируется и имеет меньшую высоту чем полный контент.
		int w = getWidth();
		Rectangle gClip = g.getClipBounds();
		int effectiveH;

		if (gClip == null) {
			effectiveH = clipHeight;
		} else {
			Rectangle own = new Rectangle(0, 0, w, clipHeight);
			Rectangle intersection = own.intersection(gClip);
			effectiveH = intersection.isEmpty() ? 0 : intersection.y + intersection.height;
		}

		if (effectiveH <= 0) {
			return;
		}

		// Рисуем дочерние компоненты в offscreen-буфер, затем накладываем с альфой.
		// Прямое применение AlphaComposite к g2 не работает для Swing-компонентов —
		// каждый дочерний компонент создаёт свой Graphics и сбрасывает composite.
		BufferedImage buf = new BufferedImage(w, effectiveH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D bufG = buf.createGraphics();
		bufG.setClip(0, 0, w, effectiveH);
		super.paintChildren(bufG);
		bufG.dispose();

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setClip(0, 0, w, effectiveH);
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
