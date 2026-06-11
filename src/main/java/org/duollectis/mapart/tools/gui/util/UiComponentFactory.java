package org.duollectis.mapart.tools.gui.util;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Фабрика переиспользуемых UI-компонентов в стиле приложения.
 * Все методы читают цвета из {@link GuiApp} в момент вызова — тема-aware.
 */
@UtilityClass
class UiComponentFactory {

	private static final int CARD_RADIUS = 12;

	static javax.swing.JPanel buildCard() {
		javax.swing.JPanel card = new javax.swing.JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS * 2, CARD_RADIUS * 2);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new java.awt.BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS * 2, CARD_RADIUS * 2);
				g2.dispose();
			}
		};

		card.setOpaque(false);
		card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

		return card;
	}

	static JLabel buildSectionLabel(String text) {
		JLabel label = new JLabel(text.toUpperCase());
		label.setForeground(GuiApp.theme.getTextDim());
		label.setFont(new Font("SansSerif", Font.BOLD, 10));
		label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

		return label;
	}

	static JLabel dimLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(GuiApp.theme.getTextDim());
		label.setFont(new Font("SansSerif", Font.PLAIN, 12));

		return label;
	}

	static JTextField buildTextField(String placeholder) {
		JTextField field = new JTextField() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				if (getText().isEmpty()) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setColor(GuiApp.theme.getTextDim());
					g2.setFont(getFont().deriveFont(Font.ITALIC));
					Insets insets = getInsets();
					g2.drawString(
						placeholder,
						insets.left,
						getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 1
					);
					g2.dispose();
				}
			}
		};

		field.setBackground(GuiApp.theme.getBgInput());
		field.setForeground(GuiApp.theme.getText());
		field.setCaretColor(GuiApp.theme.getAccent());
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GuiApp.theme.getBorder()),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)
		));
		field.setFont(new Font("SansSerif", Font.PLAIN, 12));

		return field;
	}

	static JButton buildPrimaryButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? bgColor.darker()
					: (getModel().isRollover() ? bgColor.brighter() : bgColor);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));

		return btn;
	}

	static JButton buildAccentButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = getModel().isPressed()
					? bgColor.darker()
					: (getModel().isRollover() ? bgColor.brighter() : bgColor);
				g2.setColor(base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(GuiApp.theme.getBorder());
				g2.setStroke(new java.awt.BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

		return btn;
	}

	static JButton buildIconButton(String text) {
		return buildIconButton(text, 12, new Insets(6, 10, 6, 10));
	}

	static JButton buildIconButton(String text, int fontSize, Insets padding) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				if (getModel().isRollover()) {
					g2.setColor(GuiApp.theme.getHoverBgOverlay());
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setForeground(GuiApp.theme.getTextDim());
		btn.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right));
		addHoverEffect(btn);

		return btn;
	}

	static void stylePreviewPanel(ImagePreviewPanel panel) {
		panel.setBackground(GuiApp.theme.getBgCard());
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GuiApp.theme.getBorder()),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)
		));
	}

	private static void addHoverEffect(JButton btn) {
		btn.addMouseListener(new MouseAdapter() {
			private Timer activeTimer;

			@Override
			public void mouseEntered(MouseEvent e) {
				stopPrevious();
				activeTimer = UiAnimator.hoverTransition(btn, GuiApp.theme.getTextDim(), GuiApp.theme.getText(), 150, btn::setForeground);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				stopPrevious();
				activeTimer = UiAnimator.hoverTransition(btn, GuiApp.theme.getText(), GuiApp.theme.getTextDim(), 150, btn::setForeground);
			}

			private void stopPrevious() {
				if (activeTimer != null) {
					activeTimer.stop();
				}
			}
		});
	}
}
