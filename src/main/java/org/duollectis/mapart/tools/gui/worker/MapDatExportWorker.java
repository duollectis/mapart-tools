package org.duollectis.mapart.tools.gui.worker;

import org.duollectis.mapart.tools.app.AppMessages;
import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.MapDatWriter;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Экспортирует результат дизеринга в набор файлов карт Minecraft (.dat) в фоновом потоке.
 * Каждый файл соответствует одной карте 128×128 и содержит gzip-сжатый NBT с массивом цветов.
 */
public class MapDatExportWorker extends SwingWorker<Void, String> {

	private final Ditherer ditherer;
	private final File outDir;
	private final int mapWidth;
	private final int mapHeight;
	private final Consumer<String> onProgress;
	private final Runnable onSuccess;
	private final Consumer<String> onError;

	public MapDatExportWorker(
		Ditherer ditherer,
		File outDir,
		int mapWidth,
		int mapHeight,
		Consumer<String> onProgress,
		Runnable onSuccess,
		Consumer<String> onError
	) {
		this.ditherer = ditherer;
		this.outDir = outDir;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.onProgress = onProgress;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	@Override
	protected Void doInBackground() throws Exception {
		publish(AppMessages.PROGRESS_GENERATING_MAP_DAT.formatted(mapWidth, mapHeight));

		MapDatWriter.write(
			ditherer.getDithered(),
			ditherer.getPalette(),
			outDir,
			mapWidth,
			mapHeight,
			0
		);

		return null;
	}

	@Override
	protected void process(List<String> chunks) {
		chunks.forEach(onProgress);
	}

	@Override
	protected void done() {
		try {
			get();
			onSuccess.run();
		} catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			cause.printStackTrace(System.err);
			onError.accept(cause.getMessage());
		}
	}
}
