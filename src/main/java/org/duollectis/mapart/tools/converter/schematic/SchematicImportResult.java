package org.duollectis.mapart.tools.converter.schematic;

import org.duollectis.mapart.tools.converter.BlockData;

import java.util.Set;

/**
 * Результат импорта схематики.
 * Содержит верхний слой блоков карты (128×128 на карту) и набор уникальных blockId из схемы.
 *
 * @param blocks      двумерный массив блоков верхнего слоя [y][x], размер mapHeight*128 × mapWidth*128
 * @param mapWidth    количество карт по горизонтали
 * @param mapHeight   количество карт по вертикали
 * @param blockIds    все уникальные blockId, встреченные в схеме (для добавления в палитру)
 */
public record SchematicImportResult(
	BlockData[][] blocks,
	int mapWidth,
	int mapHeight,
	Set<String> blockIds
) {}
