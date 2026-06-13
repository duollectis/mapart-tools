package org.duollectis.mapart.tools.gui.widget;

import java.awt.*;

/**
 * Layout-менеджер аналогичный {@link FlowLayout}, но корректно вычисляет
 * {@code preferredSize} с учётом переноса строк при нехватке ширины.
 * Стандартный {@link FlowLayout} всегда возвращает однострочный preferredSize,
 * из-за чего контейнер не растягивается по высоте при переносе компонентов.
 */
public class WrapLayout extends FlowLayout {

	public WrapLayout(int align, int hgap, int vgap) {
		super(align, hgap, vgap);
	}

	@Override
	public Dimension preferredLayoutSize(Container target) {
		return computeSize(target, true);
	}

	@Override
	public Dimension minimumLayoutSize(Container target) {
		return computeSize(target, false);
	}

	private Dimension computeSize(Container target, boolean preferred) {
		synchronized (target.getTreeLock()) {
			int targetWidth = target.getWidth();

			if (targetWidth == 0) {
				targetWidth = Integer.MAX_VALUE;
			}

			Insets insets = target.getInsets();
			int maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;

			int totalHeight = insets.top + insets.bottom + getVgap() * 2;
			int rowWidth = 0;
			int rowHeight = 0;
			boolean firstInRow = true;

			for (int i = 0; i < target.getComponentCount(); i++) {
				Component comp = target.getComponent(i);

				if (!comp.isVisible()) {
					continue;
				}

				Dimension size = preferred ? comp.getPreferredSize() : comp.getMinimumSize();

				if (firstInRow) {
					rowWidth = size.width;
					rowHeight = size.height;
					firstInRow = false;
				} else if (rowWidth + getHgap() + size.width > maxWidth) {
					totalHeight += rowHeight + getVgap();
					rowWidth = size.width;
					rowHeight = size.height;
				} else {
					rowWidth += getHgap() + size.width;
					rowHeight = Math.max(rowHeight, size.height);
				}
			}

			totalHeight += rowHeight;

			return new Dimension(maxWidth, totalHeight);
		}
	}
}
