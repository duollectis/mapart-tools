package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.ModernToggleButton;
import org.duollectis.mapart.tools.gui.widget.SliderRow;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.swing.*;
import java.awt.*;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Изображение»: коррекция яркости/контраста/насыщенности/гаммы/оттенка.
 * Управление размером карт вынесено в тулбар превью.
 */
final class ImageSectionBuilder {

	private ImageSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		w.actions.setupImageDropTarget(w.sourcePreview);

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildImageAdjustPanel(w));

		w.imageAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.image", w.imageAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.image", w.imageAccordion);

		return w.imageAccordion;
	}

	private static JPanel buildImageAdjustPanel(MainWindow w) {
		ImageAdjustments defaults = ImageAdjustments.defaults();

		w.brightnessRow = new SliderRow(-100, 100, defaults.brightness(), false);
		w.contrastRow = new SliderRow(-100, 100, defaults.contrast(), false);
		w.saturationRow = new SliderRow(-100, 100, defaults.saturation(), false);
		w.gammaRow = new SliderRow(10, 300, defaults.gamma(), true);
		w.hueRow = new SliderRow(-180, 180, defaults.hue(), false);

		w.brightnessRow.addChangeListener(() -> {
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.contrastRow.addChangeListener(() -> {
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.saturationRow.addChangeListener(() -> {
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.gammaRow.addChangeListener(() -> {
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.hueRow.addChangeListener(() -> {
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.brightnessRow.registerLang("adjust.brightness");
		w.contrastRow.registerLang("adjust.contrast");
		w.saturationRow.registerLang("adjust.saturation");
		w.gammaRow.registerLang("adjust.gamma");
		w.hueRow.registerLang("adjust.hue");

		w.autoConvertToggle = new ModernToggleButton("");
		UpdatableRegistry.registerLang("adjust.auto_convert", w.autoConvertToggle::setText);

		JButton resetBtn = buildResetButton(w);
		resetBtn.addActionListener(e -> resetAdjustments(w));

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;

		w.brightnessRow.addToGrid(grid, gbc, 0, w);
		w.contrastRow.addToGrid(grid, gbc, 1, w);
		w.saturationRow.addToGrid(grid, gbc, 2, w);
		w.gammaRow.addToGrid(grid, gbc, 3, w);
		w.hueRow.addToGrid(grid, gbc, 4, w);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(grid);
		wrapper.add(Box.createVerticalStrut(4));

		JPanel controlRow = new JPanel(new BorderLayout(6, 0));
		controlRow.setOpaque(false);
		controlRow.add(w.autoConvertToggle, BorderLayout.WEST);
		controlRow.add(resetBtn, BorderLayout.EAST);
		wrapper.add(controlRow);

		return wrapper;
	}

	private static void resetAdjustments(MainWindow w) {
		w.brightnessRow.reset();
		w.contrastRow.reset();
		w.saturationRow.reset();
		w.gammaRow.reset();
		w.hueRow.reset();
	}
}
