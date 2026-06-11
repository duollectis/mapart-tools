package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Полностью кастомный выпадающий список без JComboBox.
 * При открытии компонент расширяется вниз прямо в потоке layout —
 * соседние элементы сдвигаются, как если бы кнопка стала выше.
 * Вся логика анимации и рисования унаследована от {@link ExpandableWidget}.
 *
 * @param <T> тип элементов списка
 */
public class DropDown<T> extends ExpandableWidget {

	/** Заголовок группы — нельзя выбрать, отображается как dim-разделитель. */
	public record Separator(String title) {}

	private static final int POPUP_ITEM_HEIGHT = 32;
	private static final int POPUP_MAX_ITEMS = 10;
	private static final int LIST_PADDING_V = 4;

	private final List<Object> items = new ArrayList<>();
	private final List<Consumer<T>> listeners = new ArrayList<>();

	private Object selectedItem;
	private String label;

	private InertialScrollPane listScroll;
	private ItemListPanel listPanel;

	private AWTEventListener globalClickListener;

	public DropDown(T[] initialItems) {
		Collections.addAll(items, initialItems);
		selectedItem = findFirstSelectable();
		initList();
	}

	public DropDown(List<Object> model) {
		items.addAll(model);
		selectedItem = findFirstSelectable();
		initList();
	}

	private void initList() {
		listPanel = new ItemListPanel();

		listScroll = new InertialScrollPane(listPanel);
		listScroll.setBorder(BorderFactory.createEmptyBorder());
		listScroll.setViewportBorder(BorderFactory.createEmptyBorder());
		listScroll.setOpaque(false);
		listScroll.getViewport().setOpaque(false);
		listScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
		listScroll.setBackground(new Color(0, 0, 0, 0));
		listScroll.setVisible(false);

		add(listScroll);
	}

	// ── Реализация контракта ExpandableWidget ──────────────────────────────────

	@Override
	protected String getHeaderLabel() {
		return selectedItem == null ? "" : selectedItem.toString();
	}

	@Override
	protected String getHeaderPrefix() {
		return label;
	}

	@Override
	protected Component getExpandedContent() {
		return listScroll;
	}

	@Override
	protected int computeMaxContentHeight() {
		int visibleItems = Math.min(items.size(), POPUP_MAX_ITEMS);
		return visibleItems * POPUP_ITEM_HEIGHT + LIST_PADDING_V * 2;
	}

	@Override
	protected void onHeaderClick() {
		toggle();
	}

	@Override
	protected void animateOpen(boolean opening) {
		if (opening) {
			listPanel.rebuild();
			registerGlobalClickListener();
		} else {
			unregisterGlobalClickListener();
			AppTooltip.hide();
		}

		super.animateOpen(opening);
	}

	// ── Публичный API ──────────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	public T getSelectedItem() {
		return (T) selectedItem;
	}

	public void setSelectedItem(Object item) {
		if (item instanceof Separator) {
			return;
		}

		selectedItem = item;
		repaint();
	}

	public void setLabel(String labelText) {
		label = labelText;
		repaint();
	}

	public void addActionListener(Consumer<T> listener) {
		listeners.add(listener);
	}

	public int getItemCount() {
		return items.size();
	}

	public Object getItemAt(int index) {
		return items.get(index);
	}

	// ── Глобальный слушатель кликов для закрытия при расфокусе ─────────────────

	private void registerGlobalClickListener() {
		globalClickListener = event -> {
			if (event.getID() != MouseEvent.MOUSE_PRESSED) {
				return;
			}

			MouseEvent mouseEvent = (MouseEvent) event;
			Component source = mouseEvent.getComponent();

			if (isDescendantOf(source, DropDown.this)) {
				return;
			}

			collapse();
		};

		Toolkit.getDefaultToolkit().addAWTEventListener(
			globalClickListener,
			AWTEvent.MOUSE_EVENT_MASK
		);
	}

	private void unregisterGlobalClickListener() {
		if (globalClickListener == null) {
			return;
		}

		Toolkit.getDefaultToolkit().removeAWTEventListener(globalClickListener);
		globalClickListener = null;
	}

	private static boolean isDescendantOf(Component child, Component ancestor) {
		Component current = child;

		while (current != null) {
			if (current == ancestor) {
				return true;
			}

			current = current.getParent();
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private void onItemSelected(Object item) {
		if (item instanceof Separator) {
			return;
		}

		selectedItem = item;
		repaint();
		collapse();
		notifyListeners();
	}

	@SuppressWarnings("unchecked")
	private void notifyListeners() {
		for (Consumer<T> listener : listeners) {
			listener.accept((T) selectedItem);
		}
	}

	private Object findFirstSelectable() {
		for (Object item : items) {
			if (!(item instanceof Separator)) {
				return item;
			}
		}

		return null;
	}

	// ── Панель элементов списка ────────────────────────────────────────────────

	private final class ItemListPanel extends JPanel {

		ItemListPanel() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
		}

		void rebuild() {
			removeAll();

			add(buildPaddingStrut());

			for (Object item : items) {
				Component cell = item instanceof Separator sep
					? buildSeparatorCell(sep.title())
					: buildItemCell(item);

				add(cell);
			}

			add(buildPaddingStrut());

			int totalH = items.size() * POPUP_ITEM_HEIGHT + LIST_PADDING_V * 2;
			setPreferredSize(new Dimension(0, totalH));
			setMinimumSize(new Dimension(0, totalH));
			revalidate();
		}

		private JPanel buildItemCell(Object item) {
			boolean isSelected = item.equals(selectedItem);
			float[] hoverRef = {isSelected ? 0.3f : 0f};
			Timer[] timerRef = {null};

			JPanel cell = new JPanel(new BorderLayout()) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					if (hoverRef[0] > 0f) {
						g2.setColor(UiAnimator.lerp(GuiApp.theme.getBgDeep(), GuiApp.theme.getSelectionBg(), hoverRef[0]));
						g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 6, 6);
					}

					g2.dispose();
					super.paintComponent(g);
				}
			};
			cell.setOpaque(false);
			cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			cell.setMaximumSize(new Dimension(Integer.MAX_VALUE, POPUP_ITEM_HEIGHT));
			cell.setPreferredSize(new Dimension(0, POPUP_ITEM_HEIGHT));

			JLabel label = new JLabel(item.toString());
			label.setFont(new Font("SansSerif", Font.PLAIN, 13));
			label.setForeground(isSelected ? GuiApp.theme.getText() : GuiApp.theme.getTextDim());
			label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
			label.setOpaque(false);
			cell.add(label, BorderLayout.CENTER);

			cell.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					label.setForeground(GuiApp.theme.getText());
					animateCellHover(true, hoverRef, timerRef, cell);

					if (item instanceof HasDescription described) {
						AppTooltip.show(cell, described.getDescription());
					}
				}

				@Override
				public void mouseExited(MouseEvent e) {
					if (!item.equals(selectedItem)) {
						label.setForeground(GuiApp.theme.getTextDim());
					}

					animateCellHover(isSelected ? 0.3f : 0f, hoverRef, timerRef, cell);
					AppTooltip.hide();
				}

				@Override
				public void mousePressed(MouseEvent e) {
					if (e.getButton() == MouseEvent.BUTTON1) {
						onItemSelected(item);
					}
				}
			});

			cell.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					AppTooltip.move();
				}
			});

			return cell;
		}

		private void animateCellHover(boolean toHovered, float[] hoverRef, Timer[] timerRef, JPanel cell) {
			animateCellHover(toHovered ? 1f : 0f, hoverRef, timerRef, cell);
		}

		private void animateCellHover(float to, float[] hoverRef, Timer[] timerRef, JPanel cell) {
			if (timerRef[0] != null) {
				timerRef[0].stop();
			}

			float from = hoverRef[0];

			timerRef[0] = UiAnimator.animateFloat(from, to, 120, progress -> {
				hoverRef[0] = progress;
				cell.repaint();
			}, null);
		}

		private JPanel buildPaddingStrut() {
			JPanel strut = new JPanel();
			strut.setOpaque(false);
			strut.setPreferredSize(new Dimension(0, LIST_PADDING_V));
			strut.setMaximumSize(new Dimension(Integer.MAX_VALUE, LIST_PADDING_V));
			strut.setMinimumSize(new Dimension(0, LIST_PADDING_V));
			return strut;
		}

		private JPanel buildSeparatorCell(String title) {
			JPanel cell = new JPanel(new BorderLayout()) {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					Color lineColor = new Color(
						Math.min(255, GuiApp.theme.getBorder().getRed() + 60),
						Math.min(255, GuiApp.theme.getBorder().getGreen() + 60),
						Math.min(255, GuiApp.theme.getBorder().getBlue() + 60),
						200
					);
					g2.setColor(lineColor);
					g2.setStroke(new BasicStroke(1f));

					String upper = title.toUpperCase();
					Font font = new Font("SansSerif", Font.BOLD, 11);
					int textWidth = g2.getFontMetrics(font).stringWidth(upper);
					int textX = (getWidth() - textWidth) / 2;
					int lineY = getHeight() / 2;
					int padding = 8;
					int gap = 6;

					g2.drawLine(padding, lineY, textX - gap, lineY);
					g2.drawLine(textX + textWidth + gap, lineY, getWidth() - padding, lineY);
					g2.dispose();
				}
			};
			cell.setOpaque(false);
			cell.setMaximumSize(new Dimension(Integer.MAX_VALUE, POPUP_ITEM_HEIGHT));
			cell.setPreferredSize(new Dimension(0, POPUP_ITEM_HEIGHT));

			JLabel label = new JLabel(title.toUpperCase(), SwingConstants.CENTER);
			label.setFont(new Font("SansSerif", Font.BOLD, 11));
			label.setForeground(GuiApp.theme.getTextDim());
			label.setOpaque(false);
			cell.add(label, BorderLayout.CENTER);

			return cell;
		}
	}
}
