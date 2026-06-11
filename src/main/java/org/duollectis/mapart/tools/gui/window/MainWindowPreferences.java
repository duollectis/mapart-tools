package org.duollectis.mapart.tools.gui.window;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.gui.AppPreferences;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

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
		String savedVersion = AppPreferences.loadVersion(null);

		if (savedVersion != null) {
			w.versionCombo.setSelectedItem(savedVersion);
		}

		w.widthSpinner.setValue(AppPreferences.loadMapWidth(1));
		w.heightSpinner.setValue(AppPreferences.loadMapHeight(1));

		String savedAlgorithm = AppPreferences.loadAlgorithm(null);

		if (savedAlgorithm != null) {
			try {
				w.algorithmCombo.setSelectedItem(Ditherer.Algorithm.valueOf(savedAlgorithm));
			} catch (IllegalArgumentException ignored) {
				// неизвестный алгоритм — оставляем дефолт
			}
		}

		// Авто-режим восстанавливаем ДО слайдеров, чтобы ChangeListeners не триггерили конвертацию
		w.autoConvertToggle.setSelected(AppPreferences.loadAutoConvert(false));

		DitherSettings savedDither = AppPreferences.loadDitherSettings();

		if (w.errRateRSlider != null) {
			w.errRateRSlider.setValue((int) (savedDither.errRateR() * 100));
		}

		if (w.errRateGSlider != null) {
			w.errRateGSlider.setValue((int) (savedDither.errRateG() * 100));
		}

		if (w.errRateBSlider != null) {
			w.errRateBSlider.setValue((int) (savedDither.errRateB() * 100));
		}

		if (w.errRateLinkButton != null) {
			w.errRateLinkButton.setSelected(AppPreferences.loadErrRateLinked());
			w.errRateLinkButton.syncVisualState();
		}

		if (w.noiseLevelSlider != null) {
			w.noiseLevelSlider.setValue((int) (savedDither.noiseLevel() * 100));
		}

		if (w.colorMetricCombo != null) {
			w.colorMetricCombo.setSelectedItem(savedDither.colorMetric());
		}

		ImageAdjustments defaults = ImageAdjustments.defaults();
		w.brightnessSlider.setValue(AppPreferences.loadBrightness(defaults.brightness()));
		w.contrastSlider.setValue(AppPreferences.loadContrast(defaults.contrast()));
		w.saturationSlider.setValue(AppPreferences.loadSaturation(defaults.saturation()));
		w.gammaSlider.setValue(AppPreferences.loadGamma(defaults.gamma()));
		w.hueSlider.setValue(AppPreferences.loadHue(defaults.hue()));

		String imagePath = AppPreferences.loadImagePath();

		if (!imagePath.isBlank()) {
			w.imagePathField.setText(imagePath);
			java.io.File imageFile = new java.io.File(imagePath);

			if (imageFile.exists() && imageFile.isFile()) {
				w.selectedImageFile = imageFile;

				try {
					w.rawSourceImage = javax.imageio.ImageIO.read(imageFile);
					w.actions.scheduleSourcePreview();
				} catch (java.io.IOException ignored) {
					// не критично — просто не показываем превью
				}
			}
		}

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

	void savePreferences() {
		AppPreferences.saveVersion((String) w.versionCombo.getSelectedItem());
		AppPreferences.saveMapWidth((int) w.widthSpinner.getValue());
		AppPreferences.saveMapHeight((int) w.heightSpinner.getValue());
		Object selectedAlgorithm = w.algorithmCombo.getSelectedItem();

		if (selectedAlgorithm instanceof Ditherer.Algorithm algorithm) {
			AppPreferences.saveAlgorithm(algorithm.name());
		}
		AppPreferences.saveImagePath(w.imagePathField.getText().strip());
		AppPreferences.saveBlocksPath(w.blocksPathField.getText().strip());
		AppPreferences.saveOutPath(w.outPathField.getText().strip());
		AppPreferences.saveSupportSettings(w.supportSettings);
		AppPreferences.saveBrightness(w.brightnessSlider.getValue());
		AppPreferences.saveContrast(w.contrastSlider.getValue());
		AppPreferences.saveSaturation(w.saturationSlider.getValue());
		AppPreferences.saveGamma(w.gammaSlider.getValue());
		AppPreferences.saveHue(w.hueSlider.getValue());
		AppPreferences.saveAutoConvert(w.autoConvertToggle.isSelected());
		AppPreferences.saveDitherSettings(w.actions.buildDitherSettingsFromUi());

		if (w.errRateLinkButton != null) {
			AppPreferences.saveErrRateLinked(w.errRateLinkButton.isSelected());
		}

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

	Set<String> loadValidSupportIds(String version) {
		return parsePaletteBlocks(version).values().stream()
			.filter(b -> !b.isNeedSupport())
			.map(BlockData::getId)
			.collect(Collectors.toSet());
	}
}
