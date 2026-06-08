package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.utils.NbtIo;
import net.minecraft.nbt.utils.NbtSizeTracker;
import org.duollectis.mapart.tools.converter.BlockData;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Базовый класс для чтения схематик карт-артов.
 * Читает NBT из файла и делегирует парсинг конкретному формату.
 * Извлекает верхний слой блоков (Y=1..128 в схеме) для рендера превью.
 */
public abstract class SchematicReader {

	private static final int MAP_SIZE = 128;

	/**
	 * Читает схематику из файла и возвращает результат импорта.
	 *
	 * @param file файл схематики (.nbt или .litematic)
	 * @return результат с верхним слоем блоков и метаданными
	 */
	public SchematicImportResult read(File file) throws Exception {
		NbtCompound root = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
		return parse(root);
	}

	protected abstract SchematicImportResult parse(NbtCompound root);

	/**
	 * Парсит один блок из NBT-записи палитры (формат {Name, Properties?}).
	 */
	protected static BlockData parseBlockEntry(NbtCompound entry) {
		String name = entry.getString("Name");

		if (!entry.contains("Properties")) {
			return new BlockData(name);
		}

		NbtCompound propsNbt = entry.getCompound("Properties");
		Map<String, String> props = new HashMap<>();

		for (String key : propsNbt.getKeys()) {
			props.put(key, propsNbt.getString(key));
		}

		return new BlockData(name, props);
	}

	/**
	 * Собирает набор уникальных blockId из списка блоков палитры.
	 */
	protected static Set<String> collectBlockIds(List<BlockData> palette) {
		Set<String> ids = new HashSet<>();

		for (BlockData block : palette) {
			ids.add(block.getId());
		}

		return ids;
	}

	/**
	 * Вычисляет количество карт по горизонтали и вертикали из размеров схемы.
	 * Схема может содержать несколько карт (например, map_1_1, map_2_1 и т.д.).
	 */
	protected static int[] computeMapCount(int sizeX, int sizeZ) {
		int mapWidth = Math.max(1, (sizeX + MAP_SIZE - 1) / MAP_SIZE);
		int mapHeight = Math.max(1, (sizeZ + MAP_SIZE - 1) / MAP_SIZE);
		return new int[]{mapWidth, mapHeight};
	}
}
