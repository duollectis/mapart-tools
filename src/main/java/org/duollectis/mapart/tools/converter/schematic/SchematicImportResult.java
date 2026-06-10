package org.duollectis.mapart.tools.converter.schematic;

import org.duollectis.mapart.tools.converter.BlockData;

import java.util.Set;

/**
 * Результат импорта схематики.
 * Содержит верхний слой блоков карты (128×128 на карту), Y-уровни верхних блоков
 * и набор уникальных blockId из схемы.
 *
 * @param blocks      двумерный массив блоков верхнего слоя [y][x], размер mapHeight*128 × mapWidth*128
 * @param topLevels   Y-уровни верхних блоков [y][x] — нужны для расчёта яркости в превью
 * @param mapWidth    количество карт по горизонтали
 * @param mapHeight   количество карт по вертикали
 * @param blockIds    все уникальные blockId, встреченные в схеме (для добавления в палитру)
 */
public record SchematicImportResult(
	BlockData[][] blocks,
	int[][] topLevels,
	int mapWidth,
	int mapHeight,
	Set<String> blockIds
) {}
