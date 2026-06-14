package org.duollectis.mapart.tools.gui.window;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.app.AppPreferences;
import org.duollectis.mapart.tools.app.AppPreferences.LayerState;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.widget.ImageLayer;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Отвечает за сохранение и восстановление пользовательских настроек {@link MainWindow}.
 * Читает/пишет через {@link AppPreferences}, фильтрует устаревшие данные.
 */
class MainWindowPreferences {

	private final MainWindow w;

	MainWindowPreferences(MainWindow window) {
		w = window;
	}

	/**
	 * Восстанавливает настройки из файла. После загрузки палитры фильтрует
	 * сохранённые блоки опоры — удаляет ID которых нет в текущей версии.
	 */
	void restorePreferences() {
		UiStateRegistry.restoreWindow("main_window", w, false);

		String savedVersion = AppPreferences.loadVersion(null);

		if (savedVersion != null) {
			w.versionCombo.setSelectedItem(savedVersion);
		}

		w.mapSizeControl.setMapWidth(AppPreferences.loadMapWidth(1));
		w.mapSizeControl.setMapHeight(AppPreferences.loadMapHeight(1));

		String savedAlgorithm = AppPreferences.loadAlgorithm(null);

		if (savedAlgorithm != null) {
			try {
				w.algorithmCombo.setSelectedItem(DitherAlgorithm.valueOf(savedAlgorithm));
			} catch (IllegalArgumentException ignored) {
				// неизвестный алгоритм — оставляем дефолт
			}
		}

		// Авто-режим восстанавливаем ДО слайдеров, чтобы ChangeListeners не триггерили конвертацию
		w.autoConvertToggle.setSelected(AppPreferences.loadAutoConvert(false));

		DitherSettings savedDither = AppPreferences.loadDitherSettings();
		double savedStrength = AppPreferences.loadErrRateStrength(savedDither.errRateR());

		if (w.errRateStrengthRow != null) {
			w.errRateStrengthRow.setValue((int) (savedStrength * 100));
		}

		if (w.errRateRRow != null) {
			w.errRateRRow.setValue((int) (savedDither.errRateR() * 100));
		}

		if (w.errRateGRow != null) {
			w.errRateGRow.setValue((int) (savedDither.errRateG() * 100));
		}

		if (w.errRateBRow != null) {
			w.errRateBRow.setValue((int) (savedDither.errRateB() * 100));
		}

		if (w.errRateLinkButton != null) {
			w.errRateLinkButton.setSelected(AppPreferences.loadErrRateLinked());
		}

		if (w.noiseLevelRow != null) {
			w.noiseLevelRow.setValue((int) (savedDither.noiseLevel() * 100));
		}

		if (w.colorMetricCombo != null) {
			w.colorMetricCombo.setSelectedItem(savedDither.colorMetric());
		}

		ImageAdjustments defaults = ImageAdjustments.defaults();
		w.brightnessRow.setValue(AppPreferences.loadBrightness(defaults.brightness()));
		w.contrastRow.setValue(AppPreferences.loadContrast(defaults.contrast()));
		w.saturationRow.setValue(AppPreferences.loadSaturation(defaults.saturation()));
		w.gammaRow.setValue(AppPreferences.loadGamma(defaults.gamma()));
		w.hueRow.setValue(AppPreferences.loadHue(defaults.hue()));

		restoreLayers();

		String blocksPath = AppPreferences.loadBlocksPath();

		if (!blocksPath.isBlank()) {
			w.blocksPathField.setText(blocksPath);
			java.io.File blocksFile = new java.io.File(blocksPath);

			if (blocksFile.exists() && blocksFile.isFile()) {
				w.actions.loadBlocksFromFile(blocksFile);
			}
		}

		w.outPathField.setText(AppPreferences.loadOutPath("./rendered"));

		SchematicFormat savedFormat = AppPreferences.loadSchematicFormat();

		if (w.formatCombo != null) {
			w.formatCombo.setSelectedItem(savedFormat);
			w.actions.syncExportButtonLabel();
		}

		restoreUiState();
		DitheringsSectionBuilder.refreshDitherSettingsPanel(w);

		String version = (String) w.versionCombo.getSelectedItem();
		Map<String, BlockData> paletteBlocks = parsePaletteBlocks(version);

		SupportBlockSettings loaded = AppPreferences.loadSupportSettings(MainWindow.DEFAULT_SUPPORT_BLOCK);

		if (loaded != null) {
			Set<String> validSupportIds = paletteBlocks.values().stream()
				.filter(b -> !b.isNeedSupport())
				.map(BlockData::getId)
				.collect(Collectors.toSet());
			w.supportSettings = loaded.filtered(validSupportIds);
		}

		w.blockSelectors = new HashMap<>(AppPreferences.loadBlockSelectors(paletteBlocks));
	}

	/**
		* Восстанавливает визуальное состояние UI из {@code ui_state.json}:
		* аккордеоны, тоглы, цвет фона, слайдер размытия.
		* Вызывается после загрузки бизнес-настроек, чтобы не конкурировать с ними.
		*/
	private void restoreUiState() {
		UiStateRegistry.restoreAccordion("accordion.app_settings", w.appSettingsAccordion, false);
		UiStateRegistry.restoreAccordion("accordion.image", w.imageAccordion, false);
		UiStateRegistry.restoreAccordion("accordion.blocks", w.blocksAccordion, false);
		UiStateRegistry.restoreAccordion("accordion.dithering", w.ditheringAccordion, false);
		UiStateRegistry.restoreAccordion("accordion.import", w.importAccordion, false);
		UiStateRegistry.restoreAccordion("accordion.export", w.exportAccordion, false);

		UiStateRegistry.restoreToggle("preview.snap", w.snapButton, false);
		UiStateRegistry.restoreToggle("preview.show_grid", w.showGridButton, false);

		Color savedBgColor = UiStateRegistry.restoreColor("preview.bg_color_rgb", null);

		if (savedBgColor != null) {
			w.sourcePreview.setGridBackgroundColor(savedBgColor);
			w.sourcePreview.setShowGridBackground(true);
		}

		UiStateRegistry.restoreSlider("preview.blur", w.blurSlider, 0);

		double blurRadius = w.blurSlider.getValue() / 100.0 * ImagePreviewPanel.MAX_BLUR_RADIUS;
		w.blurLabel.setText(String.format("%.1f", blurRadius));
		w.resultPreview.setBlurRadius(blurRadius);

		boolean showGrid = w.showGridButton.isSelected();
		w.sourcePreview.setShowGrid(showGrid);
		w.resultPreview.setShowGrid(showGrid);
		w.sourcePreview.setSnapEnabled(w.snapButton.isSelected());

		Color snapIconColor = w.snapButton.isSelected() ? MainWindow.ACCENT() : MainWindow.TEXT_DIM();
		w.snapButton.setIcon(AppIcon.MAGNET.colored(snapIconColor));
		w.snapButton.syncVisualState();

		Color gridIconColor = w.showGridButton.isSelected() ? MainWindow.ACCENT() : MainWindow.TEXT_DIM();
		w.showGridButton.setIcon(AppIcon.GRID.colored(gridIconColor));
		w.showGridButton.syncVisualState();
	}

	private void restoreLayers() {
		List<LayerState> states = AppPreferences.loadLayerStates();

		if (states.isEmpty()) {
			restoreLegacyImagePath();
			return;
		}

		for (LayerState state : states) {
			File file = new File(state.path());

			if (!file.exists() || !file.isFile()) {
				continue;
			}

			try {
				BufferedImage image = ImageIO.read(file);

				if (image == null) {
					continue;
				}

				w.sourcePreview.addLayerNormalized(
					image,
					state.name(),
					state.visible(),
					state.normW(),
					state.normH(),
					state.normOffsetX(),
					state.normOffsetY()
				);
				w.sourcePreview.setActiveLayerSourcePath(file.getAbsolutePath());
			} catch (IOException e) {
				System.err.println("Не удалось загрузить слой: " + file.getAbsolutePath() + " — " + e.getMessage());
			}
		}

		int savedActive = AppPreferences.loadActiveLayerIndex(0);
		w.sourcePreview.setActiveLayerIndex(savedActive);

		if (!w.sourcePreview.getLayers().isEmpty()) {
			w.selectedImageFile = new File(states.get(w.sourcePreview.getActiveLayerIndex()).path());
			w.actions.scheduleSourcePreview();
		}
	}

	private void restoreLegacyImagePath() {
		String imagePath = AppPreferences.loadImagePath();

		if (imagePath.isBlank()) {
			return;
		}

		File imageFile = new File(imagePath);

		if (!imageFile.exists() || !imageFile.isFile()) {
			return;
		}

		try {
			BufferedImage image = ImageIO.read(imageFile);

			if (image == null) {
				return;
			}

			w.selectedImageFile = imageFile;
			w.rawSourceImage = image;
			w.sourcePreview.addLayer(image, imageFile.getName());
			w.sourcePreview.setActiveLayerSourcePath(imageFile.getAbsolutePath());
			w.actions.scheduleSourcePreview();
		} catch (IOException e) {
			System.err.println("Не удалось загрузить изображение: " + imageFile.getAbsolutePath() + " — " + e.getMessage());
		}
	}

	void savePreferences() {
		AppPreferences.saveVersion((String) w.versionCombo.getSelectedItem());
		AppPreferences.saveMapWidth(w.mapSizeControl.getMapWidth());
		AppPreferences.saveMapHeight(w.mapSizeControl.getMapHeight());
		Object selectedAlgorithm = w.algorithmCombo.getSelectedItem();

		if (selectedAlgorithm instanceof DitherAlgorithm algorithm) {
			AppPreferences.saveAlgorithm(algorithm.name());
		}
		AppPreferences.saveBlocksPath(w.blocksPathField.getText().strip());
		AppPreferences.saveOutPath(w.outPathField.getText().strip());
		AppPreferences.saveSupportSettings(w.supportSettings);
		AppPreferences.saveBrightness(w.brightnessRow.getValue());
		AppPreferences.saveContrast(w.contrastRow.getValue());
		AppPreferences.saveSaturation(w.saturationRow.getValue());
		AppPreferences.saveGamma(w.gammaRow.getValue());
		AppPreferences.saveHue(w.hueRow.getValue());
		AppPreferences.saveAutoConvert(w.autoConvertToggle.isSelected());
		saveDitherSliders();

		if (w.errRateLinkButton != null) {
			AppPreferences.saveErrRateLinked(w.errRateLinkButton.isSelected());
		}

		saveLayerStates();
		AppPreferences.saveBlockSelectors(w.blockSelectors);

		if (w.formatCombo != null) {
			AppPreferences.saveSchematicFormat((SchematicFormat) w.formatCombo.getSelectedItem());
		}
	}

	Map<String, BlockData> parsePaletteBlocks(String version) {
		try {
			String paletteJson = w.actions.loadPaletteJson(version);
			Map<Integer, List<BlockData>> parsed = JsonHelper.GSON.fromJson(
				paletteJson,
				new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
			);

			return parsed.values().stream()
				.flatMap(List::stream)
				.collect(Collectors.toMap(BlockData::getUniqueKey, b -> b, (a, b) -> a));
		} catch (Exception ignored) {
			return Map.of();
		}
	}

	private void saveDitherSliders() {
		DitherSettings defaults = DitherSettings.defaults();
		double errRateR = w.errRateRRow != null ? w.errRateRRow.getValue() / 100.0 : defaults.errRateR();
		double errRateG = w.errRateGRow != null ? w.errRateGRow.getValue() / 100.0 : defaults.errRateG();
		double errRateB = w.errRateBRow != null ? w.errRateBRow.getValue() / 100.0 : defaults.errRateB();
		double noiseLevel = w.noiseLevelRow != null ? w.noiseLevelRow.getValue() / 100.0 : defaults.noiseLevel();
		ColorMetric metric = w.colorMetricCombo != null
			? (ColorMetric) w.colorMetricCombo.getSelectedItem()
			: defaults.colorMetric();

		AppPreferences.saveDitherSettings(new DitherSettings(errRateR, errRateG, errRateB, noiseLevel, metric));

		if (w.errRateStrengthRow != null) {
			AppPreferences.saveErrRateStrength(w.errRateStrengthRow.getValue() / 100.0);
		}
	}

	private void saveLayerStates() {
		List<ImageLayer> layers = w.sourcePreview.getLayers();
		int[] grid = w.sourcePreview.getGridBounds();
		int gridW = grid[2];
		int gridH = grid[3];

		if (gridW == 0 || gridH == 0) {
			return;
		}

		List<LayerState> states = new ArrayList<>();

		for (ImageLayer layer : layers) {
			String path = layer.getSourcePath();

			if (path == null || path.isBlank()) {
				continue;
			}

			double normW = layer.getScaleX() * layer.getImage().getWidth() / gridW;
			double normH = layer.getScaleY() * layer.getImage().getHeight() / gridH;
			double normOffsetX = layer.getOffsetX() / gridW;
			double normOffsetY = layer.getOffsetY() / gridH;

			states.add(new LayerState(
				path,
				layer.getName(),
				layer.isVisible(),
				normW,
				normH,
				normOffsetX,
				normOffsetY
			));
		}

		AppPreferences.saveLayerStates(states, w.sourcePreview.getActiveLayerIndex());
	}

	Set<String> loadValidSupportIds(String version) {
		return parsePaletteBlocks(version).values().stream()
			.filter(b -> !b.isNeedSupport())
			.map(BlockData::getId)
			.collect(Collectors.toSet());
	}
}
