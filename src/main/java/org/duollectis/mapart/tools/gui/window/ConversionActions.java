package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.app.AppMessages;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.ImageLayer;
import org.duollectis.mapart.tools.gui.worker.ConversionWorker;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Отвечает за весь жизненный цикл конвертации: запуск, отмену, обработку результатов,
 * управление состоянием кнопки конвертации и прогресс-бара.
 */
final class ConversionActions {

	private final MainWindow w;

	ConversionActions(MainWindow window) {
		w = window;
	}

	/**
	 * Throttle-обновление превью оригинала: запускает обработку мгновенно в фоновом потоке.
	 * Если воркер уже работает — ставит флаг pending, и по завершении текущего
	 * автоматически запускается ещё один с актуальными настройками.
	 */
	void scheduleSourcePreview() {
		if (w.rawSourceImage == null) {
			return;
		}

		w.sourcePreviewPending = true;

		if (w.sourcePreviewRunning) {
			return;
		}

		runSourcePreviewWorker();
	}

	void runSourcePreviewWorker() {
		w.sourcePreviewPending = false;
		w.sourcePreviewRunning = true;

		ImageLayer activeLayer = w.sourcePreview.getLayers().isEmpty()
			? null
			: w.sourcePreview.getLayers().get(w.sourcePreview.getActiveLayerIndex());
		BufferedImage src = activeLayer != null ? activeLayer.getRawImage() : w.rawSourceImage;
		ImageAdjustments snapshot = buildAdjustments();

		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() {
				return ImageUtils.applyAdjustments(src, snapshot);
			}

			@Override
			protected void done() {
				try {
					BufferedImage adjusted = get();

					if (w.sourcePreview.getLayers().isEmpty()) {
						w.sourcePreview.addLayer(adjusted, AppMessages.LAYER_DEFAULT_NAME);
					} else {
						w.sourcePreview.updateActiveLayerImage(adjusted);
					}

					if (w.resetSourceViewOnNextImage) {
						w.resetSourceViewOnNextImage = false;
						w.sourcePreview.resetDisplayOffset();
					}
				} catch (Exception ignored) {
				}

				w.sourcePreviewRunning = false;

				if (w.sourcePreviewPending) {
					runSourcePreviewWorker();
				}
			}
		}.execute();
	}

	/**
	 * Запускает конвертацию с debounce 400ms только если включена кнопка «Авто».
	 */
	void scheduleConversion() {
		if (w.autoConvertToggle == null || !w.autoConvertToggle.isSelected()) {
			return;
		}

		if (w.conversionDebounceTimer != null) {
			w.conversionDebounceTimer.stop();
		}

		w.conversionDebounceTimer = new Timer(400, e -> startConversion());
		w.conversionDebounceTimer.setRepeats(false);
		w.conversionDebounceTimer.start();
	}

	void scheduleConversionIfAuto() {
		scheduleConversion();
	}

	void startConversion() {
		if (w.enabledBlocks.isEmpty()) {
			w.actions.showError(UpdatableRegistry.translate("error.no_blocks_selected"));
			return;
		}

		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}

		int mapWidth = w.mapSizeControl.getMapWidth();
		int mapHeight = w.mapSizeControl.getMapHeight();

		File compositeFile = buildCompositeFile(mapWidth, mapHeight);
		File blocksFile = w.actions.resolveBlocksFile();

		if (compositeFile == null || blocksFile == null) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		DitherAlgorithm algorithm = resolveAlgorithm();

		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			onConversionError(e.getMessage());
			return;
		}

		closePreviousDitherer();
		setConvertingState(true);
		DiscordRpc.setConverting(0);
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_DITHER_START, compositeFile.getName(), mapWidth, mapHeight));

		StaircaseMode staircaseMode = w.staircaseModeCombo.getSelectedItem() instanceof StaircaseMode mode
			? mode
			: StaircaseMode.STAIRCASE;

		w.activeConversionWorker = new ConversionWorker(
			paletteJson,
			compositeFile,
			blocksFile,
			mapWidth,
			mapHeight,
			algorithm,
			buildAdjustments(),
			buildDitherSettingsFromUi(),
			CropSettings.defaultFit(),
			w.blockSelectors,
			staircaseMode,
			w.actions::log,
			stage -> {
				w.progressBar.setValue(stage.percent());
				w.progressBar.setString(resolveStageLabel(stage));
				DiscordRpc.setConverting(stage.percent());
			},
			this::onDitheringSuccess,
			this::onConversionError,
			this::onConversionCancelled
		);

		w.activeConversionWorker.execute();
	}

	String loadPaletteJson(String version) throws Exception {
		String cached = w.paletteCache.get(version);

		if (cached != null) {
			return cached;
		}

		String resourcePath = "versions/" + version + ".zip";
		String jsonEntry = version + ".json";

		try (InputStream raw = w.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (raw == null) {
				throw new RuntimeException(UpdatableRegistry.translate(AppMessages.ERROR_VERSION_NOT_FOUND, version));
			}

			try (ZipInputStream zip = new ZipInputStream(raw)) {
				ZipEntry entry;

				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().equals(jsonEntry)) {
						String json = new String(zip.readAllBytes());
						w.paletteCache.put(version, json);
						return json;
					}

					zip.closeEntry();
				}
			}
		}

		throw new RuntimeException(UpdatableRegistry.translate(AppMessages.ERROR_PALETTE_ENTRY_NOT_FOUND, jsonEntry, version));
	}

	void onDitheringSuccess(Ditherer ditherer) {
		w.lastDitherer = ditherer;
		w.resultPreview.setImage(ditherer.createPreview());
		w.resultPreview.setMapCount(w.mapSizeControl.getMapWidth(), w.mapSizeControl.getMapHeight());

		// Дизеренное изображение имеет размер mapW*128 × mapH*128 — точно пропорции сетки.
		// Растягиваем его на всю сетку без полей (stretch, не fit).
		SwingUtilities.invokeLater(() -> w.resultPreview.resetDisplayOffsetStretch());

		setConvertingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setString(UpdatableRegistry.translate(AppMessages.PROGRESS_DITHER_DONE, ditherer.getDitherTime()));
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		w.exportButton.setEnabled(true);
		w.blockListButton.setEnabled(true);
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_DITHER_DONE, ditherer.getDitherTime()));
	}

	void onConversionError(String message) {
		setConvertingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setString(message);
		w.progressBar.setForeground(GuiApp.theme.getError());
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_ERROR, message));
	}

	void onConversionCancelled() {
		setConvertingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setValue(0);
		w.progressBar.setString(AppMessages.PROGRESS_CANCELLED);
		w.progressBar.setForeground(GuiApp.theme.getWarn());
		w.actions.log(AppMessages.LOG_CANCELLED);
	}

	void setConvertingState(boolean converting) {
		w.progressBar.setIndeterminate(false);

		if (converting) {
			w.progressBar.setValue(0);
			w.progressBar.setString(AppMessages.PROGRESS_DITHERING + " 0%");
			w.progressBar.setForeground(GuiApp.theme.getAccent());
			w.exportButton.setEnabled(false);
			w.blockListButton.setEnabled(false);
			switchConvertButtonToStop();
		} else {
			switchConvertButtonToConvert();
		}
	}

	void closePreviousDitherer() {
		if (w.lastDitherer == null) {
			return;
		}

		w.lastDitherer.close();
		w.lastDitherer = null;
		w.exportButton.setEnabled(false);
		w.blockListButton.setEnabled(false);
	}

	void startConversionForBlockList() {
		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}

		int mapWidth = w.mapSizeControl.getMapWidth();
		int mapHeight = w.mapSizeControl.getMapHeight();

		File compositeFile = buildCompositeFile(mapWidth, mapHeight);
		File blocksFile = w.actions.resolveBlocksFile();

		if (compositeFile == null || blocksFile == null) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		DitherAlgorithm algorithm = resolveAlgorithm();

		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			onConversionError(e.getMessage());
			return;
		}

		closePreviousDitherer();
		setConvertingState(true);
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_DITHER_START, compositeFile.getName(), mapWidth, mapHeight));

		StaircaseMode staircaseMode = w.staircaseModeCombo.getSelectedItem() instanceof StaircaseMode mode
			? mode
			: StaircaseMode.STAIRCASE;

		w.activeConversionWorker = new ConversionWorker(
			paletteJson,
			compositeFile,
			blocksFile,
			mapWidth,
			mapHeight,
			algorithm,
			buildAdjustments(),
			buildDitherSettingsFromUi(),
			CropSettings.defaultFit(),
			w.blockSelectors,
			staircaseMode,
			w.actions::log,
			stage -> {
				w.progressBar.setValue(stage.percent());
				w.progressBar.setString(resolveStageLabel(stage));
			},
			this::onDitheringSuccessFromBlockList,
			this::onConversionError,
			this::onConversionCancelled
		);

		w.activeConversionWorker.execute();
	}

	void onDitheringSuccessFromBlockList(Ditherer ditherer) {
		onDitheringSuccess(ditherer);

		if (w.activeBlockListDialog != null && w.activeBlockListDialog.isVisible()) {
			w.activeBlockListDialog.refresh(ditherer.getUsedBlockCounts(), ditherer.getSupportBlockCount());
		}
	}

	void syncSourcePreviewMapCount() {
		if (w.sourcePreview == null || w.mapSizeControl == null) {
			return;
		}

		w.sourcePreview.setMapCount(
			w.mapSizeControl.getMapWidth(),
			w.mapSizeControl.getMapHeight()
		);
	}

	void scaleMapCount(double factor) {
		int newWidth = (int) Math.round(w.mapSizeControl.getMapWidth() * factor);
		int newHeight = (int) Math.round(w.mapSizeControl.getMapHeight() * factor);
		w.mapSizeControl.setMapWidth(Math.clamp(newWidth, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.mapSizeControl.setMapHeight(Math.clamp(newHeight, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		syncSourcePreviewMapCount();
		scheduleConversionIfAuto();
	}

	void autoFitMapCount() {
		if (w.rawSourceImage == null) {
			return;
		}

		int mapsW = (int) Math.ceil(w.rawSourceImage.getWidth() / 128.0);
		int mapsH = (int) Math.ceil(w.rawSourceImage.getHeight() / 128.0);

		w.mapSizeControl.setMapWidth(Math.clamp(mapsW, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.mapSizeControl.setMapHeight(Math.clamp(mapsH, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		syncSourcePreviewMapCount();
		scheduleConversionIfAuto();
	}

	/**
	 * Компонует все видимые слои sourcePreview в единое изображение размером targetW×targetH
	 * и сохраняет его во временный PNG-файл для передачи в ConversionWorker.
	 * Возвращает null и показывает ошибку, если слоёв нет или запись не удалась.
	 */
	private File buildCompositeFile(int mapWidth, int mapHeight) {
		if (w.sourcePreview.getLayers().isEmpty()) {
			w.actions.showError(UpdatableRegistry.translate("error.no_image"));
			return null;
		}

		int targetW = mapWidth * 128;
		int targetH = mapHeight * 128;
		BufferedImage composite = w.sourcePreview.compositeImage(targetW, targetH);

		try {
			File tempFile = Files.createTempFile("mapart_composite_", ".png").toFile();
			tempFile.deleteOnExit();
			ImageIO.write(composite, "png", tempFile);
			return tempFile;
		} catch (IOException e) {
			w.actions.showError(UpdatableRegistry.translate("error.image_load_failed", e.getMessage()));
			return null;
		}
	}

	DitherSettings buildDitherSettingsFromUi() {
		DitherSettings defaults = DitherSettings.defaults();
		boolean separateChannels = w.errRateLinkButton != null && w.errRateLinkButton.isSelected();
		double errRateR = separateChannels && w.errRateRRow != null
			? w.errRateRRow.getValue() / 100.0
			: w.errRateStrengthRow != null
				? w.errRateStrengthRow.getValue() / 100.0
				: defaults.errRateR();
		double errRateG = separateChannels && w.errRateGRow != null
			? w.errRateGRow.getValue() / 100.0
			: errRateR;
		double errRateB = separateChannels && w.errRateBRow != null
			? w.errRateBRow.getValue() / 100.0
			: errRateR;
		double noiseLevel = w.noiseLevelRow != null
			? w.noiseLevelRow.getValue() / 100.0
			: defaults.noiseLevel();
		ColorMetric metric = w.colorMetricCombo != null
			? (ColorMetric) w.colorMetricCombo.getSelectedItem()
			: defaults.colorMetric();

		return new DitherSettings(errRateR, errRateG, errRateB, noiseLevel, metric);
	}

	ImageAdjustments buildAdjustments() {
		return new ImageAdjustments(
			w.brightnessRow.getValue(),
			w.contrastRow.getValue(),
			w.saturationRow.getValue(),
			w.gammaRow.getValue(),
			w.hueRow.getValue()
		);
	}

	private DitherAlgorithm resolveAlgorithm() {
		Object selected = w.algorithmCombo.getSelectedItem();
		return selected instanceof DitherAlgorithm algorithm
			? algorithm
			: DitherAlgorithm.NONE;
	}

	private void switchConvertButtonToStop() {
		for (var listener : w.convertButton.getActionListeners()) {
			w.convertButton.removeActionListener(listener);
		}

		w.convertButton.setCurrentIcon(AppIcon.STOP);
		w.convertButton.setErrorMode(true);
		w.convertButton.setEnabled(true);
		w.convertButton.addActionListener(e -> cancelConversion());
	}

	private void switchConvertButtonToConvert() {
		for (var listener : w.convertButton.getActionListeners()) {
			w.convertButton.removeActionListener(listener);
		}

		w.convertButton.setCurrentIcon(AppIcon.PLAY);
		w.convertButton.setAccentMode(true);
		w.convertButton.setEnabled(true);
		w.convertButton.addActionListener(e -> startConversion());
	}

	private void cancelConversion() {
		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}
	}

	private static String resolveStageLabel(ConversionStage stage) {
		String base = switch (stage.phase()) {
			case LOADING_PALETTE -> AppMessages.PROGRESS_LOADING_PALETTE;
			case PREPARING_IMAGE -> AppMessages.PROGRESS_PREPARING_IMAGE;
			case DITHERING -> AppMessages.PROGRESS_DITHERING;
		};

		return stage.phase() == ConversionStage.Phase.DITHERING
			? base + " " + stage.percent() + "%"
			: base;
	}
}
