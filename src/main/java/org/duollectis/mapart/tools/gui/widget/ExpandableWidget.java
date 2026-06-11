package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Базовый класс для виджетов с анимированным раскрытием вниз прямо в потоке layout.
 * Содержит общую логику: hover-анимацию, open/close-анимацию, рисование рамки и стрелки.
 * <p>
 * Подклассы обязаны реализовать:
 * <ul>
 *   <li>{@link #getHeaderLabel()} — текст заголовка кнопки</li>
 *   <li>{@link #getExpandedContent()} — компонент, который раскрывается внутри</li>
 *   <li>{@link #computeMaxContentHeight()} — вычислить максимальную высоту раскрытого контента</li>
 *   <li>{@link #onHeaderClick()} — реакция на клик по заголовку (обычно вызов {@link #toggle()})</li>
 * </ul>
 */
public abstract class ExpandableWidget extends JPanel {

	protected static final int BUTTON_HEIGHT = 32;
	protected static final int ARC = 8;
	protected static final int OPEN_DURATION_MS = 200;

	private float hoverProgress = 0f;
	private Timer hoverTimer;

	private float openProgress = 0f;
	private Timer openTimer;
	private boolean isOpen = false;

	protected int currentContentH = 0;
	protected int maxContentH = 0;

	protected ExpandableWidget() {
		setLayout(null);
		setOpaque(false);
		setPreferredSize(new Dimension(0, BUTTON_HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				updateCursor(e.getY());

				if (e.getY() < BUTTON_HEIGHT) {
					animateHover(true);
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				setCursor(Cursor.getDefaultCursor());
				animateHover(false);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1 && e.getY() < BUTTON_HEIGHT) {
					onHeaderClick();
				}
			}
		});

		addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override
			public void mouseMoved(java.awt.event.MouseEvent e) {
				updateCursor(e.getY());
			}
		});
	}

	private void updateCursor(int mouseY) {
		setCursor(
			mouseY < BUTTON_HEIGHT
				? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				: Cursor.getDefaultCursor()
		);
	}

	// ── Абстрактный контракт ───────────────────────────────────────────────────

	/** Текст заголовка кнопки. */
	protected abstract String getHeaderLabel();

	/**
	 * Необязательный dim-префикс перед основным текстом заголовка.
	 * Если не {@code null}, рендерится как {@code "Префикс  ·  Значение"}.
	 */
	protected String getHeaderPrefix() {
		return null;
	}

	/** Компонент, раскрывающийся под кнопкой. */
	protected abstract Component getExpandedContent();

	/** Вычислить максимальную высоту раскрытого контента в пикселях. */
	protected abstract int computeMaxContentHeight();

	/** Вызывается при клике по заголовку. Обычно просто вызывает {@link #toggle()}. */
	protected abstract void onHeaderClick();

	// ── Публичный API ──────────────────────────────────────────────────────────

	public boolean isExpanded() {
		return isOpen;
	}

	public void expand() {
		if (isOpen) {
			return;
		}

		open();
	}

	public void collapse() {
		if (!isOpen) {
			return;
		}

		close();
	}

	// ── Отрисовка ─────────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Color bg = UiAnimator.lerp(
			GuiApp.theme.getBgInput(),
			GuiApp.theme.getBgCard(),
			Math.max(hoverProgress, openProgress)
		);

		boolean hasContent = currentContentH > 0;

		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), BUTTON_HEIGHT, ARC, ARC);

		if (hasContent) {
			int contentBottom = BUTTON_HEIGHT + currentContentH;
			g2.fillRect(0, BUTTON_HEIGHT - ARC, getWidth(), ARC);
			g2.fillRect(0, BUTTON_HEIGHT, getWidth(), Math.max(0, currentContentH - ARC));
			g2.fillRoundRect(0, Math.max(BUTTON_HEIGHT, contentBottom - ARC * 2), getWidth(), ARC * 2, ARC, ARC);
		}

		Font headerFont = new Font("SansSerif", Font.PLAIN, 13);
		g2.setFont(headerFont);

		int textY = (BUTTON_HEIGHT + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
		String prefix = getHeaderPrefix();

		if (prefix == null) {
			g2.setColor(GuiApp.theme.getText());
			g2.drawString(getHeaderLabel(), 10, textY);
		} else {
			String separator = "  ·  ";
			int prefixW = g2.getFontMetrics().stringWidth(prefix);
			int separatorW = g2.getFontMetrics().stringWidth(separator);

			g2.setColor(GuiApp.theme.getTextDim());
			g2.drawString(prefix, 10, textY);

			g2.drawString(separator, 10 + prefixW, textY);

			g2.setColor(GuiApp.theme.getText());
			g2.drawString(getHeaderLabel(), 10 + prefixW + separatorW, textY);
		}

		drawArrow(g2);
		g2.dispose();
	}

	@Override
	protected void paintChildren(Graphics g) {
		super.paintChildren(g);

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Color border = UiAnimator.lerp(
			GuiApp.theme.getBorder(),
			GuiApp.theme.getAccentBright(),
			Math.max(hoverProgress, openProgress)
		);
		boolean hasContent = currentContentH > 0;

		g2.setColor(border);
		g2.setStroke(new BasicStroke(1f));

		if (hasContent) {
			int totalH = BUTTON_HEIGHT + currentContentH;
			g2.drawRoundRect(0, 0, getWidth() - 1, totalH - 1, ARC * 2, ARC * 2);
		} else {
			g2.drawRoundRect(0, 0, getWidth() - 1, BUTTON_HEIGHT - 1, ARC * 2, ARC * 2);
		}

		g2.dispose();
	}

	private void drawArrow(Graphics2D g2) {
		int cx = getWidth() - 16;
		int cy = BUTTON_HEIGHT / 2;
		int w = 8;
		int h = 5;

		double angle = Math.PI * openProgress;
		int dy = (int) (Math.cos(angle) * h / 2);

		g2.setColor(GuiApp.theme.getTextDim());
		g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.drawLine(cx - w / 2, cy - dy, cx, cy + dy);
		g2.drawLine(cx, cy + dy, cx + w / 2, cy - dy);
	}

	// ── Анимация hover ─────────────────────────────────────────────────────────

	private void animateHover(boolean toHovered) {
		if (hoverTimer != null) {
			hoverTimer.stop();
		}

		float from = hoverProgress;
		float to = toHovered ? 1f : 0f;

		hoverTimer = UiAnimator.animateFloat(from, to, 150, progress -> {
			hoverProgress = progress;
			repaint();
		}, null);
	}

	// ── Открытие / закрытие ────────────────────────────────────────────────────

	protected void toggle() {
		if (isOpen) {
			close();
		} else {
			open();
		}
	}

	private void open() {
		isOpen = true;

		Component content = getExpandedContent();
		content.setVisible(true);

		if (content instanceof JPanel panel) {
			panel.validate();
		}

		maxContentH = computeMaxContentHeight();
		animateOpen(true);
	}

	private void close() {
		isOpen = false;
		animateOpen(false);
	}

	protected void animateOpen(boolean opening) {
		if (openTimer != null) {
			openTimer.stop();
		}

		float from = openProgress;
		float to = opening ? 1f : 0f;

		openTimer = UiAnimator.animateFloat(from, to, OPEN_DURATION_MS, progress -> {
			openProgress = UiAnimator.easeOutCubic(progress);
			currentContentH = (int) (maxContentH * openProgress);

			int totalH = BUTTON_HEIGHT + currentContentH;
			setPreferredSize(new Dimension(0, totalH));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH));

			layoutContent();

			if (getParent() != null) {
				getParent().revalidate();
			}

			repaint();
		}, () -> {
			openProgress = to;

			if (!opening) {
				getExpandedContent().setVisible(false);
				currentContentH = 0;
				setPreferredSize(new Dimension(0, BUTTON_HEIGHT));
				setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));

				if (getParent() != null) {
					getParent().revalidate();
				}
			}

			repaint();
		});
	}

	/**
	 * Вызывается подклассом, когда содержимое изменило предпочтительный размер
	 * (например, вложенный DropDown раскрылся). Мгновенно подстраивает высоту аккордиона
	 * под новый размер дочернего виджета — без собственной анимации, чтобы не конкурировать
	 * с анимацией дочернего DropDown и не вызывать прыжок элементов.
	 */
	protected final void onContentResized() {
		if (!isOpen) {
			return;
		}

		int newMax = computeMaxContentHeight();

		if (newMax == maxContentH) {
			return;
		}

		maxContentH = newMax;
		currentContentH = (int) (maxContentH * openProgress);

		int totalH = BUTTON_HEIGHT + currentContentH;
		setPreferredSize(new Dimension(0, totalH));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH));

		layoutContent();

		if (getParent() != null) {
			getParent().revalidate();
		}

		repaint();
	}

	/**
	 * Позиционирует дочерний контент внутри виджета.
	 * Вызывается при каждом шаге анимации и из {@link #doLayout()}.
	 */
	protected void layoutContent() {
		Component content = getExpandedContent();
		content.setBounds(1, BUTTON_HEIGHT, getWidth() - 2, Math.max(0, currentContentH - 1));

		if (content instanceof JPanel panel) {
			panel.validate();
		}
	}

	@Override
	public void doLayout() {
		layoutContent();
	}
}
