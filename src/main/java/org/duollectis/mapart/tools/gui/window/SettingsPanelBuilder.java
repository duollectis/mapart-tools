package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;

import javax.swing.*;
import java.awt.*;

/**
 * Оркестратор левой колонки настроек {@link MainWindow}.
 * Делегирует построение каждой секции специализированным билдерам,
 * сам отвечает только за компоновку скролла и кнопки конвертации.
 */
class SettingsPanelBuilder {

	static JPanel buildSettingsPanel(MainWindow w) {
		JPanel content = new ScrollablePanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));

		content.add(AppSettingsSectionBuilder.build(w));
		content.add(Box.createVerticalStrut(6));
		content.add(ImageSectionBuilder.build(w));
		content.add(Box.createVerticalStrut(6));
		content.add(BlocksSectionBuilder.build(w));
		content.add(Box.createVerticalStrut(6));
		content.add(DitheringsSectionBuilder.build(w));
		content.add(Box.createVerticalStrut(6));
		content.add(ImportSectionBuilder.build(w));
		content.add(Box.createVerticalStrut(6));
		content.add(ExportSectionBuilder.build(w));

		InertialScrollPane scroll = new InertialScrollPane(content);

		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(MainWindow.CARD());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), MainWindow.CARD_RADIUS * 2, MainWindow.CARD_RADIUS * 2);
				g2.setColor(MainWindow.BORDER());
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, MainWindow.CARD_RADIUS * 2, MainWindow.CARD_RADIUS * 2);
				g2.dispose();
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(MainWindow.SETTINGS_MIN_WIDTH, super.getMinimumSize().height);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(MainWindow.SETTINGS_MAX_WIDTH, Integer.MAX_VALUE);
			}
		};
		card.setOpaque(false);
		card.setBorder(BorderFactory.createEmptyBorder());
		card.setLayout(new BorderLayout());
		card.add(scroll, BorderLayout.CENTER);

		return card;
	}
}
