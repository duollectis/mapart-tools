package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Раскрывающаяся панель (аккордеон) с плавной анимацией через {@link Timer}.
 *
 * <p>Шапка — кликабельная область с заголовком и стрелкой-индикатором.
 * При клике контентная панель плавно раскрывается или закрывается,
 * раздвигая соседние компоненты в родительском контейнере.
 */
public class AccordionPanel extends JPanel {

	private static final int ANIM_DURATION_MS = 2200;
	private static final int HEADER_HEIGHT = 40;
	private static final int CORNER_RADIUS = 10;
	private static final int ARROW_SIZE = 6;
	private final JPanel contentWrapper;
	private final JPanel contentPanel;
	private final JLabel titleLabel;
	private final JLabel subtitleLabel;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	private JPanel header;
	private JPanel headerEastPanel;
	private JLabel arrowLabel;

	private boolean expanded = false;
	private float arrowProgress = 0f;
	private final AnimatedFloat hoverProgress = new AnimatedFloat(0f);
	private int animTargetHeight;
	private Timer activeAnimation;

	public AccordionPanel(String title, JPanel content) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		titleLabel = buildTitleLabel(title);
		subtitleLabel = buildSubtitleLabel();
		header = buildHeader();

		contentPanel = content;
		contentWrapper = buildContentWrapper(content);

		add(header);
		add(contentWrapper);

		contentWrapper.setVisible(false);
		contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, 0));

		UpdatableRegistry.onThemeChanged(this::repaint);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	// ── Public API ─────────────────────────────────────────────────────────────

	public void setTitle(String title) {
		titleLabel.setText(title);
	}

	public void setSubtitle(String subtitle) {
		boolean hasText = subtitle != null && !subtitle.isBlank();
		subtitleLabel.setText(hasText ? subtitle : "");
		subtitleLabel.setVisible(hasText);
	}

	/**
	 * Вставляет компонент в правую часть шапки аккордеона — между субтитром и стрелкой.
	 * Используется для добавления кнопки «+» создания новой темы прямо в заголовок.
	 * Клики на вставленный компонент не вызывают toggle аккордеона.
	 */
	public void setHeaderTrailingComponent(JComponent component) {
		headerEastPanel.add(component, 0);
		headerEastPanel.revalidate();
		headerEastPanel.repaint();
	}

	/**
	 * Пересчитывает реальную высоту контента и обновляет preferredSize обёртки.
	 * Вызывается при изменении видимости дочерних компонентов или ширины панели.
	 *
	 * <p>После завершения анимации сбрасывает фиксированный preferredSize обёртки,
	 * чтобы кастомный {@code getPreferredSize()} снова вычислял высоту динамически.
	 */
	public void refreshContentSize() {
		if (!expanded) {
			return;
		}

		int contentWidth = resolveContentWidth();

		if (contentWidth <= 0) {
			return;
		}

		contentPanel.setSize(contentWidth, Short.MAX_VALUE);
		contentPanel.revalidate();

		// Сбрасываем фиксированный размер — wrapper.getPreferredSize() теперь динамический
		contentWrapper.setPreferredSize(null);
		animTargetHeight = contentWrapper.getPreferredSize().height;
		contentWrapper.revalidate();
		revalidate();
	}

	/**
	 * Плавно анимирует высоту контентной обёртки от текущего значения до нового,
	 * пересчитанного после изменения видимости дочерних компонентов.
	 * Используется для анимированного появления/скрытия строк внутри контента.
	 */
	public boolean isExpanded() {
		return expanded;
	}

	public void expandInstant() {
		stopAnimation();
		expanded = true;
		arrowProgress = 1f;
		contentWrapper.setVisible(true);
		revalidate();
		doLayout();

		contentPanel.setSize(resolveContentWidth(), Short.MAX_VALUE);
		contentPanel.revalidate();

		contentWrapper.setPreferredSize(null);
		animTargetHeight = contentWrapper.getPreferredSize().height;
		revalidate();
		repaint();
		SwingUtilities.invokeLater(this::scrollIntoView);
	}

	public void collapse() {
		stopAnimation();
		expanded = false;
		arrowProgress = 0f;
		animTargetHeight = 0;
		contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, 0));
		contentWrapper.setVisible(false);
		revalidate();
		repaint();
	}

	public void collapseAnimated() {
		if (expanded) {
			animateCollapse();
		}
	}

	public void addAccordionListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener("expanded", listener);
	}

	// ── Построение компонентов ─────────────────────────────────────────────────

	private JLabel buildTitleLabel(String title) {
		JLabel label = new JLabel(title) {
			private static final int FONT_SIZE_BASE = 13;
			private static final int FONT_SIZE_MIN = 9;

			@Override
			public Dimension getPreferredSize() {
				FontMetrics fm = getFontMetrics(getFont());
				String text = getText();
				int textWidth = text == null ? 0 : fm.stringWidth(text);
				return new Dimension(textWidth, HEADER_HEIGHT);
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(0, HEADER_HEIGHT);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, HEADER_HEIGHT);
			}

			@Override
			protected void paintComponent(Graphics g) {
				String text = getText();

				if (text == null || text.isBlank()) {
					return;
				}

				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(getForeground());

				Font fitted = fitFont(g2, text);
				g2.setFont(fitted);

				FontMetrics fm = g2.getFontMetrics();
				int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
				g2.drawString(text, 0, y);
				g2.dispose();
			}

			private Font fitFont(Graphics2D g2, String text) {
				for (int size = FONT_SIZE_BASE; size >= FONT_SIZE_MIN; size--) {
					Font candidate = new Font("SansSerif", Font.BOLD, size);
					FontMetrics fm = g2.getFontMetrics(candidate);

					if (fm.stringWidth(text) <= getWidth()) {
						return candidate;
					}
				}

				return new Font("SansSerif", Font.BOLD, FONT_SIZE_MIN);
			}
		};

		label.setFont(new Font("SansSerif", Font.BOLD, 13));
		label.setForeground(GuiApp.theme.getText());
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(GuiApp.theme.getText()));
		return label;
	}

	private JLabel buildSubtitleLabel() {
		JLabel label = new JLabel();
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		label.setForeground(GuiApp.theme.getTextDim());
		label.setVisible(false);
		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(GuiApp.theme.getTextDim()));
		return label;
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color base = GuiApp.theme.getBgCard();
				Color bg = UiAnimator.lerp(base, GuiApp.theme.getBtnHoverBg(), hoverProgress.get() * 0.5f);

				g2.setColor(bg);
				int arc = CORNER_RADIUS * 2;

				boolean hasContent = contentWrapper.isVisible()
					&& contentWrapper.getPreferredSize().height > 1;

				if (hasContent) {
					g2.fillRoundRect(0, 0, getWidth(), getHeight() + CORNER_RADIUS, arc, arc);
				} else {
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
				}

				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));

				if (hasContent) {
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() + CORNER_RADIUS, arc, arc);
				} else {
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
				}

				g2.dispose();
			}
		};

		headerEastPanel = new JPanel();
		headerEastPanel.setLayout(new BoxLayout(headerEastPanel, BoxLayout.X_AXIS));
		headerEastPanel.setOpaque(false);
		headerEastPanel.add(buildArrowLabel());

		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
		header.setPreferredSize(new Dimension(Short.MAX_VALUE, HEADER_HEIGHT));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_HEIGHT));
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.add(buildTitleRow(), BorderLayout.CENTER);
		header.add(headerEastPanel, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggle();
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				animateHover(header, true);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				animateHover(header, false);
			}
		});

		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return header;
	}

	private JPanel buildTitleRow() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.add(titleLabel);
		row.add(Box.createHorizontalStrut(6));
		row.add(subtitleLabel);

		return row;
	}

	private void animateHover(JPanel header, boolean entering) {
		hoverProgress.animateTo(entering ? 1f : 0f, 150, v -> header.repaint());
	}

	private JLabel buildArrowLabel() {
		arrowLabel = new JLabel() {
			@Override
			public float getAlignmentY() {
				return 0.5f;
			}

			@Override
			public Dimension getPreferredSize() {
				return new Dimension(20, HEADER_HEIGHT);
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(20, HEADER_HEIGHT);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(20, HEADER_HEIGHT);
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getTextDim());
				g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

				int cx = getWidth() / 2;
				int cy = HEADER_HEIGHT / 2;

				double angle = -arrowProgress * Math.PI;
				g2.translate(cx, cy);
				g2.rotate(angle);
				g2.drawLine(-ARROW_SIZE, -ARROW_SIZE / 2, 0, ARROW_SIZE / 2);
				g2.drawLine(0, ARROW_SIZE / 2, ARROW_SIZE, -ARROW_SIZE / 2);

				g2.dispose();
			}
		};

		return arrowLabel;
	}

	/**
	 * Создаёт обёртку контента с кастомным layout-менеджером.
	 *
	 * <p>Использует {@code null} layout с переопределённым {@code doLayout()},
	 * который явно устанавливает ширину контента равной доступной ширине обёртки.
	 * Это гарантирует корректное заполнение ширины независимо от состояния
	 * layout-прохода Swing и вложенности аккордеонов.
	 */
	private JPanel buildContentWrapper(JPanel content) {
		JPanel wrapper = new JPanel(null) {
			@Override
			public void doLayout() {
				Insets ins = getInsets();
				int availableWidth = getWidth() - ins.left - ins.right;

				if (availableWidth <= 0) {
					return;
				}

				content.setSize(availableWidth, content.getHeight());
				content.doLayout();
				content.setBounds(ins.left, ins.top, availableWidth, content.getPreferredSize().height);
			}

			@Override
			public Dimension getPreferredSize() {
				// Если установлен фиксированный размер (во время анимации) — используем его
				Dimension fixed = super.isPreferredSizeSet() ? super.getPreferredSize() : null;

				if (fixed != null) {
					return fixed;
				}

				Insets ins = getInsets();
				// Передаём контенту текущую ширину для корректного вычисления высоты
				int wrapperWidth = getWidth();

				if (wrapperWidth > ins.left + ins.right) {
					content.setSize(wrapperWidth - ins.left - ins.right, content.getHeight());
				}

				int contentHeight = content.getPreferredSize().height;
				return new Dimension(Short.MAX_VALUE, contentHeight + ins.top + ins.bottom);
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgCard());

				int arc = CORNER_RADIUS * 2;
				g2.fillRoundRect(0, -CORNER_RADIUS, getWidth(), getHeight() + CORNER_RADIUS, arc, arc);

				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, -CORNER_RADIUS, getWidth() - 1, getHeight() + CORNER_RADIUS - 1, arc, arc);

				g2.dispose();
			}

		};

		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.add(content);

		return wrapper;
	}

	// ── Анимация ───────────────────────────────────────────────────────────────

	private void toggle() {
		if (expanded) {
			animateCollapse();
		} else {
			animateExpand();
		}
	}

	private void animateExpand() {
		stopAnimation();
		boolean wasExpanded = expanded;
		expanded = true;
		pcs.firePropertyChange("expanded", wasExpanded, true);

		// Запоминаем текущую высоту до любых изменений — при прерывании закрытия она > 0
		int startHeight = contentWrapper.isVisible()
			? contentWrapper.getPreferredSize().height
			: 0;

		contentWrapper.setVisible(true);

		contentPanel.setSize(resolveContentWidth(), Short.MAX_VALUE);
		contentPanel.revalidate();

		contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, startHeight));

		float startArrow = arrowProgress;

		activeAnimation = UiAnimator.animateProgress(
			ANIM_DURATION_MS,
			t -> {
				// Пересчитываем целевую высоту на каждом кадре — контент мог измениться
				contentWrapper.setPreferredSize(null);
				int targetHeight = contentWrapper.getPreferredSize().height;
				animTargetHeight = targetHeight;

				float eased = UiAnimator.easeOutCubic(t);

				arrowProgress = startArrow + (1f - startArrow) * eased;

				int height = Math.round(startHeight + (targetHeight - startHeight) * eased);
					contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, height));
					revalidate();
					repaintWithParent();
					scrollIntoView();
			},
			() -> {
				arrowProgress = 1f;
				contentWrapper.setPreferredSize(null);
				animTargetHeight = contentWrapper.getPreferredSize().height;
				revalidate();
				repaint();
				notifyAncestors();
				scrollIntoView();
			}
		);
	}

	private void animateCollapse() {
		stopAnimation();
		boolean wasExpanded = expanded;
		expanded = false;
		pcs.firePropertyChange("expanded", wasExpanded, false);

		int startHeight = contentWrapper.getPreferredSize().height;
		float startArrow = arrowProgress;

		activeAnimation = UiAnimator.animateProgress(
			ANIM_DURATION_MS,
			t -> {
				float eased = UiAnimator.easeOutCubic(t);

				arrowProgress = startArrow * (1f - eased);

				int height = Math.round(startHeight * (1f - eased));
				contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, height));
				revalidate();
				repaintWithParent();
			},
			() -> {
				arrowProgress = 0f;
				contentWrapper.setVisible(false);
				contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, 0));
				revalidate();
				repaintWithParent();
				notifyAncestors();
			}
		);
	}

	// Инвалидирует и себя, и родителя — чтобы очистить артефакты рисования
	// дочерних компонентов, которые могли выйти за пределы contentWrapper
	// во время анимации (AnimatedPanel рисует детей за своими bounds).
	private void repaintWithParent() {
		repaint();

		Container parent = getParent();

		if (parent != null) {
			parent.repaint();
		}
	}

	/**
	 * Поднимается вверх по иерархии компонентов и вызывает {@link #refreshContentSize()}
	 * на каждом найденном предке-аккордеоне, чтобы тот пересчитал свою высоту.
	 */
	private void notifyAncestors() {
		Container current = getParent();

		while (current != null) {
			if (current instanceof AccordionPanel ancestor) {
				ancestor.refreshContentSize();
			}

			current = current.getParent();
		}
	}

	/**
	 * Прокручивает ближайший родительский {@link JScrollPane} так, чтобы нижний край
	 * аккордеона был виден в области просмотра. Передаёт прямоугольник нижней части
	 * компонента — Swing транслирует его вверх по иерархии до {@link javax.swing.JViewport}
	 * и прокручивает ровно настолько, чтобы нижний край не выходил за viewport.
	 */
	private void scrollIntoView() {
		int h = getHeight();
		int w = getWidth();
		scrollRectToVisible(new Rectangle(0, h - 1, w, 1));
	}

	private void stopAnimation() {
		if (activeAnimation == null) {
			return;
		}

		activeAnimation.stop();
		activeAnimation = null;
	}

	/**
	 * Вычисляет доступную ширину для контентной панели через ширину самого аккордеона
	 * минус инсеты обёртки. Стабильно работает даже до завершения layout-прохода,
	 * в отличие от {@code contentWrapper.getWidth()}, который может быть устаревшим.
	 */
	private int resolveContentWidth() {
		int accordionWidth = getWidth();

		if (accordionWidth <= 0) {
			return contentWrapper.getWidth();
		}

		Insets insets = contentWrapper.getInsets();
		return accordionWidth - insets.left - insets.right;
	}

	/**
	 * Создаёт панель-контейнер для содержимого аккордеона с кастомным layout-менеджером,
	 * который растягивает все дочерние компоненты до полной ширины контейнера,
	 * игнорируя их {@code alignmentX}. Это решает проблему {@code BoxLayout(Y_AXIS)},
	 * который ограничивает ширину компонентов при смешанных значениях {@code alignmentX}.
	 */
	public static JPanel createContentPanel() {
		JPanel panel = new JPanel(new FullWidthLayout());
		panel.setOpaque(false);
		return panel;
	}

	// ── Вложенные классы ───────────────────────────────────────────────────────

	/**
	 * Layout-менеджер, который раскладывает компоненты вертикально и растягивает
	 * каждый из них до полной ширины контейнера, игнорируя {@code alignmentX}.
	 * Высота каждого компонента берётся из его {@code preferredSize}.
	 */
	public static final class FullWidthLayout implements LayoutManager {

		@Override
		public void layoutContainer(Container parent) {
			Insets ins = parent.getInsets();
			int width = parent.getWidth() - ins.left - ins.right;
			int y = ins.top;

			for (Component child : parent.getComponents()) {
				if (!child.isVisible()) {
					continue;
				}

				int height = child.getPreferredSize().height;
				child.setBounds(ins.left, y, width, height);
				y += height;
			}
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			Insets ins = parent.getInsets();
			int totalHeight = ins.top + ins.bottom;
			int maxWidth = 0;

			for (Component child : parent.getComponents()) {
				if (!child.isVisible()) {
					continue;
				}

				Dimension pref = child.getPreferredSize();
				totalHeight += pref.height;
				maxWidth = Math.max(maxWidth, pref.width);
			}

			return new Dimension(maxWidth, totalHeight);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}
	}
}
