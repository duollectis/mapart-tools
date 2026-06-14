package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.AnimatedPanel;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Экспорт»: папка вывода, формат схематика, режим лестниц, кнопки экспорта/импорта.
 */
final class ExportSectionBuilder {

	private ExportSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildOutPathRow(w));
		inner.add(Box.createVerticalStrut(8));
		inner.add(buildFormatRow(w));
		inner.add(buildMapDatStartIdRow(w));
		inner.add(Box.createVerticalStrut(8));
		inner.add(buildExportButtonRow(w));

		w.exportAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.export", w.exportAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.export", w.exportAccordion);

		return w.exportAccordion;
	}

	private static JPanel buildOutPathRow(MainWindow w) {
		w.outPathField = buildTextField();

		JButton browseBtn = buildIconButton(AppIcon.FOLDER_OPEN, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.browse_out", t -> AppTooltip.install(browseBtn, t));
		browseBtn.addActionListener(e -> w.actions.chooseOutDir());

		JLabel label = dimLabel("");
		UpdatableRegistry.registerLang("label.out_path", label::setText);

		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setOpaque(false);
		row.add(label, BorderLayout.NORTH);
		row.add(w.outPathField, BorderLayout.CENTER);
		row.add(browseBtn, BorderLayout.EAST);

		return row;
	}

	private static JComponent buildFormatRow(MainWindow w) {
		List<Object> formatItems = new ArrayList<>();
		formatItems.add(SchematicFormat.NBT);
		formatItems.add(SchematicFormat.LITEMATIC);
		formatItems.add(SchematicFormat.SCHEM);
		formatItems.add(SchematicFormat.MAP_DAT);

		w.formatCombo = new SelectionPanel<>(formatItems);
		w.formatCombo.setDisplayConverter(item -> item instanceof SchematicFormat f
				? UpdatableRegistry.translate("format." + f.name())
				: item.toString()
		);
		w.formatCombo.addSelectionListener(item -> w.actions.syncExportButtonLabel());

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.formatCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.format", accordion::setTitle);
		w.formatCombo.addInitializedSelectionListener(item -> {
			if (item == null) {
				return;
			}

			accordion.setSubtitle(w.formatCombo.getDisplayText(item));
		});

		return accordion;
	}

	private static JComponent buildMapDatStartIdRow(MainWindow w) {
		w.mapDatStartIdField = buildTextField();
		w.mapDatStartIdField.setText("0");

		JLabel label = dimLabel("");
		UpdatableRegistry.registerLang("label.map_dat_start_id", label::setText);

		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setOpaque(false);
		row.add(label, BorderLayout.NORTH);
		row.add(w.mapDatStartIdField, BorderLayout.CENTER);

		AnimatedPanel panel = new AnimatedPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(row, BorderLayout.CENTER);
		panel.collapseInstant();
		w.mapDatStartIdPanel = panel;

		return panel;
	}

	private static JPanel buildExportButtonRow(MainWindow w) {
		w.exportButton = buildThemedButton("", w);
		UpdatableRegistry.registerLang("btn.export", w.exportButton::setText);
		w.exportButton.addActionListener(e -> w.actions.startExport());

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(w.exportButton, BorderLayout.CENTER);

		return row;
	}
}
