package org.duollectis.mapart.tools.gui.worker;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Выполняет только дизеринг изображения в фоновом потоке.
 * По завершении передаёт готовый {@link Ditherer} в колбэк успеха —
 * схематики не сохраняются, пользователь сначала видит превью.
 * <p>
 * Принимает уже загруженный {@code paletteJson} — повторное чтение ZIP
 * из classpath не происходит, что устраняет задержку при повторных запусках.
 * <p>
 * Прогресс дизеринга (0–100) публикуется через {@link SwingWorker#setProgress},
 * что позволяет GUI обновлять {@code JProgressBar} без блокировки EDT.
 */
public class ConversionWorker extends SwingWorker<Ditherer, String> {

	private final String paletteJson;
	private final File imageFile;
	private final File blocksFile;
	private final int mapWidth;
	private final int mapHeight;
	private final Ditherer.Algorithm algorithm;
	private final ImageAdjustments adjustments;
	private final DitherSettings ditherSettings;
	private final CropSettings cropSettings;
	private final Map<String, WeightedSelector<BlockData>> blockSelectors;
	private final StaircaseMode staircaseMode;
	private final Consumer<String> onLog;
	private final Consumer<Ditherer> onSuccess;
	private final Consumer<String> onError;
	private final Runnable onCancelled;

	public ConversionWorker(
		String paletteJson,
		File imageFile,
		File blocksFile,
		int mapWidth,
		int mapHeight,
		Ditherer.Algorithm algorithm,
		ImageAdjustments adjustments,
		DitherSettings ditherSettings,
		CropSettings cropSettings,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		StaircaseMode staircaseMode,
		Consumer<String> onLog,
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
		this.onSuccess = onSuccess;
		this.onError = onError;
		this.onCancelled = onCancelled;
	}

	@Override
	protected Ditherer doInBackground() throws Exception {
		publish("Дизеринг изображения (%dx%d карт, алгоритм: %s)..."
			.formatted(mapWidth, mapHeight, algorithm.name()));

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
			percent -> {
				setProgress(percent);
				return isCancelled() ? 1 : 0;
			}
		);
	}

	@Override
	protected void process(java.util.List<String> chunks) {
		chunks.forEach(onLog);
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
