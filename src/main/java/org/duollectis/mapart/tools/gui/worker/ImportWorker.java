package org.duollectis.mapart.tools.gui.worker;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.Brightness;
import org.duollectis.mapart.tools.converter.ImageConverter;
import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.app.AppMessages;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.utils.JsonHelper;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Импортирует одну или несколько схематик в фоновом потоке.
 * Каждый файл становится отдельным слоем. Если имя файла соответствует паттерну
 * {@code map_X_Y}, слой получает координаты ячейки сетки (col = X-1, row = Y-1).
 * <p>
 * Оптимизации: палитра парсится один раз для всей пачки файлов,
 * а сами файлы обрабатываются параллельно через пул потоков.
 */
public class ImportWorker extends SwingWorker<List<ImportWorker.SingleResult>, String> {

	private static final Pattern MAP_NAME_PATTERN = Pattern.compile("map_(\\d+)_(\\d+)(?:\\..+)?$");

	private final List<File> schematicFiles;
	private final String paletteJson;
	private final Consumer<String> onProgress;
	private final Consumer<List<SingleResult>> onSuccess;
	private final Consumer<String> onError;

	public ImportWorker(
		List<File> schematicFiles,
		String paletteJson,
		Consumer<String> onProgress,
		Consumer<List<SingleResult>> onSuccess,
		Consumer<String> onError
	) {
		this.schematicFiles = schematicFiles;
		this.paletteJson = paletteJson;
		this.onProgress = onProgress;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	@Override
	protected List<SingleResult> doInBackground() throws Exception {
		Map<String, Map<Brightness, Integer>> blockColorMap = buildBlockColorMap(paletteJson);

		int total = schematicFiles.size();
		List<SingleResult> results = new ArrayList<>(total);

		for (int i = 0; i < total; i++) {
			if (isCancelled()) {
				break;
			}

			SingleResult result = processFile(schematicFiles.get(i), blockColorMap);

			if (result != null) {
				results.add(result);
			}

			setProgress((i + 1) * 100 / total);
		}

		return results;
	}

	private SingleResult processFile(File file, Map<String, Map<Brightness, Integer>> blockColorMap) {
		publish(file.getName());

		try {
			SchematicFormat format = SchematicFormat.fromExtension(file.getName());
			SchematicImportResult importResult = format.createReader().read(file);
			BufferedImage preview = ImageConverter.renderPreview(importResult, blockColorMap);
			int[] cell = parseGridCell(file.getName());
			return new SingleResult(importResult, preview, file.getName(), cell[0], cell[1]);
		} catch (Exception e) {
			publish(UpdatableRegistry.translate(AppMessages.IMPORT_FILE_SKIP, file.getName(), e.getMessage()));
			return null;
		}
	}

	private static Map<String, Map<Brightness, Integer>> buildBlockColorMap(String paletteJson) {
		Map<Integer, List<BlockData>> paletteMap = JsonHelper.GSON.fromJson(
			paletteJson,
			new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
		);

		return ImageConverter.buildBlockColorMapPublic(paletteMap);
	}

	/**
	 * Парсит координаты ячейки сетки из имени файла вида {@code map_X_Y}.
	 * Возвращает 0-based (col, row) = (X-1, Y-1), или (-1, -1) если паттерн не совпал.
	 */
	private static int[] parseGridCell(String fileName) {
		Matcher matcher = MAP_NAME_PATTERN.matcher(fileName);

		if (matcher.find()) {
			int col = Integer.parseInt(matcher.group(1)) - 1;
			int row = Integer.parseInt(matcher.group(2)) - 1;
			return new int[]{col, row};
		}

		return new int[]{-1, -1};
	}

	@Override
	protected void process(List<String> chunks) {
		chunks.forEach(onProgress);
	}

	@Override
	protected void done() {
		if (isCancelled()) {
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

	/**
	 * @param gridCol 0-based колонка в сетке, или -1 если позиция неизвестна
	 * @param gridRow 0-based строка в сетке, или -1 если позиция неизвестна
	 */
	public record SingleResult(
		SchematicImportResult importResult,
		BufferedImage preview,
		String fileName,
		int gridCol,
		int gridRow
	) {}
}
