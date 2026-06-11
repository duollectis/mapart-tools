package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class ModernComboBox<T> extends JComboBox<T> {

	/** Заголовок группы в выпадающем списке — нельзя выбрать, отображается как dim-разделитель. */
	public record Separator(String title) {}

	private static Color bg() { return GuiApp.theme.getBgInput(); }
	private static Color bgHover() { return GuiApp.theme.getBgCard(); }
	private static Color border() { return GuiApp.theme.getBorder(); }
	private static Color borderHover() { return GuiApp.theme.getAccentBright(); }
	private static Color text() { return GuiApp.theme.getText(); }
	private static Color textDim() { return GuiApp.theme.getTextDim(); }
	private static Color selection() { return GuiApp.theme.getSelectionBg(); }
	private static Color popupBg() { return GuiApp.theme.getBgDeep(); }

	private float hoverProgress = 0f;
	private Timer hoverTimer;

	public ModernComboBox(T[] items) {
		super(items);
		init();
	}

	public ModernComboBox(javax.swing.ComboBoxModel<T> model) {
		super(model);
		init();
	}

	private void init() {
		setOpaque(false);
		setBackground(bg());
		setForeground(text());
		setFont(new Font("SansSerif", Font.PLAIN, 13));
		setBorder(BorderFactory.createEmptyBorder());
		setRenderer(buildRenderer());
		setUI(new ModernComboBoxUI());
		setPreferredSize(new Dimension(getPreferredSize().width, 32));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				animateHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				animateHover(false);
			}
		});
	}

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

	@Override
	public void setSelectedItem(Object item) {
		if (item instanceof Separator) {
			return;
		}
		super.setSelectedItem(item);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor(UiAnimator.lerp(bg(), bgHover(), hoverProgress));
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

		g2.setColor(UiAnimator.lerp(border(), borderHover(), hoverProgress));
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

		g2.dispose();
		super.paintComponent(g);
	}

	@Override
	protected void paintBorder(Graphics g) {
		// граница рисуется в paintComponent
	}

	private ListCellRenderer<Object> buildRenderer() {
		return (list, value, index, isSelected, cellHasFocus) -> {
			boolean isHeader = index == -1;
			boolean isSeparator = value instanceof Separator;

			if (isSeparator) {
				return buildSeparatorCell(((Separator) value).title());
			}

			JLabel label = new JLabel(value == null ? "" : value.toString()) {
				@Override
				protected void paintComponent(Graphics g) {
					if (!isHeader && isSelected) {
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g2.setColor(selection());
						g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 6, 6);
						g2.dispose();
					}

					super.paintComponent(g);
				}
			};

			label.setFont(new Font("SansSerif", Font.PLAIN, 13));
			label.setForeground(isSelected && !isHeader ? text() : textDim());
			label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			label.setOpaque(false);
			label.setBackground(isHeader ? bg() : popupBg());

			return label;
		};
	}

	private static JComponent buildSeparatorCell(String title) {
		JLabel label = new JLabel(title.toUpperCase()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				// Линия светлее фона попапа — берём цвет между BORDER и TEXT_DIM
				Color lineColor = new Color(
					Math.min(255, GuiApp.theme.getBorder().getRed() + 60),
					Math.min(255, GuiApp.theme.getBorder().getGreen() + 60),
					Math.min(255, GuiApp.theme.getBorder().getBlue() + 60),
					200
				);
				g2.setColor(lineColor);
				g2.setStroke(new BasicStroke(1f));

				int lineY = getHeight() / 2;
				int textWidth = getFontMetrics(getFont()).stringWidth(getText());
				int padding = 8;
				int gap = 6;
				int textX = (getWidth() - textWidth) / 2;
				int lineLeft = padding;
				int lineRight = getWidth() - padding;

				g2.drawLine(lineLeft, lineY, textX - gap, lineY);
				g2.drawLine(textX + textWidth + gap, lineY, lineRight, lineY);

				g2.dispose();
				super.paintComponent(g);
			}
		};

		label.setFont(new Font("SansSerif", Font.BOLD, 11));
		label.setForeground(GuiApp.theme.getTextDim());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		label.setOpaque(false);
		label.setEnabled(false);

		return label;
	}

	private static class ModernComboBoxUI extends BasicComboBoxUI {

		@Override
		protected JButton createArrowButton() {
			JButton btn = new JButton() {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					int w = 8;
					int h = 5;

					g2.setColor(textDim());
					g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawLine(cx - w / 2, cy - h / 2, cx, cy + h / 2);
					g2.drawLine(cx, cy + h / 2, cx + w / 2, cy - h / 2);
					g2.dispose();
				}
			};
			btn.setOpaque(false);
			btn.setContentAreaFilled(false);
			btn.setBorderPainted(false);
			btn.setFocusPainted(false);
			btn.setPreferredSize(new Dimension(28, 0));

			return btn;
		}

		@Override
		protected ComboPopup createPopup() {
			BasicComboPopup popup = new BasicComboPopup(comboBox) {

				private Timer openTimer;
				private DescriptionTooltip tooltip;
	
				private int fullHeight = 0;
				private int clipHeight = 0;
				private int maxPopupHeight = Integer.MAX_VALUE;
	
				@Override
				public void doLayout() {
					super.doLayout();
					// BorderLayout растягивает JScrollPane на весь попап игнорируя maxPopupHeight.
					// Принудительно ограничиваем высоту JScrollPane после каждого layout-прохода.
					if (maxPopupHeight < Integer.MAX_VALUE) {
						for (Component comp : getComponents()) {
							if (comp instanceof javax.swing.JScrollPane && comp.getHeight() > maxPopupHeight) {
								comp.setBounds(comp.getX(), comp.getY(), comp.getWidth(), maxPopupHeight);
							}
						}
					}
				}
	
				/**
				 * Двухэтапный подход без мигания:
				 * 1. setPreferredSize() до super.show() — pack() берёт наш размер
				 * 2. После super.show() синхронно корректируем bounds нативного окна —
				 *    PopupFactory иногда игнорирует preferredSize и обрезает высоту сам.
				 * Мигания нет: окно уже появилось в правильном месте (этап 1),
				 * этап 2 только уточняет размер если нужно.
				 */
				@Override
					public void show() {
						comboBox.setMaximumRowCount(comboBox.getModel().getSize() + 1);
						Dimension target = computeTargetSize();
						maxPopupHeight = target.height;
						setPreferredSize(target);
						super.show();
						list.ensureIndexIsVisible(0);
						forcePopupBounds(target);
						animateOpen(getHeight());
					}
	
				@Override
				public void show(Component invoker, int x, int y) {
					super.show(invoker, x, comboBox.getHeight());
				}
	
				private static final int POPUP_BOTTOM_GAP = 10;
	
				private Dimension computeTargetSize() {
						int popupWidth = comboBox.getWidth();
	
						int itemCount = comboBox.getModel().getSize();
						int rowHeight = list.getFixedCellHeight() > 0 ? list.getFixedCellHeight() : 32;
						int naturalHeight = itemCount * rowHeight + 2;
	
						if (popupWidth <= 0) {
							return new Dimension(100, naturalHeight);
						}
	
						javax.swing.JRootPane rootPane = SwingUtilities.getRootPane(comboBox);
						if (rootPane == null) {
							return new Dimension(popupWidth, naturalHeight);
						}
	
						javax.swing.JLayeredPane layeredPane = rootPane.getLayeredPane();
						Point comboOnScreen = comboBox.getLocationOnScreen();
						Point layeredOnScreen = layeredPane.getLocationOnScreen();
						int popupLocalY = comboOnScreen.y + comboBox.getHeight() - layeredOnScreen.y;
						int available = layeredPane.getHeight() - popupLocalY - POPUP_BOTTOM_GAP;
						int targetHeight = available > 0 ? Math.min(naturalHeight, available) : naturalHeight;
	
						return new Dimension(popupWidth, targetHeight);
					}
	
				/**
				 * Корректирует позицию обёртки попапа в JLayeredPane.
				 * Нужно потому что show(invoker, x, y) задаёт y относительно invoker,
				 * но JLayeredPane может иметь смещение относительно экрана.
				 */
				private void forcePopupBounds(Dimension target) {
					javax.swing.JRootPane rootPane = SwingUtilities.getRootPane(comboBox);
					if (rootPane == null) {
						return;
					}
	
					javax.swing.JLayeredPane layeredPane = rootPane.getLayeredPane();
					Component[] popupLayerComps = layeredPane.getComponentsInLayer(javax.swing.JLayeredPane.POPUP_LAYER);
	
					for (Component comp : popupLayerComps) {
						if (comp.isVisible()) {
							Point comboOnScreen = comboBox.getLocationOnScreen();
							Point layeredOnScreen = layeredPane.getLocationOnScreen();
							int localX = comboOnScreen.x - layeredOnScreen.x;
							int localY = comboOnScreen.y + comboBox.getHeight() - layeredOnScreen.y;
							comp.setBounds(localX, localY, target.width, target.height);
							break;
						}
					}
	
					layeredPane.revalidate();
				}
	
				private void animateOpen(int targetHeight) {
					if (openTimer != null) {
						openTimer.stop();
					}
	
					fullHeight = targetHeight;
					clipHeight = 0;
	
					openTimer = UiAnimator.animateFloat(0f, 1f, 180, progress -> {
						clipHeight = (int) (fullHeight * UiAnimator.easeOutCubic(progress));
						repaint();
					}, () -> {
						clipHeight = fullHeight;
						repaint();
					});
				}
	
				@Override
					protected void paintChildren(Graphics g) {
						int h = clipHeight > 0 && clipHeight < fullHeight ? clipHeight : getHeight();
						Graphics clipped = g.create(0, 0, getWidth(), h);
						super.paintChildren(clipped);
						clipped.dispose();
					}
	
					@Override
					protected void paintBorder(Graphics g) {
						int h = clipHeight > 0 && clipHeight < fullHeight ? clipHeight : getHeight();
						Graphics clipped = g.create(0, 0, getWidth(), h);
						super.paintBorder(clipped);
						clipped.dispose();
					}
	
					@Override
					public void paintComponent(Graphics g) {
						int h = clipHeight > 0 && clipHeight < fullHeight ? clipHeight : getHeight();
						g.setColor(popupBg());
						g.fillRect(0, 0, getWidth(), h);
					}

				@Override
				protected JScrollPane createScroller() {
					JScrollPane scroller = super.createScroller();
					scroller.setBorder(BorderFactory.createEmptyBorder());
					scroller.getViewport().setBackground(popupBg());
					scroller.setBackground(popupBg());
	
					return scroller;
				}

				@Override
				protected MouseListener createListMouseListener() {
					MouseListener original = super.createListMouseListener();
					return new MouseAdapter() {
						@Override
						public void mousePressed(MouseEvent e) {
							if (isSeparatorAt(e)) {
								return;
							}
							original.mousePressed(e);
						}

						@Override
						public void mouseReleased(MouseEvent e) {
							if (isSeparatorAt(e)) {
								return;
							}
							original.mouseReleased(e);
						}

						private boolean isSeparatorAt(MouseEvent e) {
							int index = list.locationToIndex(e.getPoint());
							return index >= 0 && list.getModel().getElementAt(index) instanceof Separator;
						}
					};
				}

				@Override
				protected JList<Object> createList() {
					JList<Object> list = new JList<>(comboBox.getModel());
	
					list.setCellRenderer(comboBox.getRenderer());
					list.setFixedCellHeight(32);
					list.setBackground(popupBg());
					list.setSelectionModel(new javax.swing.DefaultListSelectionModel() {
						@Override
						public void setSelectionInterval(int index0, int index1) {
							if (isSeparatorIndex(index0)) {
								return;
							}
							super.setSelectionInterval(index0, index1);
						}
	
						@Override
						public void addSelectionInterval(int index0, int index1) {
							if (isSeparatorIndex(index0)) {
								return;
							}
							super.addSelectionInterval(index0, index1);
						}
	
						private boolean isSeparatorIndex(int index) {
							return index >= 0
								&& index < comboBox.getModel().getSize()
								&& comboBox.getModel().getElementAt(index) instanceof Separator;
						}
					});

					tooltip = new DescriptionTooltip(list);

					list.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseExited(MouseEvent e) {
							tooltip.hide();
						}
					});

					list.addMouseMotionListener(new MouseAdapter() {
						@Override
						public void mouseMoved(MouseEvent e) {
							int index = list.locationToIndex(e.getPoint());
							Object item = index >= 0 ? list.getModel().getElementAt(index) : null;

							if (item instanceof Separator) {
								tooltip.hide();
								return;
							}

							list.setSelectedIndex(index);

							String desc = (item instanceof HasDescription hd) ? hd.getDescription() : null;
							tooltip.show(desc, e.getLocationOnScreen());
						}
					});

					return list;
				}
	
				@Override
				public void hide() {
					if (tooltip != null) {
						tooltip.hide();
					}
					super.hide();
				}
			};

			popup.setOpaque(true);
			popup.getList().setBackground(popupBg());
			popup.getList().setForeground(text());
			popup.getList().setSelectionBackground(new Color(0, 0, 0, 0));
			popup.getList().setSelectionForeground(text());
			popup.setBorder(BorderFactory.createLineBorder(borderHover(), 1));
			popup.setBackground(popupBg());

			return popup;
		}

		@Override
		public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
			// фон рисуется в paintComponent самого комбобокса
		}
	}

	/**
	 * Постоянный тултип-попап для элементов выпадающего списка.
	 * Показывается при наведении с плавным fade-in и не скрывается автоматически —
	 * только при уходе курсора с элемента или закрытии попапа.
	 */
	private static final class DescriptionTooltip {

		private static final int OFFSET_X = 16;
		private static final int OFFSET_Y = 4;
		private static final int FADE_DURATION_MS = 180;

		private final JWindow window;
		private final JLabel label;
		private String currentDesc;
		private Timer fadeTimer;

		DescriptionTooltip(Component owner) {
			window = new JWindow(SwingUtilities.getWindowAncestor(owner));
			window.setFocusableWindowState(false);
			window.setAlwaysOnTop(true);
			window.setOpacity(0f);

			label = new JLabel();
			label.setFont(new Font("SansSerif", Font.PLAIN, 12));
			label.setForeground(GuiApp.theme.getText());
			label.setBackground(GuiApp.theme.getTooltipBg());
			label.setOpaque(true);
			label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(GuiApp.theme.getBorder(), 1),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)
			));

			window.getContentPane().add(label);
		}

		void show(String desc, Point screenPos) {
			if (desc == null || desc.isBlank()) {
				hide();
				return;
			}

			window.setLocation(screenPos.x + OFFSET_X, screenPos.y + OFFSET_Y);

			if (desc.equals(currentDesc) && window.isVisible()) {
				return;
			}

			currentDesc = desc;
			label.setText(desc);
			window.pack();
			window.setLocation(screenPos.x + OFFSET_X, screenPos.y + OFFSET_Y);

			stopFade();
			window.setOpacity(0f);
			window.setVisible(true);

			fadeTimer = UiAnimator.animateFloat(0f, 1f, FADE_DURATION_MS, opacity -> {
				window.setOpacity(UiAnimator.easeOutCubic(opacity));
			}, null);
		}

		void hide() {
			stopFade();
			currentDesc = null;
			window.setOpacity(0f);
			window.setVisible(false);
		}

		private void stopFade() {
			if (fadeTimer != null) {
				fadeTimer.stop();
				fadeTimer = null;
			}
		}
	}
}
