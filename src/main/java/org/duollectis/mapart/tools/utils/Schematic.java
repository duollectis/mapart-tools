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
import java.util.List;
import java.util.Map;

public class Schematic {

	@Setter
	private int width, height, length;

	private final List<BlockData> palette = new ArrayList<>();

	private final List<Block> blocks = new ArrayList<>();

	public Schematic(int width, int height, int length) {
		this.width = width;
		this.height = height;
		this.length = length;
	}

	public void setBlock(int x, int y, int z, BlockData block) {
		if (x < 0 || x >= width) {
			throw new RuntimeException("X out of bounds! %s".formatted(x));
		}

		if (y < 0 || y >= height) {
			throw new RuntimeException("Y out of bounds! %s".formatted(y));
		}

		if (z < 0 || z >= length) {
			throw new RuntimeException("Z out of bounds! %s".formatted(z));
		}

		if (!palette.contains(block)) {
			palette.add(block);
		}

		blocks.add(new Block(palette.indexOf(block), x, y, z));
	}

	public void save(Path path) throws Exception {
		NbtCompound root = new NbtCompound();
		root.putString("author", "Duollectis Mapart Tools By Applee453");
		root.putInt("DataVersion", 3463); // Чтобы Майн не ругался на версию

		NbtList size = new NbtList();
		root.put("size", size);
		size.add(NbtInt.of(width));
		size.add(NbtInt.of(height));
		size.add(NbtInt.of(length));


		// 2. Палитра
		NbtList nbtPalette = new NbtList();
		root.put("palette", nbtPalette);

		for (BlockData block : palette) {
			NbtCompound b = new NbtCompound();
			nbtPalette.add(b);

			b.putString("Name", block.getId());
		}

		NbtList _blocks = new NbtList();
		root.put("blocks", _blocks);

		for (Block block : blocks) {
			NbtCompound b = new NbtCompound();
			_blocks.add(b);

			b.putInt("state", block.id);
			NbtList pos = new NbtList();
			b.put("pos", pos);
			pos.add(NbtInt.of(block.x));
			pos.add(NbtInt.of(block.y));
			pos.add(NbtInt.of(block.z));
		}

		NbtIo.writeCompressed(root, path);


	}

	@AllArgsConstructor
	private static class Block {

		private final int id;
		private final int x;
		private final int y;
		private final int z;
	}
}