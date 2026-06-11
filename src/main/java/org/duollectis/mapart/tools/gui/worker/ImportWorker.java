package org.duollectis.mapart.tools.gui.worker;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.ImageConverter;
import org.duollectis.mapart.tools.converter.SchematicFormat;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.gui.Lang;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Импортирует одну или несколько схематик в фоновом потоке и склеивает их в единое превью.
 * Каждый файл — одна карта 128×128. Файлы с именем вида {@code map_X_Y} расставляются
 * по координатам (X-1, Y-1) в сетке. Остальные файлы — по натуральной сортировке имён,
 * слева направо, сверху вниз.
 */
public class ImportWorker extends SwingWorker<ImportWorker.Result, String> {

	private static final int MAP_SIZE = 128;
	private static final int MAP_SIZE_WITH_SHADOW = 129;

	// Паттерн для имён вида map_1_2, map_1_2.litematic, map_1_2.nbt
	private static final Pattern MAP_NAME_PATTERN = Pattern.compile("map_(\\d+)_(\\d+)(?:\\..+)?$");

	private final List<File> schematicFiles;
	private final String paletteJson;
	private final int gridWidth;
	private final int gridHeight;
	private final boolean xyOrder;
	private final Consumer<String> onProgress;
	private final BiConsumer<Result, BufferedImage> onSuccess;
	private final Consumer<String> onError;

	public ImportWorker(
		List<File> schematicFiles,
		String paletteJson,
		int gridWidth,
		int gridHeight,
		boolean xyOrder,
		Consumer<String> onProgress,
		BiConsumer<Result, BufferedImage> onSuccess,
		Consumer<String> onError
	) {
		this.schematicFiles = schematicFiles;
		this.paletteJson = paletteJson;
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.xyOrder = xyOrder;
		this.onProgress = onProgress;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	@Override
	protected Result doInBackground() throws Exception {
		SchematicImportResult merged = readAndMerge();
		BufferedImage preview = ImageConverter.renderPreview(merged, paletteJson);
		return new Result(merged, preview);
	}

	/**
	 * Читает все файлы и склеивает их блоки в единый массив.
	 * <p>
	 * Если выбран один файл — его реальные размеры (mapWidth × mapHeight) берутся из самой схемы,
	 * диалог сетки игнорируется. Если файлов несколько — каждый должен быть ровно одной картой
	 * 128×128; файлы, превышающие этот размер, пропускаются с предупреждением.
	 */
	private SchematicImportResult readAndMerge() throws Exception {
		return schematicFiles.size() == 1
			? readSingleFile(schematicFiles.getFirst())
			: readMultipleFiles();
	}

	private SchematicImportResult readSingleFile(File file) throws Exception {
		publish(file.getName());

		SchematicFormat format = SchematicFormat.fromExtension(file.getName());
		SchematicImportResult single = format.createReader().read(file);

		return single;
	}

	private SchematicImportResult readMultipleFiles() {
		int totalWidth = gridWidth * MAP_SIZE;
		int totalHeight = gridHeight * MAP_SIZE;
		BlockData[][] merged = new BlockData[totalHeight][totalWidth];
		// +1 строка: индекс 0 — опорный ряд первого тайла, индексы 1..totalHeight — реальные строки
		int[][] mergedLevels = new int[totalHeight + 1][totalWidth];
		Set<String> allBlockIds = new HashSet<>();

		List<File> sorted = schematicFiles.stream()
			.sorted(Comparator.comparing(f -> f.getName().toLowerCase()))
			.toList();

		int fallbackIndex = 0;

		for (File file : sorted) {
			publish(file.getName());

			try {
				SchematicFormat format = SchematicFormat.fromExtension(file.getName());
				SchematicImportResult single = format.createReader().read(file);

				BlockData[][] src = single.blocks();
				int[][] srcLevels = single.topLevels();
				int srcRows = src.length;
				int srcCols = srcRows > 0 ? src[0].length : 0;

				if (srcRows > MAP_SIZE_WITH_SHADOW || srcCols > MAP_SIZE) {
					publish(Lang.t("import.file.skip", file.getName(), Lang.t("import.file.too_large")));
					continue;
				}

				allBlockIds.addAll(single.blockIds());

				int col;
				int row;

				Matcher matcher = MAP_NAME_PATTERN.matcher(file.getName());

				if (matcher.find()) {
					col = Integer.parseInt(matcher.group(1)) - 1;
					row = Integer.parseInt(matcher.group(2)) - 1;
				} else if (xyOrder) {
					col = fallbackIndex % gridWidth;
					row = fallbackIndex / gridWidth;
					fallbackIndex++;
				} else {
					row = fallbackIndex % gridHeight;
					col = fallbackIndex / gridHeight;
					fallbackIndex++;
				}

				if (col >= gridWidth || row >= gridHeight) {
					continue;
				}

				int offsetX = col * MAP_SIZE;
				int offsetY = row * MAP_SIZE;

				for (int y = 0; y < srcRows; y++) {
					int rowWidth = Math.min(src[y].length, MAP_SIZE);

					for (int x = 0; x < rowWidth; x++) {
						merged[offsetY + y][offsetX + x] = src[y][x];
					}
				}

				if (srcLevels == null) {
					continue;
				}

				// srcLevels[0] — опорный ряд z=0, srcLevels[1..srcRows] — реальные строки.
				// Реальные строки копируем в mergedLevels[offsetY+1..offsetY+srcRows].
				// В позицию mergedLevels[offsetY] (предшественник первой строки тайла) записываем
				// srcLevels[1] (первую реальную строку тайла) — это даёт NORMAL для первой строки
				// каждого тайла и устраняет артефакты на границах между картами.
				int rowWidth0 = srcLevels.length > 1 ? Math.min(srcLevels[1].length, MAP_SIZE) : 0;

				for (int x = 0; x < rowWidth0; x++) {
					mergedLevels[offsetY][offsetX + x] = srcLevels[1][x];
				}

				for (int lz = 1; lz < srcLevels.length; lz++) {
					int destRow = offsetY + lz;

					if (destRow >= mergedLevels.length) {
						break;
					}

					int rowWidth = Math.min(srcLevels[lz].length, MAP_SIZE);

					for (int x = 0; x < rowWidth; x++) {
						mergedLevels[destRow][offsetX + x] = srcLevels[lz][x];
					}
				}
			} catch (Exception e) {
				publish(Lang.t("import.file.skip", file.getName(), e.getMessage()));
			}
		}

		return new SchematicImportResult(merged, mergedLevels, gridWidth, gridHeight, allBlockIds);
	}

	@Override
	protected void process(List<String> chunks) {
		chunks.forEach(onProgress);
	}

	@Override
	protected void done() {
		try {
			Result result = get();
			onSuccess.accept(result, result.preview());
		} catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			cause.printStackTrace(System.err);
			onError.accept(cause.getMessage());
		}
	}

	public record Result(SchematicImportResult importResult, BufferedImage preview) {}
}
