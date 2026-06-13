package org.duollectis.mapart.tools.gui.worker;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Выполняет конвертацию изображения в фоновом потоке.
 * По завершении передаёт готовый {@link Ditherer} в колбэк успеха —
 * схематики не сохраняются, пользователь сначала видит превью.
 * <p>
 * Прогресс публикуется через {@code publish(ConversionStage)} — каждый апдейт несёт
 * фазу этапа и числовой процент. Веса этапов:
 * загрузка палитры (0–5%), подготовка изображения (5–10%), дизеринг (10–100%).
 */
public class ConversionWorker extends SwingWorker<Ditherer, ConversionStage> {

	private final String paletteJson;
	private final File imageFile;
	private final File blocksFile;
	private final int mapWidth;
	private final int mapHeight;
	private final DitherAlgorithm algorithm;
	private final ImageAdjustments adjustments;
	private final DitherSettings ditherSettings;
	private final CropSettings cropSettings;
	private final Map<String, WeightedSelector<BlockData>> blockSelectors;
	private final StaircaseMode staircaseMode;
	private final Consumer<String> onLog;
	private final Consumer<ConversionStage> onStage;
	private final Consumer<Ditherer> onSuccess;
	private final Consumer<String> onError;
	private final Runnable onCancelled;

	public ConversionWorker(
		String paletteJson,
		File imageFile,
		File blocksFile,
		int mapWidth,
		int mapHeight,
		DitherAlgorithm algorithm,
		ImageAdjustments adjustments,
		DitherSettings ditherSettings,
		CropSettings cropSettings,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		StaircaseMode staircaseMode,
		Consumer<String> onLog,
		Consumer<ConversionStage> onStage,
		Consumer<Ditherer> onSuccess,
		Consumer<String> onError,
		Runnable onCancelled
	) {
		this.paletteJson = paletteJson;
		this.imageFile = imageFile;
		this.blocksFile = blocksFile;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.algorithm = algorithm;
		this.adjustments = adjustments;
		this.ditherSettings = ditherSettings;
		this.cropSettings = cropSettings;
		this.blockSelectors = blockSelectors;
		this.staircaseMode = staircaseMode;
		this.onLog = onLog;
		this.onStage = onStage;
		this.onSuccess = onSuccess;
		this.onError = onError;
		this.onCancelled = onCancelled;
	}

	@Override
	protected Ditherer doInBackground() throws Exception {
		try {
			return new ImageConverter().dither(
				paletteJson,
				imageFile,
				blocksFile,
				mapWidth,
				mapHeight,
				algorithm,
				adjustments,
				ditherSettings,
				cropSettings,
				blockSelectors,
				staircaseMode,
				stage -> {
					if (isCancelled()) {
						return;
					}

					publish(stage);
				},
				this::isCancelled
			);
		} catch (CancellationException e) {
			cancel(false);
			return null;
		}
	}

	@Override
	protected void process(java.util.List<ConversionStage> chunks) {
		onStage.accept(chunks.getLast());
	}

	@Override
	protected void done() {
		if (isCancelled()) {
			onCancelled.run();
			return;
		}

		try {
			onSuccess.accept(get());
		} catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			cause.printStackTrace(System.err);
			onError.accept(cause.getMessage());
		}
	}
}
