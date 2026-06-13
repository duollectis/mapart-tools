package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
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
	 * В режиме {@link StaircaseMode#STAIRCASE} строит классические лесенки вверх: LOW поднимает уровень,
	 * HIGH — опускает. В режиме {@link StaircaseMode#VALLEY} лесенка разбивается на сегменты по
	 * локальным максимумам — каждый сегмент сбрасывается вниз к нулю, экономя высоту без изменения
	 * итоговой картинки. В плоских режимах все блоки размещаются на уровне 0 — схема одноэтажная.
	 *
	 * @param normalize если {@code true}, применяется сегментная нормализация (valley)
	 * @param mode      режим ступенчатости схемы
	 */
	public void process(boolean normalize, StaircaseMode mode) {
		if (mode.isFlat()) {
			processFlat();
			return;
		}

		int width = image[0].length;
		int height = image.length;

		processed = new LeveledEntry[height + 1][width];

		PaletteEntry stoneEntry = buildSupportEntry();

		int globalMin = Integer.MAX_VALUE;
		int globalMax = Integer.MIN_VALUE;

		for (int x = 0; x < width; x++) {
			int level = 0;

			for (int y = height - 1; y >= 0; y--) {
				PaletteEntry entry = palette.get(image[y][x]);

				if (y < height - 1) {
					level += processed[y + 2][x].getEntry().getBrightness().getLevelDelta();
				}

				processed[y + 1][x] = new LeveledEntry(entry, level);
			}

			Brightness topBrightness = processed[1][x].getEntry().getBrightness();
			level += topBrightness.getLevelDelta();
	
			processed[0][x] = topBrightness == Brightness.HIGH
				? new LeveledEntry(airEntry(), level)
				: new LeveledEntry(stoneEntry, level);

			if (normalize) {
				applyValleyNormalization(x, height);
			}

			int colMin = Integer.MAX_VALUE;
			int colMax = Integer.MIN_VALUE;

			for (int y = 0; y <= height; y++) {
				int l = processed[y][x].getLevel();
				colMin = Math.min(colMin, l);
				colMax = Math.max(colMax, l);
			}

			globalMin = Math.min(globalMin, colMin);
			globalMax = Math.max(globalMax, colMax);
		}

		// Финальный сдвиг: приводим глобальный минимум к нулю.
		// Для Classic это исправляет отрицательные уровни от HIGH-блоков.
		// Для Valley это поднимает всю схему вверх, так как после нормализации уровни отрицательные.
		if (globalMin != 0) {
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
	 * Valley-нормализация по алгоритму Rebasin: находит плато (участки после подъёма до спуска)
	 * и не-плато участки между ними. Каждый не-плато участок сдвигается вниз на свой минимум.
	 * Плато сдвигается на минимум из двух соседних сдвигов не-плато — это гарантирует плавность
	 * без разрывов между участками. Итоговая картинка идентична Classic-режиму.
	 */
	private void applyValleyNormalization(int x, int height) {
		// Индексы плато: [startY, endY) — полуоткрытый интервал.
		// Начальное нулевое плато нужно, чтобы не было спецслучая для первого реального плато.
		List<int[]> plateaus = new ArrayList<>();
		plateaus.add(new int[]{0, 0});

		boolean ascending = false;
		int plateauStartY = -1;
		int visibleLevel = processed[0][x].getLevel();

		for (int y = 1; y <= height; y++) {
			int level = processed[y][x].getLevel();

			if (ascending && level < visibleLevel) {
				// Спуск после подъёма — плато найдено
				ascending = false;
				plateaus.add(new int[]{plateauStartY, y});
			} else if (level > visibleLevel) {
				// Подъём — начало нового плато
				ascending = true;
				plateauStartY = y;
			}

			visibleLevel = level;
		}

		plateaus.add(new int[]{height + 1, height + 1});

		// Сдвигаем не-плато участки и плато между ними
		int[] prevNonPlateauShift = {Integer.MAX_VALUE, Integer.MAX_VALUE};

		while (plateaus.size() != 1) {
			int[] left = plateaus.get(0);
			int[] right = plateaus.get(1);

			// Сдвигаем не-плато участок между left и right на его минимум
			int nonPlateauMin = Integer.MAX_VALUE;

			for (int y = left[1]; y < right[0]; y++) {
				nonPlateauMin = Math.min(nonPlateauMin, processed[y][x].getLevel());
			}

			if (nonPlateauMin != Integer.MAX_VALUE && nonPlateauMin != 0) {
				for (int y = left[1]; y < right[0]; y++) {
					processed[y][x].addLevel(-nonPlateauMin);
				}
			}

			prevNonPlateauShift[1] = nonPlateauMin == Integer.MAX_VALUE ? 0 : nonPlateauMin;

			// Плато сдвигается на минимум из двух соседних сдвигов не-плато
			int plateauShift = Math.min(prevNonPlateauShift[0], prevNonPlateauShift[1]);

			if (plateauShift != Integer.MAX_VALUE && plateauShift != 0) {
				for (int y = left[0]; y < left[1]; y++) {
					processed[y][x].addLevel(-plateauShift);
				}
			}

			plateaus.remove(0);
			prevNonPlateauShift[0] = prevNonPlateauShift[1];
		}
	}

	/**
	 * Создаёт технический PaletteEntry для блока-опоры напрямую из supportBlockId,
	 * не завися от активной палитры пользователя. Яркость NORMAL — блок-подпорка
	 * не участвует в расчёте затенения карты.
	 */
	private PaletteEntry buildSupportEntry() {
		return new PaletteEntry(List.of(new BlockData(supportBlockId)), 0, Brightness.NORMAL);
	}

	private void processFlat() {
		int width = image[0].length;
		int height = image.length;

		processed = new LeveledEntry[height + 1][width];

		PaletteEntry stoneEntry = buildSupportEntry();

		for (int x = 0; x < width; x++) {
			processed[0][x] = new LeveledEntry(stoneEntry, 0);

			for (int y = 0; y < height; y++) {
				processed[y + 1][x] = new LeveledEntry(palette.get(image[y][x]), 0);
			}
		}

		processedHeight = 1;
	}

	private static PaletteEntry airEntry() {
		return new PaletteEntry(List.of(new BlockData(AIR_BLOCK_ID)), 0, Brightness.HIGH);
	}

	/**
	 * Обратный процесс: читает верхний слой блоков и их Y-уровни из 3D-объёма схемы.
	 * Для каждой позиции (x, z) находит блок с максимальным Y, который не является воздухом.
	 * Y-уровни нужны для корректного расчёта яркости при рендере превью импортированной схемы.
	 * <p>
	 * Массив {@code levels} имеет размер {@code [sizeZ][sizeX]}: индекс 0 соответствует
	 * техническому опорному ряду z=0, индексы 1..sizeZ-1 — реальным строкам карты.
	 * Это позволяет корректно вычислить яркость первой строки карты, сравнив её с опорным рядом.
	 *
	 * @param volume трёхмерный массив блоков [y][z][x]
	 * @param sizeX  ширина схемы по X
	 * @param sizeY  высота схемы по Y
	 * @param sizeZ  глубина схемы по Z (включает технический ряд Z=0)
	 * @return результат: blocks[z-1][x] — реальные блоки, levels[z][x] — Y-уровни включая z=0
	 */
	public static TopLayerResult readTopLayer(BlockData[][][] volume, int sizeX, int sizeY, int sizeZ) {
		int mapZ = sizeZ - 1;
		BlockData[][] topLayer = new BlockData[mapZ][sizeX];
		int[][] topLevels = new int[sizeZ][sizeX];
		BlockData fallback = new BlockData(AIR_BLOCK_ID);

		for (int z = 0; z < sizeZ; z++) {
			for (int x = 0; x < sizeX; x++) {
				BlockData top = fallback;
				int topY = 0;

				for (int y = sizeY - 1; y >= 0; y--) {
					BlockData block = volume[y][z][x];

					if (block == null || block.getId().equals(AIR_BLOCK_ID)) {
						continue;
					}

					top = block;
					topY = y;
					break;
				}

				topLevels[z][x] = topY;

				if (z > 0) {
					topLayer[z - 1][x] = top;
				}
			}
		}

		return new TopLayerResult(topLayer, topLevels);
	}

	public record TopLayerResult(BlockData[][] blocks, int[][] levels) {}
}
