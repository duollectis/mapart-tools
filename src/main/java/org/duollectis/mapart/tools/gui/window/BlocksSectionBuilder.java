package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;

import javax.swing.*;
import java.awt.*;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Блоки»: путь к файлу блоков, кнопки выбора и просмотра.
 */
final class BlocksSectionBuilder {

	private BlocksSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildBlocksRow(w));

		w.blocksAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.blocks", w.blocksAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.blocks", w.blocksAccordion);

		return w.blocksAccordion;
	}

	private static JPanel buildBlocksRow(MainWindow w) {
		w.blocksPathField = buildTextField();

		JButton browseBtn = buildIconButton(AppIcon.BROWSE, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.browse_blocks", t -> AppTooltip.install(browseBtn, t));
		browseBtn.addActionListener(e -> w.actions.chooseBlocks());

		JButton pickBtn = buildIconButton(AppIcon.BLOCK, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.pick_blocks", t -> AppTooltip.install(pickBtn, t));
		pickBtn.addActionListener(e -> w.actions.openBlockPicker());
		w.pickBlocksButton = pickBtn;

		JButton listBtn = buildIconButton(AppIcon.LIST, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.block_list", t -> AppTooltip.install(listBtn, t));
		listBtn.addActionListener(e -> w.actions.openBlockList());
		w.blockListButton = listBtn;

		w.blocksCountLabel = new JLabel("");
		w.blocksCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		w.blocksCountLabel.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> w.blocksCountLabel.setForeground(MainWindow.TEXT_DIM()));

		JLabel label = dimLabel("");
		UpdatableRegistry.registerLang("label.blocks_path", label::setText);

		JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
		btnRow.setOpaque(false);
		btnRow.add(listBtn);
		btnRow.add(pickBtn);
		btnRow.add(browseBtn);

		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setOpaque(false);
		row.add(label, BorderLayout.NORTH);
		row.add(w.blocksPathField, BorderLayout.CENTER);
		row.add(btnRow, BorderLayout.EAST);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(row);
		wrapper.add(Box.createVerticalStrut(4));
		wrapper.add(w.blocksCountLabel);

		return wrapper;
	}
}
