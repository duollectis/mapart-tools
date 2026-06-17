package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Раскрывающаяся панель (аккордеон).
 *
 * При клике контентная панель раскрывается или закрывается,
 * раздвигая соседние компоненты в родительском контейнере.
 */
public class AccordionPanel extends JPanel {

	private static final int HEADER_HEIGHT = 40;
	private static final int CORNER_RADIUS = 10;
	private static final int EXPAND_DURATION_MS = 220;
	private static final int ARROW_DURATION_MS = 200;
	private final JPanel contentWrapper;
	private final JPanel contentPanel;
	private final JLabel titleLabel;
	private final JLabel subtitleLabel;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	private JPanel header;
	private JPanel headerEastPanel;

	private boolean expanded = false;
	private final AnimatedFloat hoverProgress = new AnimatedFloat(0f);
	private final AnimatedFloat arrowProgress = new AnimatedFloat(0f);
	private final AnimatedFloat expandProgress = new AnimatedFloat(0f);
	private int cachedContentHeight = 0;

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

	// ── Анимация раскрытия ─────────────────────────────────────────────────────

	private void animateExpand() {
		contentWrapper.setVisible(true);

		int targetHeight = resolveFullContentHeight();
		cachedContentHeight = targetHeight;

		expandProgress.animateTo(1f, EXPAND_DURATION_MS, progress -> {
			int height = (int) (UiAnimator.easeOutCubic(progress) * targetHeight);
			contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, height));
			revalidate();
			repaint();
		}, () -> {
			contentWrapper.setPreferredSize(null);
			revalidate();
			repaint();
		});
	}

	private void animateCollapse() {
		int startHeight = cachedContentHeight > 0
			? cachedContentHeight
			: contentWrapper.getHeight();

		expandProgress.animateTo(0f, EXPAND_DURATION_MS, progress -> {
			int height = (int) (UiAnimator.easeOutCubic(progress) * startHeight);
			contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, height));
			revalidate();
			repaint();
		}, () -> {
			contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, 0));
			contentWrapper.setVisible(false);
			revalidate();
			repaint();
		});
	}

	private int resolveFullContentHeight() {
		int width = resolveContentWidth();

		if (width > 0) {
			contentPanel.setSize(width, Short.MAX_VALUE);
			contentPanel.revalidate();
		}

		Insets insets = contentWrapper.getInsets();
		return contentPanel.getPreferredSize().height + insets.top + insets.bottom;
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int arc = CORNER_RADIUS * 2;
		int totalHeight = getHeight();
		int width = getWidth();

		g2.setColor(GuiApp.theme.getBgCard());
		g2.fillRoundRect(0, 0, width, totalHeight, arc, arc);

		float hover = hoverProgress.get();
		if (hover > 0f) {
			Graphics2D g2h = (Graphics2D) g2.create();
			g2h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2h.clip(new Rectangle(0, 0, width, HEADER_HEIGHT));
			Color hoverColor = UiAnimator.lerp(GuiApp.theme.getBgCard(), GuiApp.theme.getBtnHoverBg(), hover * 0.5f);
			g2h.setColor(hoverColor);
			g2h.fillRoundRect(0, 0, width, totalHeight, arc, arc);
			g2h.dispose();
		}

		g2.setColor(GuiApp.theme.getBorder());
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, width - 1, totalHeight - 1, arc, arc);

		g2.dispose();
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
	 * Вставляет компонент в правую часть шапки аккордеона.
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
		contentWrapper.revalidate();
		revalidate();
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void expandInstant() {
		expanded = true;
		arrowProgress.set(1f);
		expandProgress.set(1f);
		contentWrapper.setVisible(true);
		revalidate();
		doLayout();

		contentPanel.setSize(resolveContentWidth(), Short.MAX_VALUE);
		contentPanel.revalidate();

		contentWrapper.setPreferredSize(null);
		revalidate();
		repaint();
	}

	public void collapse() {
		expanded = false;
		arrowProgress.set(0f);
		expandProgress.set(0f);
		contentWrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, 0));
		contentWrapper.setVisible(false);
		revalidate();
		repaint();
	}

	public void collapseAnimated() {
		if (!expanded) {
			return;
		}

		expanded = false;
		pcs.firePropertyChange("expanded", true, false);
		arrowProgress.animateTo(0f, ARROW_DURATION_MS, v -> header.repaint());
		animateCollapse();
	}

	public void addAccordionListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener("expanded", listener);
	}

	private void toggle() {
		if (expanded) {
			expanded = false;
			pcs.firePropertyChange("expanded", true, false);
			arrowProgress.animateTo(0f, ARROW_DURATION_MS, v -> header.repaint());
			animateCollapse();
		} else {
			expanded = true;
			pcs.firePropertyChange("expanded", false, true);
			arrowProgress.animateTo(1f, ARROW_DURATION_MS, v -> header.repaint());
			animateExpand();
		}
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
		JPanel header = new JPanel(new BorderLayout(8, 0));

		headerEastPanel = new JPanel();
		headerEastPanel.setLayout(new BoxLayout(headerEastPanel, BoxLayout.X_AXIS));
		headerEastPanel.setOpaque(false);
		headerEastPanel.add(buildArrowIcon());

		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
		header.setPreferredSize(new Dimension(Short.MAX_VALUE, HEADER_HEIGHT));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_HEIGHT));
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.add(buildTitleRow(), BorderLayout.CENTER);
		header.add(headerEastPanel, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (header.contains(e.getPoint())) {
					toggle();
				}
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
		UiAnimator.applyHandCursor(header);

		return header;
	}

	private void animateHover(JPanel header, boolean entering) {
		hoverProgress.animateTo(entering ? 1f : 0f, 150, v -> header.repaint());
	}

	/**
	 * Создаёт иконку-стрелку на базе SVG {@code chevron_right.svg}.
	 * Угол поворота управляется через {@link #arrowProgress}:
	 * 0 = смотрит вправо (закрыто), 1 = смотрит вниз (открыто).
	 * SVG рендерится в {@link Icon} и вращается через {@code g2.rotate()} на каждом кадре.
	 */
	private JComponent buildArrowIcon() {
		return new JComponent() {
			private static final int SIZE = 16;

			{
				setOpaque(false);
				setPreferredSize(new Dimension(SIZE, SIZE));
				setMinimumSize(new Dimension(SIZE, SIZE));
				setMaximumSize(new Dimension(SIZE, SIZE));
				UpdatableRegistry.onThemeAnimFrame(this::repaint);
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				// 0° = вправо (закрыто), 90° = вниз (открыто)
				double angle = arrowProgress.get() * 90.0;
				int cx = getWidth() / 2;
				int cy = getHeight() / 2;

				g2.translate(cx, cy);
				g2.rotate(Math.toRadians(angle));
				g2.translate(-cx, -cy);

				Icon icon = AppIcon.CHEVRON_RIGHT.colored(SIZE, GuiApp.theme.getTextDim());
				icon.paintIcon(this, g2, 0, 0);

				g2.dispose();
			}
		};
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
				Dimension fixed = super.isPreferredSizeSet() ? super.getPreferredSize() : null;

				if (fixed != null) {
					return fixed;
				}

				Insets ins = getInsets();
				int wrapperWidth = getWidth();

				if (wrapperWidth > ins.left + ins.right) {
					content.setSize(wrapperWidth - ins.left - ins.right, content.getHeight());
				}

				int contentHeight = content.getPreferredSize().height;
				return new Dimension(Short.MAX_VALUE, contentHeight + ins.top + ins.bottom);
			}

		};

		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.add(content);

		return wrapper;
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
