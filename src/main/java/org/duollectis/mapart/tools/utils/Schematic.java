package org.duollectis.mapart.tools.utils;

import lombok.AllArgsConstructor;
import lombok.Setter;
import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtInt;
import net.minecraft.nbt.elements.NbtList;
import net.minecraft.nbt.utils.NbtIo;
import org.duollectis.mapart.tools.converter.BlockData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Schematic {

	private static final String AUTHOR = "Duollectis Mapart Tools By Applee453";
	// Версия данных 1.21.1 — чтобы Майнкрафт не ругался на несовместимость формата
	private static final int DATA_VERSION = 3463;

	@Setter
	private int width;

	@Setter
	private int height;

	@Setter
	private int length;

	// Список для сохранения порядка (индекс = state в NBT)
	private final List<BlockData> paletteList = new ArrayList<>();
	// HashMap для O(1) поиска индекса блока в палитре
	private final Map<BlockData, Integer> paletteIndex = new HashMap<>();
	private final List<Block> blocks = new ArrayList<>();

	public Schematic(int width, int height, int length) {
		this.width = width;
		this.height = height;
		this.length = length;
	}

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

		// computeIfAbsent гарантирует O(1) и добавляет блок в список только при первом появлении
		int index = paletteIndex.computeIfAbsent(block, b -> {
			paletteList.add(b);
			return paletteList.size() - 1;
		});

		blocks.add(new Block(index, x, y, z));
	}

	public void save(Path path) throws Exception {
		NbtIo.writeCompressed(buildRootNbt(), path);
	}

	private NbtCompound buildRootNbt() {
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

	private NbtList buildPaletteNbt() {
		NbtList nbtPalette = new NbtList();

		for (BlockData block : paletteList) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Name", block.getId());
			nbtPalette.add(entry);
		}

		return nbtPalette;
	}

	private NbtList buildBlocksNbt() {
		NbtList nbtBlocks = new NbtList();

		for (Block block : blocks) {
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
	private static class Block {

		private final int id;
		private final int x;
		private final int y;
		private final int z;
	}
}
