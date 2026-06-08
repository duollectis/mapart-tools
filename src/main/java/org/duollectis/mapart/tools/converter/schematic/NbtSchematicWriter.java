package org.duollectis.mapart.tools.converter.schematic;

import lombok.AllArgsConstructor;
import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtInt;
import net.minecraft.nbt.elements.NbtList;
import org.duollectis.mapart.tools.converter.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Экспортёр схематики в формат Structure Block (.nbt).
 * Хранит блоки как список позиций со ссылкой на индекс палитры.
 */
public class NbtSchematicWriter extends SchematicWriter {

	// Версия данных 1.21.1 — чтобы Майнкрафт не ругался на несовместимость формата
	private static final int DATA_VERSION = 3463;

	private final int width;
	private final int height;
	private final int length;

	private final List<PlacedBlock> blocks = new ArrayList<>();

	public NbtSchematicWriter(int width, int height, int length) {
		this.width = width;
		this.height = height;
		this.length = length;
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData block) {
		if (x < 0 || x >= width) {
			throw new RuntimeException("X выходит за границы схематики: %s".formatted(x));
		}

		if (y < 0 || y >= height) {
			throw new RuntimeException("Y выходит за границы схематики: %s".formatted(y));
		}

		if (z < 0 || z >= length) {
			throw new RuntimeException("Z выходит за границы схематики: %s".formatted(z));
		}

		blocks.add(new PlacedBlock(resolveIndex(block), x, y, z));
	}

	@Override
	protected NbtCompound buildRootNbt() {
		NbtCompound root = new NbtCompound();
		root.putString("author", AUTHOR);
		root.putInt("DataVersion", DATA_VERSION);
		root.put("size", buildSizeNbt());
		root.put("palette", buildPaletteNbt());
		root.put("blocks", buildBlocksNbt());
		return root;
	}

	private NbtList buildSizeNbt() {
		NbtList size = new NbtList();
		size.add(NbtInt.of(width));
		size.add(NbtInt.of(height));
		size.add(NbtInt.of(length));
		return size;
	}

	private NbtList buildBlocksNbt() {
		NbtList nbtBlocks = new NbtList();

		for (PlacedBlock block : blocks) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("state", block.id);

			NbtList pos = new NbtList();
			pos.add(NbtInt.of(block.x));
			pos.add(NbtInt.of(block.y));
			pos.add(NbtInt.of(block.z));

			entry.put("pos", pos);
			nbtBlocks.add(entry);
		}

		return nbtBlocks;
	}

	@AllArgsConstructor
	private static class PlacedBlock {

		private final int id;
		private final int x;
		private final int y;
		private final int z;
	}
}
