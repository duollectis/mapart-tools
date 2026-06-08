package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtList;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.BlockLeveler;

import java.util.ArrayList;
import java.util.List;

/**
 * Читает схематику формата Litematica (.litematic).
 * <p>
 * Структура: {@code Regions.{name}.BlockStatePalette}, {@code BlockStates} (packed long[]),
 * {@code Size} → {x, y, z}. Индекс блока: {@code x + z*sizeX + y*sizeX*sizeZ}.
 * Биты упакованы без переноса через границу long:
 * {@code bitsPerBlock = max(4, ceil(log2(paletteSize)))}.
 */
public class LitematicSchematicReader extends SchematicReader {

	private static final int MIN_BITS_PER_BLOCK = 4;

	@Override
	protected SchematicImportResult parse(NbtCompound root) {
		NbtCompound regions = root.getCompound("Regions");
		String regionName = regions.getKeys().iterator().next();
		NbtCompound region = regions.getCompound(regionName);

		NbtCompound sizeNbt = region.getCompound("Size");
		int sizeX = Math.abs(sizeNbt.getInt("x"));
		int sizeY = Math.abs(sizeNbt.getInt("y"));
		int sizeZ = Math.abs(sizeNbt.getInt("z"));

		List<BlockData> palette = parsePalette(region.getList("BlockStatePalette", 10));
		long[] packedStates = region.getLongArray("BlockStates");

		BlockData[][][] volume = unpackVolume(packedStates, palette, sizeX, sizeY, sizeZ);

		BlockData[][] topLayer = BlockLeveler.readTopLayer(volume, sizeX, sizeY, sizeZ);
		int[] mapCount = computeMapCount(sizeX, sizeZ - 1);

		return new SchematicImportResult(topLayer, mapCount[0], mapCount[1], collectBlockIds(palette));
	}

	private static List<BlockData> parsePalette(NbtList paletteNbt) {
		List<BlockData> palette = new ArrayList<>();

		for (int i = 0; i < paletteNbt.size(); i++) {
			palette.add(parseBlockEntry(paletteNbt.getCompound(i)));
		}

		return palette;
	}

	/**
	 * Распаковывает packed long[] в трёхмерный объём блоков.
	 * Каждый long хранит несколько индексов без переноса через границу long.
	 * Индекс блока в массиве: {@code x + z*sizeX + y*sizeX*sizeZ}.
	 */
	private static BlockData[][][] unpackVolume(
		long[] packedStates,
		List<BlockData> palette,
		int sizeX,
		int sizeY,
		int sizeZ
	) {
		int paletteSize = Math.max(1, palette.size());
		int bitsPerBlock = Math.max(MIN_BITS_PER_BLOCK, ceilLog2(paletteSize));
		int blocksPerLong = 64 / bitsPerBlock;
		long mask = (1L << bitsPerBlock) - 1;

		int totalBlocks = sizeX * sizeY * sizeZ;
		BlockData air = palette.isEmpty() ? new BlockData("minecraft:air") : palette.getFirst();
		BlockData[][][] volume = new BlockData[sizeY][sizeZ][sizeX];

		for (int i = 0; i < totalBlocks; i++) {
			int longIndex = i / blocksPerLong;
			int bitOffset = (i % blocksPerLong) * bitsPerBlock;

			int paletteIndex = longIndex < packedStates.length
				? (int) ((packedStates[longIndex] >> bitOffset) & mask)
				: 0;

			BlockData block = paletteIndex < palette.size() ? palette.get(paletteIndex) : air;

			// Индекс i = x + z*sizeX + y*sizeX*sizeZ → обратное преобразование
			int x = i % sizeX;
			int remainder = i / sizeX;
			int z = remainder % sizeZ;
			int y = remainder / sizeZ;

			volume[y][z][x] = block;
		}

		return volume;
	}

	private static int ceilLog2(int value) {
		return value <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(value - 1);
	}
}
