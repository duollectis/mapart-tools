package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.FadingLabel;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;
import org.duollectis.mapart.tools.gui.widget.ThemedButton;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Блоки»: выбор версии, путь к файлу блоков, кнопки выбора и просмотра.
 */
final class BlocksSectionBuilder {

	private BlocksSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildVersionRow(w));
		inner.add(Box.createVerticalStrut(8));
		inner.add(buildBlocksRow(w));

		w.blocksAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.blocks", w.blocksAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.blocks", w.blocksAccordion);

		return w.blocksAccordion;
	}

	private static JComponent buildVersionRow(MainWindow w) {
		String[] versions = w.actions.loadVersions();
		java.util.List<Object> versionItems = new ArrayList<>();

		for (String v : versions) {
			versionItems.add(v);
		}

		w.versionCombo = new SelectionPanel<>(versionItems);

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.versionCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("label.version", accordion::setTitle);
		w.versionCombo.addInitializedSelectionListener(
			item -> accordion.setSubtitle(item != null ? item.toString() : null)
		);

		return accordion;
	}

	private static JPanel buildBlocksRow(MainWindow w) {
		w.blocksPathField = buildTextField();

		JButton browseBtn = buildIconButton(AppIcon.BROWSE, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.browse_blocks", t -> AppTooltip.install(browseBtn, t));
		browseBtn.addActionListener(e -> w.actions.chooseBlocks());


		w.blocksCountLabel = new JLabel("");
		w.blocksCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		w.blocksCountLabel.setForeground(MainWindow.TEXT_DIM());
		UpdatableRegistry.onThemeAnimFrame(() -> w.blocksCountLabel.setForeground(MainWindow.TEXT_DIM()));

		FadingLabel label = buildFadingDimLabel("");
		UpdatableRegistry.registerLangFading("label.blocks_path", label);

		JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
		btnRow.setOpaque(false);
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
		wrapper.add(Box.createVerticalStrut(6));
		wrapper.add(buildPickerButton(w));

		return wrapper;
	}

	private static JPanel buildPickerButton(MainWindow w) {
		ThemedButton btn = buildAccentButton("");
		UpdatableRegistry.registerLang("btn.pick_blocks", btn::setText);
		btn.addActionListener(e -> w.showBlockPicker(() -> {
			w.blocksCountLabel.setText(UpdatableRegistry.translate("label.blocks_count", w.enabledBlocks.size()));
			w.actions.startConversionForBlockList();
		}));
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(btn, BorderLayout.CENTER);
		return row;
	}
}
