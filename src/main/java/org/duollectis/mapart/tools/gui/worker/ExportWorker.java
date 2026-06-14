package org.duollectis.mapart.tools.gui.worker;

import org.duollectis.mapart.tools.app.AppMessages;
import org.duollectis.mapart.tools.converter.*;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Экспортирует результат дизеринга в фоновом потоке.
 * При формате {@link SchematicFormat#MAP_DAT} записывает файлы карт через {@link MapDatWriter}.
 * Для остальных форматов генерирует схематики через {@link ImageConverter#exportSchematics}.
 */
public class ExportWorker extends SwingWorker<Void, String> {

	private final Ditherer ditherer;
	private final File outDir;
	private final int mapWidth;
	private final int mapHeight;
	private final SupportBlockSettings supportSettings;
	private final SchematicFormat format;
	private final StaircaseMode staircaseMode;
	private final int mapDatStartId;
	private final Consumer<String> onProgress;
	private final Runnable onSuccess;
	private final Consumer<String> onError;

	public ExportWorker(
		Ditherer ditherer,
		File outDir,
		int mapWidth,
		int mapHeight,
		SupportBlockSettings supportSettings,
		SchematicFormat format,
		StaircaseMode staircaseMode,
		int mapDatStartId,
		Consumer<String> onProgress,
		Runnable onSuccess,
		Consumer<String> onError
	) {
		this.ditherer = ditherer;
		this.outDir = outDir;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.supportSettings = supportSettings;
		this.format = format;
		this.staircaseMode = staircaseMode;
		this.mapDatStartId = mapDatStartId;
		this.onProgress = onProgress;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	@Override
	protected Void doInBackground() throws Exception {
		if (format.isMapDat()) {
			publish(AppMessages.PROGRESS_GENERATING_MAP_DAT.formatted(mapWidth, mapHeight));
			MapDatWriter.write(
				ditherer.getDithered(),
				ditherer.getPalette(),
				outDir,
				mapWidth,
				mapHeight,
				mapDatStartId
			);
		} else {
			publish(AppMessages.PROGRESS_GENERATING_SCHEMATICS.formatted(mapWidth, mapHeight));
			new ImageConverter().exportSchematics(
				ditherer.getDithered(),
				ditherer.getResolved(),
				ditherer.getPalette(),
				outDir,
				mapWidth,
				mapHeight,
				supportSettings,
				format,
				staircaseMode
			);
		}

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
