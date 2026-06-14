package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.app.AppPreferences;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;

import javax.swing.*;
import java.awt.*;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

final class ImportSectionBuilder {

	private ImportSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		w.importAddToBlocks = AppPreferences.loadImportAddToBlocks(false);

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildAddToPaletteRow(w));
		inner.add(Box.createVerticalStrut(8));
		inner.add(buildImportButtonRow(w));

		w.importAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.import", w.importAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.import", w.importAccordion);

		return w.importAccordion;
	}

	private static JPanel buildAddToPaletteRow(MainWindow w) {
		return buildToggleRow(
			"import.add_to_palette",
			w.importAddToBlocks,
			enabled -> {
				w.importAddToBlocks = enabled;
				AppPreferences.saveImportAddToBlocks(enabled);
			}
		);
	}

	private static JPanel buildImportButtonRow(MainWindow w) {
		w.importButton = buildIconButton(AppIcon.IMPORT_FILE, new Insets(6, 10, 6, 10), w);
		UpdatableRegistry.registerLang("btn.import", t -> AppTooltip.install(w.importButton, t));
		w.importButton.addActionListener(e -> w.actions.startImport());

		JPanel row = new JPanel(new BorderLayout(0, 0));
		row.setOpaque(false);
		row.add(w.importButton, BorderLayout.EAST);

		return row;
	}
}
