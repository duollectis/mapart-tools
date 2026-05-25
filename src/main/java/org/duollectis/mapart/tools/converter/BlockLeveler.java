package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class BlockLeveler {

	private static final String AIR_BLOCK_ID = "minecraft:air";

	@Setter
	private int[][] image;

	@Setter
	private List<PaletteEntry> palette = List.of();

	@Setter
	private String supportBlockId = "minecraft:stone";

	@Getter
	private LeveledEntry[][] processed;
	@Getter
	private int processedHeight;

	/**
	 * Вычисляет высотный уровень для каждого блока карты на основе яркости соседних пикселей.
	 * Алгоритм обходит столбцы снизу вверх, корректируя уровень в зависимости от
	 * {@link Brightness} следующего блока: LOW поднимает уровень, HIGH — опускает.
	 * При {@code normalize=true} нормализация выполняется прямо внутри основного цикла
	 * по столбцам, исключая отдельный проход.
	 *
	 * @param normalize если {@code true}, нормализует уровни так, чтобы минимальный стал 0
	 */
	public void process(boolean normalize) {
		int width = image[0].length;
		int height = image.length;

		processed = new LeveledEntry[height + 1][width];

		PaletteEntry stoneEntry = buildSupportEntry();

		int globalMin = Integer.MAX_VALUE;
		int globalMax = Integer.MIN_VALUE;

		for (int x = 0; x < width; x++) {
			int level = 0;
			int colMin = Integer.MAX_VALUE;
			int colMax = Integer.MIN_VALUE;

			for (int y = height - 1; y >= 0; y--) {
				PaletteEntry entry = palette.get(image[y][x]);

				if (y < height - 1) {
					level += levelDelta(processed[y + 2][x].getEntry().getBrightness());
				}

				colMin = Math.min(colMin, level);
				colMax = Math.max(colMax, level);

				processed[y + 1][x] = new LeveledEntry(entry, level);
			}

			Brightness topBrightness = processed[1][x].getEntry().getBrightness();
			level += levelDelta(topBrightness);

			colMin = Math.min(colMin, level);
			colMax = Math.max(colMax, level);

			processed[0][x] = topBrightness == Brightness.HIGH
				? new LeveledEntry(airEntry(), level)
				: new LeveledEntry(stoneEntry, level);

			// Нормализуем столбец сразу, не откладывая на второй проход
			if (normalize) {
				for (int y = 0; y <= height; y++) {
					processed[y][x].addLevel(-colMin);
				}

				colMax -= colMin;
				colMin = 0;
			}

			globalMin = Math.min(globalMin, colMin);
			globalMax = Math.max(globalMax, colMax);
		}

		// Финальный сдвиг нужен только если normalize=false (глобальный минимум может быть отрицательным)
		if (!normalize && globalMin != 0) {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y <= height; y++) {
					processed[y][x].addLevel(-globalMin);
				}
			}

			globalMax -= globalMin;
		}

		processedHeight = globalMax + 1;
	}

	/**
	 * Создаёт технический PaletteEntry для блока-опоры напрямую из supportBlockId,
	 * не завися от активной палитры пользователя. Яркость NORMAL — блок-подпорка
	 * не участвует в расчёте затенения карты.
	 */
	private PaletteEntry buildSupportEntry() {
		return new PaletteEntry(List.of(new BlockData(supportBlockId)), 0, Brightness.NORMAL);
	}

	private static int levelDelta(Brightness brightness) {
		return switch (brightness) {
			case LOW -> 1;
			case HIGH -> -1;
			default -> 0;
		};
	}

	private static PaletteEntry airEntry() {
		return new PaletteEntry(List.of(new BlockData(AIR_BLOCK_ID)), 0, Brightness.HIGH);
	}
}
