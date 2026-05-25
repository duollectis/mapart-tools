package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.Ditherer;
import org.duollectis.mapart.tools.converter.ImageConverter;

import javax.swing.SwingWorker;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Экспортирует схематики (.nbt) из уже готового результата дизеринга в фоновом потоке.
 * Принимает {@link Ditherer} с заполненным результатом и сохраняет файлы в указанную директорию.
 * Палитра берётся напрямую из {@link Ditherer#getPalette()} — повторная загрузка не нужна.
 */
public class ExportWorker extends SwingWorker<Void, String> {

	private final Ditherer ditherer;
	private final File outDir;
	private final int mapWidth;
	private final int mapHeight;
	private final String supportBlockId;
	private final Consumer<String> onProgress;
	private final Runnable onSuccess;
	private final Consumer<String> onError;

	public ExportWorker(
		Ditherer ditherer,
		File outDir,
		int mapWidth,
		int mapHeight,
		String supportBlockId,
		Consumer<String> onProgress,
		Runnable onSuccess,
		Consumer<String> onError
	) {
		this.ditherer = ditherer;
		this.outDir = outDir;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.supportBlockId = supportBlockId;
		this.onProgress = onProgress;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	@Override
	protected Void doInBackground() throws Exception {
		publish("Генерация схематик (%dx%d карт)...".formatted(mapWidth, mapHeight));

		new ImageConverter().exportSchematics(
			ditherer.getDithered(),
			ditherer.getPalette(),
			outDir,
			mapWidth,
			mapHeight,
			supportBlockId
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
