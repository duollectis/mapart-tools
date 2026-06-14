package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.AnimatedFloat;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Панель вертикального списка элементов для передачи в {@link AccordionPanel}.
 *
 * <p>Визуально — плоские кнопки-строки в тёмной теме с ховером и акцентом на выбранном.
 * Поддерживает разделители-заголовки групп ({@link Separator}).
 * Если суммарная высота строк превышает {@code MAX_VISIBLE_HEIGHT}, список
 * автоматически ограничивается по высоте и становится прокручиваемым.
 *
 * @param <T> тип элементов списка
 */
public class SelectionPanel<T> extends JPanel {

	private static final int ITEM_HEIGHT = 32;
	private static final int SEPARATOR_HEIGHT = 24;
	private static final int CORNER_RADIUS = 6;
	private static final int MAX_VISIBLE_HEIGHT = 280;
	private static final int ACTION_BTN_SIZE = 22;
	private static final int ACTION_BTN_GAP = 2;

	private final List<Object> items;
	private final JPanel rowsPanel;
	private final InertialScrollPane scroll;
	private final List<Consumer<T>> selectionListeners = new ArrayList<>();

	private Function<Object, String> displayConverter = Object::toString;
	private Function<T, List<RowAction>> rowActionProvider;
	private T selectedItem;

	public SelectionPanel(List<Object> items) {
		this.items = new ArrayList<>(items);
		rowsPanel = buildRowsPanel();
		scroll = buildScroll();
		initLayout();
		rebuildRows();
	}

	public SelectionPanel(T[] items) {
		this.items = new ArrayList<>(List.of(items));
		rowsPanel = buildRowsPanel();
		scroll = buildScroll();
		initLayout();
		rebuildRows();
	}

	@Override
	public Dimension getPreferredSize() {
		int rowsHeight = rowsPanel.getPreferredSize().height;
		int height = Math.min(rowsHeight, MAX_VISIBLE_HEIGHT);
		return new Dimension(Short.MAX_VALUE, height);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	// ── Public API ─────────────────────────────────────────────────────────────

	public void setDisplayConverter(Function<Object, String> converter) {
		displayConverter = converter;
		rebuildRows();
	}

	/**
	 * Устанавливает провайдер действий для строк списка.
	 * Кнопки действий (edit, delete и т.д.) появляются справа при наведении курсора на строку.
	 * Провайдер вызывается для каждой строки при построении — возвращает список действий или null.
	 */
	@SuppressWarnings("unchecked")
	public void setRowActionProvider(Function<T, List<RowAction>> provider) {
		rowActionProvider = provider;
		rebuildRows();
	}

	public void setItems(List<Object> newItems) {
		items.clear();
		items.addAll(newItems);
		rebuildRows();
	}

	public void addSelectionListener(Consumer<T> listener) {
		selectionListeners.add(listener);
	}

	/**
	 * Регистрирует listener и немедленно вызывает его с текущим выбранным элементом,
	 * если он уже установлен. Используется для синхронизации субтитров аккордеона при старте.
	 */
	public void addInitializedSelectionListener(Consumer<T> listener) {
		selectionListeners.add(listener);

		if (selectedItem != null) {
			listener.accept(selectedItem);
		}
	}

	@SuppressWarnings("unchecked")
	public T getSelectedItem() {
		return selectedItem;
	}

	public String getDisplayText(Object item) {
		return displayConverter.apply(item);
	}

	@SuppressWarnings("unchecked")
	public void setSelectedItem(Object item) {
		if (item == null) {
			return;
		}

		for (Object candidate : items) {
			if (candidate instanceof Separator) {
				continue;
			}

			if (candidate.equals(item)) {
				selectedItem = (T) candidate;
				repaintRows();
				notifyListeners();
				return;
			}
		}
	}

	/**
	 * Устанавливает выбранный элемент без уведомления слушателей.
	 * Используется для восстановления сохранённого состояния при старте,
	 * когда язык уже загружен через {@code UpdatableRegistry.load()} и
	 * повторный вызов слушателей не нужен.
	 */
	@SuppressWarnings("unchecked")
	public void setSelectedItemSilently(Object item) {
		if (item == null) {
			return;
		}

		for (Object candidate : items) {
			if (candidate instanceof Separator) {
				continue;
			}

			if (candidate.equals(item)) {
				selectedItem = (T) candidate;
				repaintRows();
				return;
			}
		}
	}

	// ── Инициализация ──────────────────────────────────────────────────────────

	private JPanel buildRowsPanel() {
		JPanel panel = new JPanel(new AccordionPanel.FullWidthLayout());
		panel.setOpaque(false);
		return panel;
	}

	private InertialScrollPane buildScroll() {
		InertialScrollPane pane = new InertialScrollPane(rowsPanel);
		pane.setOpaque(false);
		pane.getViewport().setOpaque(false);
		return pane;
	}

	private void initLayout() {
		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		add(scroll, BorderLayout.CENTER);
	}

	// ── Построение строк ───────────────────────────────────────────────────────

	private void rebuildRows() {
		rowsPanel.removeAll();

		for (Object item : items) {
			if (item instanceof Separator sep) {
				rowsPanel.add(buildSeparatorRow(sep));
			} else {
				rowsPanel.add(buildItemRow(item));
			}
		}

		if (selectedItem == null) {
			selectFirstNonSeparator();
		}

		rowsPanel.revalidate();
		rowsPanel.repaint();
		revalidate();
		repaint();
	}

	private void repaintRows() {
		for (Component comp : rowsPanel.getComponents()) {
			if (comp instanceof JPanel rowPanel) {
				Object item = rowPanel.getClientProperty("item");
				Object animObj = rowPanel.getClientProperty("selectionProgress");

				if (animObj instanceof AnimatedFloat anim && item != null) {
					float target = item.equals(selectedItem) ? 1f : 0f;
					anim.animateTo(target, 200, v -> comp.repaint());
				}
			}

			comp.repaint();
		}
	}

	private void notifyListeners() {
		for (Consumer<T> listener : selectionListeners) {
			listener.accept(selectedItem);
		}
	}

	@SuppressWarnings("unchecked")
	private void selectFirstNonSeparator() {
		for (Object item : items) {
			if (item instanceof Separator) {
				continue;
			}

			selectedItem = (T) item;
			return;
		}
	}

	private JComponent buildSeparatorRow(Separator sep) {
		JLabel label = new JLabel(UpdatableRegistry.translate(sep.langKey()).toUpperCase());
		label.setFont(new Font("SansSerif", Font.BOLD, 10));
		label.setForeground(GuiApp.theme.getTextDim());
		label.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, SEPARATOR_HEIGHT));

		UpdatableRegistry.onThemeAnimFrame(() -> label.setForeground(GuiApp.theme.getTextDim()));
		UpdatableRegistry.registerLang(sep.langKey(), t -> label.setText(t.toUpperCase()));

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, SEPARATOR_HEIGHT));
		wrapper.setPreferredSize(new Dimension(Short.MAX_VALUE, SEPARATOR_HEIGHT));

		JPanel line = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new BasicStroke(1f));
				g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
				g2.dispose();
			}
		};
		line.setOpaque(false);
		line.setPreferredSize(new Dimension(0, 1));

		wrapper.add(label, BorderLayout.WEST);
		wrapper.add(line, BorderLayout.CENTER);

		return wrapper;
	}

	@SuppressWarnings("unchecked")
	private JComponent buildItemRow(Object item) {
		AnimatedFloat hoverProgress = new AnimatedFloat(0f);
		AnimatedFloat selectionProgress = new AnimatedFloat(item.equals(selectedItem) ? 1f : 0f);
		String[] labelText = {displayConverter.apply(item)};

		List<RowAction> actions = rowActionProvider == null
				? List.of()
				: rowActionProvider.apply((T) item);

		JPanel actionsPanel = buildActionsPanel(actions, hoverProgress);

		JPanel row = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

				float selProgress = selectionProgress.get();
				float hovProgress = hoverProgress.get();

				if (selProgress > 0f) {
					Color accent = GuiApp.theme.getAccent();
					g2.setColor(new Color(
						accent.getRed(),
						accent.getGreen(),
						accent.getBlue(),
						Math.round(40 * selProgress)
					));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS * 2, CORNER_RADIUS * 2);
				}

				if (hovProgress > 0f && selProgress < 1f) {
					Color overlay = GuiApp.theme.getHoverBgOverlay();
					g2.setColor(new Color(
						overlay.getRed(),
						overlay.getGreen(),
						overlay.getBlue(),
						Math.round(overlay.getAlpha() * hovProgress * (1f - selProgress))
					));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS * 2, CORNER_RADIUS * 2);
				}

				Color textColor = UiAnimator.lerp(GuiApp.theme.getText(), GuiApp.theme.getAccent(), selProgress);
				g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
				g2.setColor(textColor);

				FontMetrics fm = g2.getFontMetrics();
				int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
				g2.drawString(labelText[0], 6, textY);

				g2.dispose();
			}
		};

		row.setOpaque(false);
		row.setBorder(null);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
		row.setPreferredSize(new Dimension(Short.MAX_VALUE, ITEM_HEIGHT));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		if (actionsPanel != null) {
			row.add(actionsPanel, BorderLayout.EAST);
		}

		UpdatableRegistry.onLangChanged(() -> {
			labelText[0] = displayConverter.apply(item);
			row.repaint();
		});

		row.putClientProperty("item", item);
		row.putClientProperty("selectionProgress", selectionProgress);

		row.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				T prev = selectedItem;
				selectedItem = (T) item;
				repaintRows();

				if (!item.equals(prev)) {
					notifyListeners();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				hoverProgress.animateTo(1f, 150, v -> row.repaint());

				if (item instanceof HasDescription described) {
					String desc = described.getDescription();
					if (desc != null && !desc.isBlank()) {
						AppTooltip.show(row, desc);
					}
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hoverProgress.animateTo(0f, 150, v -> row.repaint());
				AppTooltip.hide();
			}
		});

		scroll.attachWheelListenerTo(row);

		return row;
	}

	/**
	 * Строит панель с кнопками действий для строки.
	 * Кнопки прозрачны при отсутствии hover и плавно появляются при наведении.
	 * Клики на кнопки не вызывают выбор строки.
	 */
	private JPanel buildActionsPanel(List<RowAction> actions, AnimatedFloat hoverProgress) {
		if (actions.isEmpty()) {
			return null;
		}

		int panelWidth = actions.size() * (ACTION_BTN_SIZE + ACTION_BTN_GAP) + ACTION_BTN_GAP;

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, ACTION_BTN_GAP, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				// Прозрачный фон — рисуем только дочерние кнопки
			}
		};
		panel.setOpaque(false);
		panel.setPreferredSize(new Dimension(panelWidth, ITEM_HEIGHT));

		for (RowAction action : actions) {
			JButton btn = buildActionButton(action, hoverProgress);
			panel.add(btn);
		}

		return panel;
	}

	private JButton buildActionButton(RowAction action, AnimatedFloat hoverProgress) {
		JButton btn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				float alpha = hoverProgress.get();

				if (alpha <= 0f) {
					return;
				}

				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

				Object hoverObj = getClientProperty("btnHover");
				float btnHover = hoverObj instanceof AnimatedFloat af ? af.get() : 0f;

				if (btnHover > 0f) {
					Color overlay = GuiApp.theme.getHoverBgOverlay();
					g2.setColor(new Color(
						overlay.getRed(),
						overlay.getGreen(),
						overlay.getBlue(),
						Math.round(overlay.getAlpha() * btnHover)
					));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				Color iconColor = UiAnimator.lerp(GuiApp.theme.getTextDim(), GuiApp.theme.getText(), btnHover);
				Icon icon = action.icon().colored(ACTION_BTN_SIZE - 6, iconColor);
				int iconX = (getWidth() - icon.getIconWidth()) / 2;
				int iconY = (getHeight() - icon.getIconHeight()) / 2;
				icon.paintIcon(this, g2, iconX, iconY);

				g2.dispose();
			}
		};

		AnimatedFloat btnHover = new AnimatedFloat(0f);
		btn.putClientProperty("btnHover", btnHover);

		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setPreferredSize(new Dimension(ACTION_BTN_SIZE, ACTION_BTN_SIZE));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				btnHover.animateTo(1f, 120, v -> btn.repaint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				btnHover.animateTo(0f, 120, v -> btn.repaint());
			}
		});

		btn.addActionListener(e -> action.handler().run());

		UpdatableRegistry.onThemeAnimFrame(() -> btn.repaint());

		return btn;
	}

	// ── Вложенные типы ─────────────────────────────────────────────────────────

	/** Разделитель-заголовок группы элементов в списке. Хранит ключ перевода. */
	public record Separator(String langKey) {}

	/** Действие для строки списка: иконка и обработчик клика. */
	public record RowAction(AppIcon icon, Runnable handler) {}
}
