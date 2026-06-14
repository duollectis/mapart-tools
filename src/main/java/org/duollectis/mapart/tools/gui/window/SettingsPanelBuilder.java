package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.InertialScrollPane;
import org.duollectis.mapart.tools.gui.widget.RippleButton;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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

		AccordionPanel appSettings = AppSettingsSectionBuilder.build(w);
		AccordionPanel image = ImageSectionBuilder.build(w);
		AccordionPanel blocks = BlocksSectionBuilder.build(w);
		AccordionPanel dithering = DitheringsSectionBuilder.build(w);
		AccordionPanel importSection = ImportSectionBuilder.build(w);
		AccordionPanel exportSection = ExportSectionBuilder.build(w);

		List<AccordionPanel> allAccordions = List.of(
			appSettings, image, blocks, dithering, importSection, exportSection
		);

		JPanel collapseBar = buildCollapseBar(allAccordions, w);
		collapseBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(collapseBar);
		content.add(Box.createVerticalStrut(2));
		content.add(appSettings);
		content.add(Box.createVerticalStrut(6));
		content.add(image);
		content.add(Box.createVerticalStrut(6));
		content.add(blocks);
		content.add(Box.createVerticalStrut(6));
		content.add(dithering);
		content.add(Box.createVerticalStrut(6));
		content.add(importSection);
		content.add(Box.createVerticalStrut(6));
		content.add(exportSection);

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

	private static JPanel buildCollapseBar(List<AccordionPanel> accordions, MainWindow w) {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.add(buildCollapseButton(accordions, w), BorderLayout.EAST);
		return bar;
	}

	private static RippleButton buildCollapseButton(List<AccordionPanel> accordions, MainWindow w) {
		RippleButton btn = new RippleButton(AppIcon.CROSS, new Insets(5, 5, 5, 5), w);
		btn.addActionListener(e -> accordions.forEach(AccordionPanel::collapseAnimated));
		UpdatableRegistry.registerLang("settings.collapse_all", btn::setToolTipText);
		return btn;
	}
}
