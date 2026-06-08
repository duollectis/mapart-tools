package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtList;
import net.minecraft.nbt.utils.NbtIo;
import org.duollectis.mapart.tools.converter.BlockData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Базовый класс для экспортёров схематик.
 * Хранит палитру блоков, предоставляет общие утилиты построения NBT
 * и реализует сохранение через {@link NbtIo#writeCompressed}.
 * Конкретный формат файла определяется в подклассах через {@link #buildRootNbt()}.
 */
public abstract class SchematicWriter {

	protected static final String AUTHOR = "Duollectis Mapart Tools By Applee453";

	protected final List<BlockData> paletteList = new ArrayList<>();
	protected final Map<BlockData, Integer> paletteIndex = new HashMap<>();

	public abstract void setBlock(int x, int y, int z, BlockData block);

	public final void save(Path path) throws Exception {
		NbtIo.writeCompressed(buildRootNbt(), path);
	}

	protected abstract NbtCompound buildRootNbt();

	/**
	 * Регистрирует блок в палитре (если ещё не зарегистрирован) и возвращает его индекс.
	 */
	protected int resolveIndex(BlockData block) {
		return paletteIndex.computeIfAbsent(block, b -> {
			paletteList.add(b);
			return paletteList.size() - 1;
		});
	}

	/**
	 * Строит NBT-список палитры в формате {@code [{Name, Properties?}, ...]}.
	 * Используется как в NBT (.nbt), так и в Litematica (.litematic).
	 */
	protected NbtList buildPaletteNbt() {
		NbtList nbtPalette = new NbtList();

		for (BlockData block : paletteList) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Name", block.getId());

			Map<String, String> props = block.getProperties();

			if (!props.isEmpty()) {
				NbtCompound nbtProps = new NbtCompound();
				props.forEach(nbtProps::putString);
				entry.put("Properties", nbtProps);
			}

			nbtPalette.add(entry);
		}

		return nbtPalette;
	}

	protected static NbtCompound buildVectorNbt(int x, int y, int z) {
		NbtCompound vec = new NbtCompound();
		vec.putInt("x", x);
		vec.putInt("y", y);
		vec.putInt("z", z);
		return vec;
	}
}
