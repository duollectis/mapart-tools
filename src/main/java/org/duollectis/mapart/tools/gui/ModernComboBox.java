package org.duollectis.mapart.tools.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernComboBox<T> extends JComboBox<T> {

	private static final Color BG = GuiApp.BG_INPUT;
	private static final Color BG_HOVER = new Color(38, 42, 62);
	private static final Color BORDER = GuiApp.BORDER;
	private static final Color BORDER_HOVER = new Color(80, 100, 140);
	private static final Color TEXT = GuiApp.TEXT;
	private static final Color TEXT_DIM = GuiApp.TEXT_DIM;
	private static final Color SELECTION = new Color(49, 130, 206, 180);
	private static final Color ITEM_HOVER = new Color(38, 45, 65);
	private static final Color POPUP_BG = new Color(22, 25, 38);

	private boolean hovered;

	public ModernComboBox(T[] items) {
		super(items);
		setOpaque(false);
		setBackground(BG);
		setForeground(TEXT);
		setFont(new Font("SansSerif", Font.PLAIN, 13));
		setBorder(BorderFactory.createEmptyBorder());
		setRenderer(buildRenderer());
		setUI(new ModernComboBoxUI());
		setPreferredSize(new Dimension(getPreferredSize().width, 32));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hovered = false;
				repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor(hovered ? BG_HOVER : BG);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

		g2.setColor(hovered ? BORDER_HOVER : BORDER);
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

			JComponent label = new javax.swing.JLabel(value == null ? "" : value.toString()) {
				@Override
				protected void paintComponent(Graphics g) {
					if (!isHeader && isSelected) {
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g2.setColor(SELECTION);
						g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 6, 6);
						g2.dispose();
					}

					super.paintComponent(g);
				}
			};

			((javax.swing.JLabel) label).setFont(new Font("SansSerif", Font.PLAIN, 13));
			((javax.swing.JLabel) label).setForeground(isSelected && !isHeader ? TEXT : TEXT_DIM);
			((javax.swing.JLabel) label).setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			label.setOpaque(false);
			label.setBackground(isHeader ? BG : POPUP_BG);

			return label;
		};
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

					g2.setColor(TEXT_DIM);
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
				@Override
				public void paintComponent(Graphics g) {
					g.setColor(POPUP_BG);
					g.fillRect(0, 0, getWidth(), getHeight());
				}

				@Override
				protected JScrollPane createScroller() {
					JScrollPane scroller = super.createScroller();
					scroller.setBorder(BorderFactory.createEmptyBorder());
					scroller.getViewport().setBackground(POPUP_BG);
					scroller.setBackground(POPUP_BG);
					scroller.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());

					return scroller;
				}

				@Override
				protected JList<Object> createList() {
					JList<Object> list = super.createList();
					list.setFixedCellHeight(32);
					list.setBackground(POPUP_BG);

					list.addMouseMotionListener(new MouseAdapter() {
						@Override
						public void mouseMoved(MouseEvent e) {
							int index = list.locationToIndex(e.getPoint());
							list.setSelectedIndex(index);
						}
					});

					return list;
				}
			};

			popup.setOpaque(true);
			popup.getList().setBackground(POPUP_BG);
			popup.getList().setForeground(TEXT);
			popup.getList().setSelectionBackground(new Color(0, 0, 0, 0));
			popup.getList().setSelectionForeground(TEXT);
			popup.setBorder(BorderFactory.createLineBorder(BORDER_HOVER, 1));
			popup.setBackground(POPUP_BG);

			return popup;
		}

		@Override
		public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
			// фон рисуется в paintComponent самого комбобокса
		}

	}
}
