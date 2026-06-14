package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.DitherAlgorithm;
import org.duollectis.mapart.tools.converter.DitherSettings;
import org.duollectis.mapart.tools.converter.ColorMetric;
import org.duollectis.mapart.tools.converter.StaircaseMode;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.AnimatedPanel;
import org.duollectis.mapart.tools.gui.widget.RgbChannelsButton;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;
import org.duollectis.mapart.tools.gui.widget.SliderRow;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Дизеринг»: алгоритм, метрика цвета, режим лестниц,
 * слайдеры шума и скорости ошибки с поддержкой раздельных RGB-каналов.
 */
final class DitheringsSectionBuilder {

	private DitheringsSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildAlgorithmRow(w));
		inner.add(Box.createVerticalStrut(4));
		inner.add(buildColorMetricRow(w));
		inner.add(Box.createVerticalStrut(4));
		inner.add(buildStaircaseModeRow(w));
		inner.add(Box.createVerticalStrut(8));

		w.ditherSettingsPanel = buildDitherSettingsPanel(w);
		inner.add(w.ditherSettingsPanel);

		w.ditheringAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.dithering", w.ditheringAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.dithering", w.ditheringAccordion);

		return w.ditheringAccordion;
	}

	private static JComponent buildAlgorithmRow(MainWindow w) {
		w.algorithmCombo = new SelectionPanel<>(buildAlgorithmItems());
		w.algorithmCombo.addSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator) {
				return;
			}

			refreshDitherSettingsPanel(w);
			w.actions.scheduleConversionIfAuto();
		});

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.algorithmCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.algorithm", accordion::setTitle);
		w.algorithmCombo.addInitializedSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator || item == null) {
				return;
			}

			accordion.setSubtitle(w.algorithmCombo.getDisplayText(item));
		});

		return accordion;
	}

	private static List<Object> buildAlgorithmItems() {
		List<Object> model = new ArrayList<>();
		String lastGroup = null;

		for (DitherAlgorithm alg : DitherAlgorithm.values()) {
			if (!alg.getGroupKey().equals(lastGroup)) {
				model.add(new SelectionPanel.Separator(alg.getGroupKey()));
				lastGroup = alg.getGroupKey();
			}

			model.add(alg);
		}

		return model;
	}

	private static JComponent buildStaircaseModeRow(MainWindow w) {
		w.staircaseModeCombo = new SelectionPanel<>(StaircaseMode.values());
		w.staircaseModeCombo.setSelectedItem(StaircaseMode.VALLEY);
		w.staircaseModeCombo.addSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator) {
				return;
			}

			w.actions.scheduleConversionIfAuto();
		});

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.staircaseModeCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.staircase_mode", accordion::setTitle);
		w.staircaseModeCombo.addInitializedSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator || item == null) {
				return;
			}

			accordion.setSubtitle(w.staircaseModeCombo.getDisplayText(item));
		});

		return accordion;
	}

	private static JComponent buildColorMetricRow(MainWindow w) {
		w.colorMetricCombo = new SelectionPanel<>(ColorMetric.values());
		w.colorMetricCombo.addSelectionListener(item -> w.actions.scheduleConversionIfAuto());

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.colorMetricCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.color_metric", accordion::setTitle);
		w.colorMetricCombo.addInitializedSelectionListener(item -> {
			if (item == null) {
				return;
			}

			accordion.setSubtitle(w.colorMetricCombo.getDisplayText(item));
		});

		return accordion;
	}

	private static JPanel buildDitherSettingsPanel(MainWindow w) {
		DitherSettings defaults = DitherSettings.defaults();

		w.noiseLevelRow = new SliderRow(0, 100, (int) (defaults.noiseLevel() * 100), false);
		w.errRateStrengthRow = new SliderRow(0, 200, (int) (defaults.errRateR() * 100), false);
		w.errRateRRow = new SliderRow(0, 200, (int) (defaults.errRateR() * 100), false);
		w.errRateGRow = new SliderRow(0, 200, (int) (defaults.errRateG() * 100), false);
		w.errRateBRow = new SliderRow(0, 200, (int) (defaults.errRateB() * 100), false);

		w.noiseLevelRow.addChangeListener(w.actions::scheduleConversionIfAuto);
		w.errRateStrengthRow.addChangeListener(w.actions::scheduleConversionIfAuto);
		w.errRateRRow.addChangeListener(w.actions::scheduleConversionIfAuto);
		w.errRateGRow.addChangeListener(w.actions::scheduleConversionIfAuto);
		w.errRateBRow.addChangeListener(w.actions::scheduleConversionIfAuto);

		w.noiseLevelRow.registerLang("dither.noise_level");
		w.errRateStrengthRow.registerLang("dither.err_rate_strength");
		w.errRateRRow.registerLang("dither.err_rate_r");
		w.errRateGRow.registerLang("dither.err_rate_g");
		w.errRateBRow.registerLang("dither.err_rate_b");

		w.errRateLinkButton = new RgbChannelsButton();

		JPanel topGrid = new JPanel(new GridBagLayout());
		topGrid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;

		w.noiseLevelRow.addToGrid(topGrid, gbc, 0, w);

		AnimatedPanel strengthPanel = new AnimatedPanel(new GridBagLayout());
		GridBagConstraints strengthGbc = new GridBagConstraints();
		strengthGbc.fill = GridBagConstraints.HORIZONTAL;
		w.errRateStrengthRow.addToGrid(strengthPanel, strengthGbc, 0, w);
		w.errRateStrengthPanel = strengthPanel;

		AnimatedPanel rPanel = new AnimatedPanel(new GridBagLayout());
		GridBagConstraints rGbc = new GridBagConstraints();
		rGbc.fill = GridBagConstraints.HORIZONTAL;
		w.errRateRRow.addToGrid(rPanel, rGbc, 0, w);
		w.errRateRPanel = rPanel;

		AnimatedPanel rgbRowsPanel = new AnimatedPanel(new GridBagLayout());
		GridBagConstraints rgbGbc = new GridBagConstraints();
		rgbGbc.fill = GridBagConstraints.HORIZONTAL;
		w.errRateGRow.addToGrid(rgbRowsPanel, rgbGbc, 0, w);
		w.errRateBRow.addToGrid(rgbRowsPanel, rgbGbc, 1, w);
		w.errRateRgbPanel = rgbRowsPanel;

		w.errRateLinkButton.addActionListener(e -> {
			boolean separate = w.errRateLinkButton.isSelected();

			w.errRateStrengthPanel.animateVisible(!separate, () -> notifyAccordionResized(w));
			w.errRateRPanel.animateVisible(separate, () -> notifyAccordionResized(w));
			w.errRateRgbPanel.animateVisible(separate, () -> notifyAccordionResized(w));
			w.actions.scheduleConversionIfAuto();
		});

		JPanel buttonBar = new JPanel(new BorderLayout());
		buttonBar.setOpaque(false);
		buttonBar.add(w.errRateLinkButton, BorderLayout.EAST);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.add(topGrid);
		panel.add(strengthPanel);
		panel.add(rPanel);
		panel.add(rgbRowsPanel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(buttonBar);

		return panel;
	}

	/**
	 * Обновляет видимость слайдеров дизеринга в зависимости от выбранного алгоритма.
	 * Вызывается при смене алгоритма и при восстановлении настроек.
	 */
	static void refreshDitherSettingsPanel(MainWindow w) {
		if (w.ditherSettingsPanel == null) {
			return;
		}

		Object selected = w.algorithmCombo.getSelectedItem();
		boolean showsNoise = selected instanceof DitherAlgorithm alg && alg.isShowsNoise();
		boolean showsErrRate = selected instanceof DitherAlgorithm alg2 && alg2.isShowsErrRate();

		w.ditherSettingsPanel.setVisible(showsNoise || showsErrRate);

		if (w.noiseLevelRow != null) {
			w.noiseLevelRow.setRowVisible(showsNoise);
		}

		if (w.errRateLinkButton != null) {
			w.errRateLinkButton.setVisible(showsErrRate);
		}

		boolean separate = w.errRateLinkButton != null && w.errRateLinkButton.isSelected();

		if (w.errRateStrengthPanel != null) {
			if (showsErrRate && !separate) {
				w.errRateStrengthPanel.expandInstant();
			} else {
				w.errRateStrengthPanel.collapseInstant();
			}
		}

		if (w.errRateRPanel != null) {
			if (showsErrRate && separate) {
				w.errRateRPanel.expandInstant();
			} else {
				w.errRateRPanel.collapseInstant();
			}
		}

		if (w.errRateRgbPanel != null) {
			if (showsErrRate && separate) {
				w.errRateRgbPanel.expandInstant();
			} else {
				w.errRateRgbPanel.collapseInstant();
			}
		}

		if (w.errRateLinkButton != null) {
			w.errRateLinkButton.syncVisualState();
		}

		notifyAccordionResized(w);
	}

	private static void notifyAccordionResized(MainWindow w) {
		if (w.ditheringAccordion != null) {
			w.ditheringAccordion.refreshContentSize();
		}
	}
}
