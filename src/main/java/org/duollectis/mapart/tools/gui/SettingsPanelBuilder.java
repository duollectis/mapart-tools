package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.ColorMetric;
import org.duollectis.mapart.tools.converter.DitherSettings;
import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.converter.StaircaseMode;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Строит левую колонку настроек MainWindow: версия, размер, алгоритм,
 * настройки дизеринга, коррекция изображения, пути к файлам, кнопки действий.
 */
class SettingsPanelBuilder {

	static JPanel buildSettingsPanel(MainWindow w) {
		// Контент с реализацией Scrollable — фиксирует ширину по viewport,
		// чтобы BoxLayout не растягивал панель горизонтально внутри JScrollPane.
		JPanel content = new ScrollablePanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));

		content.add(buildSectionLabel(Lang.t("section.version")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildVersionRow(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.size")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildSizeRow(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.algorithm")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildAlgorithmRow(w));
		content.add(Box.createVerticalStrut(4));
		content.add(buildColorMetricRow(w));
		content.add(Box.createVerticalStrut(4));
		content.add(buildDitherSettingsPanel(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.staircase")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildStaircaseModeRow(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.image_adjust")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildImageAdjustPanel(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.image")));
		content.add(Box.createVerticalStrut(5));
		w.imagePathField = buildTextField(Lang.t("placeholder.image"));
		content.add(buildFileRow(w.imagePathField, w.actions::chooseImage));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.blocks")));
		content.add(Box.createVerticalStrut(5));
		content.add(buildBlocksRow(w));

		content.add(Box.createVerticalStrut(12));
		content.add(buildSectionLabel(Lang.t("section.outdir")));
		content.add(Box.createVerticalStrut(5));
		w.outPathField = buildTextField(Lang.t("placeholder.outdir"));
		content.add(buildFileRow(w.outPathField, w.actions::chooseOutDir));

		JScrollPane scroll = new JScrollPane(
			content,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
		);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(12);
		scroll.getVerticalScrollBar().setUI(GuiApp.buildScrollBarUi());

		JPanel buttons = buildActionButtons(w);
		buttons.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));

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
		card.add(buttons, BorderLayout.SOUTH);

		return card;
	}

	private static JPanel buildVersionRow(MainWindow w) {
		w.versionCombo = new ModernComboBox<>(w.actions.loadVersions());

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(w.versionCombo, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private static JPanel buildSizeRow(MainWindow w) {
		w.widthSpinner = new ModernSpinner(new SpinnerNumberModel(1, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX, 1));
		w.heightSpinner = new ModernSpinner(new SpinnerNumberModel(1, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX, 1));

		w.widthSpinner.addChangeListener(e -> {
			w.actions.syncSourcePreviewMapCount();
			w.actions.scheduleConversionIfAuto();
		});

		w.heightSpinner.addChangeListener(e -> {
			w.actions.syncSourcePreviewMapCount();
			w.actions.scheduleConversionIfAuto();
		});

		JButton autoFitBtn = buildIconButton("⊡", 14, new Insets(2, 6, 2, 6), w);
		autoFitBtn.setToolTipText(Lang.t("btn.auto_fit_maps"));
		autoFitBtn.addActionListener(e -> w.actions.autoFitMapCount());

		JButton halvBtn = buildIconButton("÷2", 11, new Insets(2, 5, 2, 5), w);
		halvBtn.setToolTipText(Lang.t("btn.halve_maps"));
		halvBtn.addActionListener(e -> w.actions.scaleMapCount(0.5));

		JButton doubleBtn = buildIconButton("×2", 11, new Insets(2, 5, 2, 5), w);
		doubleBtn.setToolTipText(Lang.t("btn.double_maps"));
		doubleBtn.addActionListener(e -> w.actions.scaleMapCount(2.0));

		JPanel widthCol = new JPanel();
		widthCol.setLayout(new BoxLayout(widthCol, BoxLayout.Y_AXIS));
		widthCol.setOpaque(false);
		widthCol.add(dimLabel(Lang.t("label.width")));
		widthCol.add(Box.createVerticalStrut(3));
		widthCol.add(w.widthSpinner);

		JPanel heightCol = new JPanel();
		heightCol.setLayout(new BoxLayout(heightCol, BoxLayout.Y_AXIS));
		heightCol.setOpaque(false);
		heightCol.add(dimLabel(Lang.t("label.height")));
		heightCol.add(Box.createVerticalStrut(3));
		heightCol.add(w.heightSpinner);

		JPanel btnGroup = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
		btnGroup.setOpaque(false);
		btnGroup.add(halvBtn);
		btnGroup.add(doubleBtn);
		btnGroup.add(autoFitBtn);

		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(0, 0, 0, 6);

		gbc.gridx = 0;
		gbc.weightx = 1;
		row.add(widthCol, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.insets = new Insets(0, 6, 0, 6);
		row.add(heightCol, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(0, 0, 0, 0);
		row.add(btnGroup, gbc);

		return row;
	}

	private static JPanel buildImageAdjustPanel(MainWindow w) {
		ImageAdjustments defaults = ImageAdjustments.defaults();

		w.brightnessSlider = new ModernSlider(-100, 100, defaults.brightness());
		w.contrastSlider = new ModernSlider(-100, 100, defaults.contrast());
		w.saturationSlider = new ModernSlider(-100, 100, defaults.saturation());
		w.gammaSlider = new ModernSlider(10, 300, defaults.gamma());
		w.hueSlider = new ModernSlider(-180, 180, defaults.hue());

		w.brightnessLabel = buildSliderValueLabel(defaults.brightness(), false);
		w.contrastLabel = buildSliderValueLabel(defaults.contrast(), false);
		w.saturationLabel = buildSliderValueLabel(defaults.saturation(), false);
		w.gammaLabel = buildSliderValueLabel(defaults.gamma(), true);
		w.hueLabel = buildSliderValueLabel(defaults.hue(), false);

		w.brightnessSlider.addChangeListener(e -> {
			w.brightnessLabel.setText(formatSliderValue(w.brightnessSlider.getValue(), false));
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.contrastSlider.addChangeListener(e -> {
			w.contrastLabel.setText(formatSliderValue(w.contrastSlider.getValue(), false));
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.saturationSlider.addChangeListener(e -> {
			w.saturationLabel.setText(formatSliderValue(w.saturationSlider.getValue(), false));
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.gammaSlider.addChangeListener(e -> {
			w.gammaLabel.setText(formatSliderValue(w.gammaSlider.getValue(), true));
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.hueSlider.addChangeListener(e -> {
			w.hueLabel.setText(formatSliderValue(w.hueSlider.getValue(), false));
			w.actions.scheduleSourcePreview();
			w.actions.scheduleConversion();
		});

		w.autoConvertToggle = new ModernToggleButton(Lang.t("adjust.auto_convert"));

		JButton resetBtn = buildIconButton(Lang.t("adjust.reset"), w);
		resetBtn.addActionListener(e -> resetAdjustments(w));

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 0, 1, 4);

		addSliderRow(grid, gbc, 0, Lang.t("adjust.brightness"), w.brightnessSlider, w.brightnessLabel, defaults.brightness(), false, w);
		addSliderRow(grid, gbc, 1, Lang.t("adjust.contrast"), w.contrastSlider, w.contrastLabel, defaults.contrast(), false, w);
		addSliderRow(grid, gbc, 2, Lang.t("adjust.saturation"), w.saturationSlider, w.saturationLabel, defaults.saturation(), false, w);
		addSliderRow(grid, gbc, 3, Lang.t("adjust.gamma"), w.gammaSlider, w.gammaLabel, defaults.gamma(), true, w);
		addSliderRow(grid, gbc, 4, Lang.t("adjust.hue"), w.hueSlider, w.hueLabel, defaults.hue(), false, w);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(grid);
		wrapper.add(Box.createVerticalStrut(4));

		JButton convertNowBtn = buildIconButton(Lang.t("adjust.convert_now"), w);
		convertNowBtn.addActionListener(e -> w.actions.startConversion());

		JPanel controlRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
		controlRow.setOpaque(false);
		controlRow.add(w.autoConvertToggle);
		controlRow.add(resetBtn);
		controlRow.add(convertNowBtn);
		wrapper.add(controlRow);

		return wrapper;
	}

	static void addSliderRow(
		JPanel grid,
		GridBagConstraints gbc,
		int row,
		String label,
		ModernSlider slider,
		JLabel valueLabel,
		int defaultValue,
		boolean isGamma,
		MainWindow w
	) {
		gbc.gridy = row;

		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 6);
		JLabel nameLabel = dimLabel(label);
		nameLabel.setPreferredSize(new Dimension(80, 16));
		grid.add(nameLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.insets = new Insets(1, 0, 1, 4);
		grid.add(slider, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 4);
		valueLabel.setPreferredSize(new Dimension(36, 16));

		JButton decBtn = buildIconButton("◀", 9, new Insets(2, 4, 2, 4), w);
		JButton incBtn = buildIconButton("▶", 9, new Insets(2, 4, 2, 4), w);

		decBtn.addActionListener(e -> {
			int next = Math.max(slider.getMinimum(), slider.getValue() - 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});

		incBtn.addActionListener(e -> {
			int next = Math.min(slider.getMaximum(), slider.getValue() + 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});

		JPanel valueCell = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 2, 0));
		valueCell.setOpaque(false);
		valueCell.add(decBtn);
		valueCell.add(valueLabel);
		valueCell.add(incBtn);
		grid.add(valueCell, gbc);

		gbc.gridx = 3;
		gbc.weightx = 0;
		gbc.insets = new Insets(1, 0, 1, 0);
		JButton resetOne = buildIconButton("↺", w);
		resetOne.addActionListener(e -> {
			slider.setValue(defaultValue);
			valueLabel.setText(formatSliderValue(defaultValue, isGamma));
		});
		grid.add(resetOne, gbc);
	}

	private static JComponent buildAlgorithmRow(MainWindow w) {
		w.algorithmCombo = new ModernComboBox<>(buildAlgorithmModel());
		w.algorithmCombo.addActionListener(e -> {
			if (w.algorithmCombo.getSelectedItem() instanceof ModernComboBox.Separator) {
				return;
			}

			refreshDitherSettingsPanel(w);
			w.actions.scheduleConversionIfAuto();
		});

		return w.algorithmCombo;
	}

	private static DefaultComboBoxModel<Object> buildAlgorithmModel() {
		DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();

		model.addElement(new ModernComboBox.Separator(Lang.t("algorithm.group.none")));
		model.addElement(Ditherer.Algorithm.NONE);

		model.addElement(new ModernComboBox.Separator(Lang.t("algorithm.group.error_diffusion")));
		model.addElement(Ditherer.Algorithm.FLOYD_STEINBERG);
		model.addElement(Ditherer.Algorithm.FLOYD_STEINBERG_20);
		model.addElement(Ditherer.Algorithm.FLOYD_STEINBERG_24);
		model.addElement(Ditherer.Algorithm.STUCKI);
		model.addElement(Ditherer.Algorithm.JJN);
		model.addElement(Ditherer.Algorithm.BURKES);
		model.addElement(Ditherer.Algorithm.SIERRA3);
		model.addElement(Ditherer.Algorithm.SIERRA2);
		model.addElement(Ditherer.Algorithm.SIERRA_LITE);
		model.addElement(Ditherer.Algorithm.ATKINSON);
		model.addElement(Ditherer.Algorithm.FILTER_LITE);

		model.addElement(new ModernComboBox.Separator(Lang.t("algorithm.group.row_diffusion")));
		model.addElement(Ditherer.Algorithm.FAN);
		model.addElement(Ditherer.Algorithm.SHIAU_FAN);
		model.addElement(Ditherer.Algorithm.SHIAU_FAN_2);
		model.addElement(Ditherer.Algorithm.PIGEON);
		model.addElement(Ditherer.Algorithm.NAKANO);
		model.addElement(Ditherer.Algorithm.ZHOU_FANG);

		model.addElement(new ModernComboBox.Separator(Lang.t("algorithm.group.bayer")));
		model.addElement(Ditherer.Algorithm.BAYER_2X2);
		model.addElement(Ditherer.Algorithm.BAYER_3X3);
		model.addElement(Ditherer.Algorithm.BAYER_4X4);
		model.addElement(Ditherer.Algorithm.BAYER_8X8);
		model.addElement(Ditherer.Algorithm.BAYER_16X16);
		model.addElement(Ditherer.Algorithm.BAYER_32X32);

		model.addElement(new ModernComboBox.Separator(Lang.t("algorithm.group.ordered")));
		model.addElement(Ditherer.Algorithm.ORDERED_3X3);
		model.addElement(Ditherer.Algorithm.CLUSTERED_DOT);
		model.addElement(Ditherer.Algorithm.CLUSTERED_DOT_4X4);
		model.addElement(Ditherer.Algorithm.HALFTONE);
		model.addElement(Ditherer.Algorithm.VOID_AND_CLUSTER);
		model.addElement(Ditherer.Algorithm.VOID_AND_CLUSTER_14X14);
		model.addElement(Ditherer.Algorithm.DISPERSED_DOT_4X4);
		model.addElement(Ditherer.Algorithm.DISPERSED_DOT_8X8);
		model.addElement(Ditherer.Algorithm.MAGIC_SQUARE_5X5);
		model.addElement(Ditherer.Algorithm.BLUE_NOISE_16X16);

		model.setSelectedItem(Ditherer.Algorithm.FLOYD_STEINBERG);

		return model;
	}

	private static JComponent buildStaircaseModeRow(MainWindow w) {
		w.staircaseModeCombo = new ModernComboBox<>(buildStaircaseModel());
		w.staircaseModeCombo.setSelectedItem(StaircaseMode.VALLEY);
		w.staircaseModeCombo.addActionListener(e -> {
			if (w.staircaseModeCombo.getSelectedItem() instanceof ModernComboBox.Separator) {
				return;
			}

			w.actions.scheduleConversionIfAuto();
		});

		return w.staircaseModeCombo;
	}

	private static DefaultComboBoxModel<Object> buildStaircaseModel() {
		DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();

		model.addElement(new ModernComboBox.Separator(Lang.t("staircase.group.staircase")));
		model.addElement(StaircaseMode.STAIRCASE);
		model.addElement(StaircaseMode.VALLEY);
		model.addElement(StaircaseMode.UPWARDS_ONLY);
		model.addElement(StaircaseMode.REVERSE_UPWARDS_ONLY);

		model.addElement(new ModernComboBox.Separator(Lang.t("staircase.group.single_tone")));
		model.addElement(StaircaseMode.DARK);
		model.addElement(StaircaseMode.LIGHT);

		model.addElement(new ModernComboBox.Separator(Lang.t("staircase.group.flat")));
		model.addElement(StaircaseMode.FLAT);
		model.addElement(StaircaseMode.FLAT_DARK);
		model.addElement(StaircaseMode.FLAT_LIGHT);

		return model;
	}

	private static JPanel buildColorMetricRow(MainWindow w) {
		w.colorMetricCombo = new ModernComboBox<>(buildColorMetricModel());
		w.colorMetricCombo.addActionListener(e -> {
			if (w.colorMetricCombo.getSelectedItem() instanceof ModernComboBox.Separator) {
				return;
			}

			w.actions.scheduleConversionIfAuto();
		});

		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(dimLabel(Lang.t("dither.color_metric")), BorderLayout.WEST);
		row.add(w.colorMetricCombo, BorderLayout.CENTER);

		return row;
	}

	private static DefaultComboBoxModel<Object> buildColorMetricModel() {
		DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.lab")));
		model.addElement(ColorMetric.LAB);
		model.addElement(ColorMetric.CIEDE2000);
		model.addElement(ColorMetric.LAB_D50);
		model.addElement(ColorMetric.CIEDE2000_D50);

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.oklab")));
		model.addElement(ColorMetric.OKLAB);
		model.addElement(ColorMetric.OKLAB_CHROMA);

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.perceptual")));
		model.addElement(ColorMetric.HCT);
		model.addElement(ColorMetric.IPT);
		model.addElement(ColorMetric.JZAZBZ);

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.rgb")));
		model.addElement(ColorMetric.RGB);
		model.addElement(ColorMetric.WEIGHTED_RGB);

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.cylindrical")));
		model.addElement(ColorMetric.HSL);
		model.addElement(ColorMetric.HSV);

		model.addElement(new ModernComboBox.Separator(Lang.t("metric.group.video")));
		model.addElement(ColorMetric.YUV);
		model.addElement(ColorMetric.YCBCR);

		model.setSelectedItem(ColorMetric.LAB);

		return model;
	}

	private static JPanel buildDitherSettingsPanel(MainWindow w) {
		DitherSettings defaults = DitherSettings.defaults();
		int defaultNoiseLevel = (int) (defaults.noiseLevel() * 100);
		int defaultErrRateChannel = (int) (defaults.errRateR() * 100);

		w.noiseLevelSlider = new ModernSlider(0, 100, defaultNoiseLevel);
		w.errRateRSlider = new ModernSlider(0, 200, defaultErrRateChannel);
		w.errRateGSlider = new ModernSlider(0, 200, defaultErrRateChannel);
		w.errRateBSlider = new ModernSlider(0, 200, defaultErrRateChannel);

		w.noiseLevelLabel = buildSliderValueLabel(defaultNoiseLevel, true);
		w.errRateRLabel = buildSliderValueLabel(defaultErrRateChannel, true);
		w.errRateGLabel = buildSliderValueLabel(defaultErrRateChannel, true);
		w.errRateBLabel = buildSliderValueLabel(defaultErrRateChannel, true);

		w.errRateLinkButton = buildLinkToggleButton(w);

		// Флаг для предотвращения рекурсии при синхронизации
		boolean[] syncing = {false};

		w.noiseLevelSlider.addChangeListener(e -> {
			w.noiseLevelLabel.setText(formatSliderValue(w.noiseLevelSlider.getValue(), true));
			w.actions.scheduleConversionIfAuto();
		});

		w.errRateRSlider.addChangeListener(e -> {
			w.errRateRLabel.setText(formatSliderValue(w.errRateRSlider.getValue(), true));

			if (w.errRateLinkButton.isSelected() && !syncing[0]) {
				syncing[0] = true;
				int val = w.errRateRSlider.getValue();
				w.errRateGSlider.setValue(val);
				w.errRateBSlider.setValue(val);
				syncing[0] = false;
			}

			w.actions.scheduleConversionIfAuto();
		});

		w.errRateGSlider.addChangeListener(e -> {
			w.errRateGLabel.setText(formatSliderValue(w.errRateGSlider.getValue(), true));

			if (w.errRateLinkButton.isSelected() && !syncing[0]) {
				syncing[0] = true;
				int val = w.errRateGSlider.getValue();
				w.errRateRSlider.setValue(val);
				w.errRateBSlider.setValue(val);
				syncing[0] = false;
			}

			w.actions.scheduleConversionIfAuto();
		});

		w.errRateBSlider.addChangeListener(e -> {
			w.errRateBLabel.setText(formatSliderValue(w.errRateBSlider.getValue(), true));

			if (w.errRateLinkButton.isSelected() && !syncing[0]) {
				syncing[0] = true;
				int val = w.errRateBSlider.getValue();
				w.errRateRSlider.setValue(val);
				w.errRateGSlider.setValue(val);
				syncing[0] = false;
			}

			w.actions.scheduleConversionIfAuto();
		});

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 0, 1, 4);

		addSliderRow(grid, gbc, 0, Lang.t("dither.noise_level"), w.noiseLevelSlider, w.noiseLevelLabel, defaultNoiseLevel, true, w);
		addSliderRow(grid, gbc, 1, Lang.t("dither.err_rate_r"), w.errRateRSlider, w.errRateRLabel, defaultErrRateChannel, true, w);
		addSliderRow(grid, gbc, 2, Lang.t("dither.err_rate_g"), w.errRateGSlider, w.errRateGLabel, defaultErrRateChannel, true, w);
		addSliderRow(grid, gbc, 3, Lang.t("dither.err_rate_b"), w.errRateBSlider, w.errRateBLabel, defaultErrRateChannel, true, w);

		// Панель с кнопкой-замком и визуальной линией справа от слайдеров
		JPanel linkPanel = buildErrRateLinkPanel(w);

		JPanel channelsRow = new JPanel(new BorderLayout(4, 0));
		channelsRow.setOpaque(false);
		channelsRow.add(grid, BorderLayout.CENTER);
		channelsRow.add(linkPanel, BorderLayout.EAST);

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(channelsRow);

		w.ditherSettingsPanel = wrapper;

		refreshDitherSettingsPanel(w);

		return wrapper;
	}

	/**
	 * Строит боковую панель с кнопкой-замком для синхронизации R/G/B слайдеров.
	 * Когда замок включён — рисует вертикальную линию, визуально связывающую три слайдера.
	 */
	private static JPanel buildErrRateLinkPanel(MainWindow w) {
		JPanel panel = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				if (!w.errRateLinkButton.isSelected()) {
					return;
				}

				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(MainWindow.ACCENT());
				g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

				int cx = getWidth() / 2;
				int btnH = w.errRateLinkButton.getHeight();
				int btnY = w.errRateLinkButton.getY();
				int lineTop = 2;
				int lineBottom = getHeight() - 2;

				// Линия выше кнопки
				if (btnY > lineTop) {
					g2.drawLine(cx, lineTop, cx, btnY);
				}

				// Линия ниже кнопки
				int btnBottom = btnY + btnH;
				if (btnBottom < lineBottom) {
					g2.drawLine(cx, btnBottom, cx, lineBottom);
				}

				g2.dispose();
			}
		};
		panel.setOpaque(false);
		panel.add(w.errRateLinkButton, BorderLayout.CENTER);

		w.errRateLinkButton.addActionListener(e -> panel.repaint());

		return panel;
	}

	private static ModernToggleButton buildLinkToggleButton(MainWindow w) {
		ModernToggleButton btn = new ModernToggleButton("🔗");
		btn.setToolTipText(Lang.t("dither.err_rate_link"));
		btn.setFont(new Font("SansSerif", Font.PLAIN, 13));
		btn.setPreferredSize(new Dimension(28, 28));
		btn.setMaximumSize(new Dimension(28, 28));
		btn.setMinimumSize(new Dimension(28, 28));

		return btn;
	}

	private static final Set<Ditherer.Algorithm> ERROR_DIFFUSION_ALGORITHMS = EnumSet.of(
		Ditherer.Algorithm.FLOYD_STEINBERG,
		Ditherer.Algorithm.FLOYD_STEINBERG_20,
		Ditherer.Algorithm.FLOYD_STEINBERG_24,
		Ditherer.Algorithm.STUCKI,
		Ditherer.Algorithm.JJN,
		Ditherer.Algorithm.BURKES,
		Ditherer.Algorithm.SIERRA3,
		Ditherer.Algorithm.SIERRA2,
		Ditherer.Algorithm.SIERRA_LITE,
		Ditherer.Algorithm.ATKINSON,
		Ditherer.Algorithm.FILTER_LITE,
		Ditherer.Algorithm.FAN,
		Ditherer.Algorithm.SHIAU_FAN,
		Ditherer.Algorithm.SHIAU_FAN_2,
		Ditherer.Algorithm.PIGEON,
		Ditherer.Algorithm.NAKANO,
		Ditherer.Algorithm.ZHOU_FANG
	);

	static void refreshDitherSettingsPanel(MainWindow w) {
		if (w.ditherSettingsPanel == null) {
			return;
		}

		Object raw = w.algorithmCombo.getSelectedItem();

		if (raw instanceof ModernComboBox.Separator) {
			return;
		}

		if (!(raw instanceof Ditherer.Algorithm selected) || selected == Ditherer.Algorithm.NONE) {
			w.ditherSettingsPanel.setVisible(false);
			return;
		}

		boolean isErrorDiffusion = ERROR_DIFFUSION_ALGORITHMS.contains(selected);

		w.ditherSettingsPanel.setVisible(true);
		setSliderRowVisible(w.errRateRSlider, w.errRateRLabel, isErrorDiffusion);
		setSliderRowVisible(w.errRateGSlider, w.errRateGLabel, isErrorDiffusion);
		setSliderRowVisible(w.errRateBSlider, w.errRateBLabel, isErrorDiffusion);
	}

	static void setSliderRowVisible(ModernSlider slider, JLabel valueLabel, boolean visible) {
		// Находим все компоненты в той же строке GridBagLayout и переключаем видимость
		java.awt.Container parent = slider.getParent();

		if (parent == null) {
			return;
		}

		java.awt.GridBagLayout layout = (java.awt.GridBagLayout) parent.getLayout();
		int targetRow = layout.getConstraints(slider).gridy;

		for (java.awt.Component comp : parent.getComponents()) {
			java.awt.GridBagConstraints c = layout.getConstraints(comp);

			if (c.gridy == targetRow) {
				comp.setVisible(visible);
			}
		}
	}

	private static JPanel buildBlocksRow(MainWindow w) {
		w.blocksPathField = buildTextField(Lang.t("placeholder.blocks"));

		w.blocksCountLabel = new JLabel(Lang.t("label.blocks_not_selected"));
		w.blocksCountLabel.setForeground(MainWindow.TEXT_DIM());
		w.blocksCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		w.blocksCountLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

		w.pickBlocksButton = buildAccentButton(
			Lang.t("btn.pick_blocks"),
			new Color(34, 85, 34),
			new Color(100, 210, 130)
		);
		w.pickBlocksButton.addActionListener(e -> w.actions.openBlockPicker());

		JButton browseBtn = buildIconButton("...", w);
		browseBtn.addActionListener(e -> w.actions.chooseBlocks());

		JPanel fileRow = new JPanel(new BorderLayout(4, 0));
		fileRow.setOpaque(false);
		fileRow.add(w.blocksPathField, BorderLayout.CENTER);
		fileRow.add(browseBtn, BorderLayout.EAST);
		fileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JPanel buttonRow = new JPanel(new BorderLayout(8, 0));
		buttonRow.setOpaque(false);
		buttonRow.add(w.pickBlocksButton, BorderLayout.CENTER);
		buttonRow.add(w.blocksCountLabel, BorderLayout.EAST);
		buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.add(fileRow);
		wrapper.add(Box.createVerticalStrut(5));
		wrapper.add(buttonRow);

		return wrapper;
	}

	static JPanel buildFileRow(JTextField field, Runnable onBrowse) {
		JButton browseBtn = new JButton("...") {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				if (getModel().isRollover()) {
					g2.setColor(new Color(255, 255, 255, 20));
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				}

				g2.dispose();
				super.paintComponent(g);
			}
		};
		browseBtn.setForeground(MainWindow.TEXT_DIM());
		browseBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
		browseBtn.setFocusPainted(false);
		browseBtn.setContentAreaFilled(false);
		browseBtn.setBorderPainted(false);
		browseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		browseBtn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
		browseBtn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				browseBtn.setForeground(MainWindow.TEXT());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				browseBtn.setForeground(MainWindow.TEXT_DIM());
			}
		});
		browseBtn.addActionListener(e -> onBrowse.run());

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(field, BorderLayout.CENTER);
		row.add(browseBtn, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		return row;
	}

	private static JPanel buildActionButtons(MainWindow w) {
		w.convertButton = buildPrimaryButton(Lang.t("btn.convert"), MainWindow.ACCENT(), MainWindow.BG());
		w.convertButton.addActionListener(e -> w.actions.startConversion());

		w.formatCombo = new ModernComboBox<>(SchematicFormat.values());
		w.formatCombo.addActionListener(e -> w.actions.syncExportButtonLabel());

		w.exportButton = buildAccentButton(Lang.t("btn.export_nbt"), new Color(60, 45, 10), MainWindow.WARN());
		w.exportButton.setEnabled(false);
		w.exportButton.addActionListener(e -> w.actions.startExport());

		w.blockListButton = buildAccentButton(Lang.t("btn.block_list"), new Color(20, 35, 60), new Color(130, 180, 240));
		w.blockListButton.setEnabled(false);
		w.blockListButton.addActionListener(e -> w.actions.openBlockList());

		w.importButton = buildAccentButton(Lang.t("btn.import_schematic"), new Color(35, 20, 55), new Color(180, 140, 240));
		w.importButton.addActionListener(e -> w.actions.startImport());

		w.convertButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
		w.exportButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
		w.blockListButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
		w.importButton.setAlignmentX(JButton.LEFT_ALIGNMENT);

		w.convertButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.convertButton.getPreferredSize().height + 4));
		w.exportButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.exportButton.getPreferredSize().height + 4));
		w.blockListButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.blockListButton.getPreferredSize().height + 4));
		w.importButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.importButton.getPreferredSize().height + 4));
		w.formatCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JLabel formatLabel = dimLabel(Lang.t("label.format"));

		JPanel formatRow = new JPanel(new BorderLayout(6, 0));
		formatRow.setOpaque(false);
		formatRow.add(formatLabel, BorderLayout.WEST);
		formatRow.add(w.formatCombo, BorderLayout.CENTER);
		formatRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		formatRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.add(w.convertButton);
		panel.add(Box.createVerticalStrut(6));
		panel.add(formatRow);
		panel.add(Box.createVerticalStrut(4));
		panel.add(w.exportButton);
		panel.add(Box.createVerticalStrut(4));
		panel.add(w.blockListButton);
		panel.add(Box.createVerticalStrut(8));
		panel.add(w.importButton);

		return panel;
	}

	// ── UI-фабрики ─────────────────────────────────────────────────────────────

	static JLabel buildSectionLabel(String text) {
		JLabel label = new JLabel(text.toUpperCase());
		label.setForeground(MainWindow.TEXT_DIM());
		label.setFont(new Font("SansSerif", Font.BOLD, 10));
		label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel dimLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(MainWindow.TEXT_DIM());
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
					g2.setColor(MainWindow.TEXT_DIM());
					g2.setFont(getFont().deriveFont(Font.ITALIC));
					Insets insets = getInsets();
					g2.drawString(placeholder, insets.left, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 1);
					g2.dispose();
				}
			}
		};

		field.setBackground(MainWindow.INPUT());
		field.setForeground(MainWindow.TEXT());
		field.setCaretColor(MainWindow.ACCENT());
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(MainWindow.BORDER(), 1, true),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		field.setFont(new Font("SansSerif", Font.PLAIN, 13));

		return field;
	}

	static JButton buildPrimaryButton(String text, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		btn.setBackground(MainWindow.ACCENT());
		btn.setForeground(Color.WHITE);
		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	static JButton buildAccentButton(String text, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		btn.setBackground(MainWindow.CARD());
		btn.setForeground(MainWindow.TEXT());
		btn.setFont(new Font("SansSerif", Font.PLAIN, 13));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	static JButton buildIconButton(String tooltip, MainWindow w) {
		return buildIconButton(tooltip, "≡", w);
	}

	static JButton buildIconButton(String tooltip, String icon, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton(icon) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		btn.setToolTipText(tooltip);
		btn.setBackground(MainWindow.CARD());
		btn.setForeground(MainWindow.TEXT());
		btn.setFont(new Font("SansSerif", Font.PLAIN, 16));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	static JLabel buildSliderValueLabel(int value) {
		JLabel label = new JLabel(formatSliderValue(value));
		label.setForeground(MainWindow.ACCENT());
		label.setFont(new Font("SansSerif", Font.BOLD, 12));
		label.setPreferredSize(new Dimension(38, 16));
		label.setHorizontalAlignment(SwingConstants.RIGHT);

		return label;
	}

	static String formatSliderValue(int value) {
		return value >= 0 ? "+" + value : String.valueOf(value);
	}

	static JLabel buildSliderValueLabel(int value, boolean isPercent) {
		String text = isPercent ? value + "%" : formatSliderValue(value);
		JLabel label = new JLabel(text);
		label.setForeground(MainWindow.ACCENT());
		label.setFont(new Font("SansSerif", Font.BOLD, 12));
		label.setPreferredSize(new Dimension(38, 16));
		label.setHorizontalAlignment(SwingConstants.RIGHT);

		return label;
	}

	static String formatSliderValue(int value, boolean isPercent) {
		return isPercent ? value + "%" : formatSliderValue(value);
	}

	static JButton buildIconButton(String icon, int fontSize, Insets insets, MainWindow w) {
		UiAnimator.RippleState[] ripple = {null};

		JButton btn = new JButton(icon) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				UiAnimator.paintRipple(g2, ripple[0], getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				ripple[0] = UiAnimator.startRipple(e.getX(), e.getY(), btn);
			}
		});

		btn.setBackground(MainWindow.CARD());
		btn.setForeground(MainWindow.TEXT());
		btn.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
		w.actions.addHoverEffect(btn);

		return btn;
	}

	static JButton buildPrimaryButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setBackground(bgColor);
		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

		return btn;
	}

	static JButton buildAccentButton(String text, Color bgColor, Color fgColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};

		btn.setBackground(bgColor);
		btn.setForeground(fgColor);
		btn.setFont(new Font("SansSerif", Font.PLAIN, 13));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

		return btn;
	}

	static void resetAdjustments(MainWindow w) {
		ImageAdjustments defaults = ImageAdjustments.defaults();
		w.brightnessSlider.setValue(defaults.brightness());
		w.contrastSlider.setValue(defaults.contrast());
		w.saturationSlider.setValue(defaults.saturation());
		w.gammaSlider.setValue(defaults.gamma());
		w.hueSlider.setValue(defaults.hue());
	}
}