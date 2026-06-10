package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtList;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.BlockLeveler;

import java.util.ArrayList;
import java.util.List;

/**
 * Читает схематику формата Structure Block (.nbt).
 * Структура: root → size[x,y,z], palette[{Name, Properties?}], blocks[{state, pos[x,y,z]}].
 */
public class NbtSchematicReader extends SchematicReader {

	private static final String AIR_ID = "minecraft:air";

	@Override
	protected SchematicImportResult parse(NbtCompound root) {
		NbtList sizeNbt = root.getList("size", 3);
		int sizeX = sizeNbt.getInt(0);
		int sizeY = sizeNbt.getInt(1);
		int sizeZ = sizeNbt.getInt(2);

		List<BlockData> palette = parsePalette(root.getList("palette", 10));
		BlockData[][][] volume = buildVolume(root.getList("blocks", 10), palette, sizeX, sizeY, sizeZ);

		BlockLeveler.TopLayerResult topLayerResult = BlockLeveler.readTopLayer(volume, sizeX, sizeY, sizeZ);
		int[] mapCount = computeMapCount(sizeX, sizeZ - 1);

		return new SchematicImportResult(
			topLayerResult.blocks(),
			topLayerResult.levels(),
			mapCount[0],
			mapCount[1],
			collectBlockIds(palette)
		);
	}

	private static List<BlockData> parsePalette(NbtList paletteNbt) {
		List<BlockData> palette = new ArrayList<>();

		for (int i = 0; i < paletteNbt.size(); i++) {
			palette.add(parseBlockEntry(paletteNbt.getCompound(i)));
		}

		return palette;
	}

	private static BlockData[][][] buildVolume(
		NbtList blocksNbt,
		List<BlockData> palette,
		int sizeX,
		int sizeY,
		int sizeZ
	) {
		BlockData[][][] volume = new BlockData[sizeY][sizeZ][sizeX];
		BlockData air = new BlockData(AIR_ID);

		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					volume[y][z][x] = air;
				}
			}
		}

		for (int i = 0; i < blocksNbt.size(); i++) {
			NbtCompound entry = blocksNbt.getCompound(i);
			int stateIndex = entry.getInt("state");
			NbtList pos = entry.getList("pos", 3);

			int x = pos.getInt(0);
			int y = pos.getInt(1);
			int z = pos.getInt(2);

			if (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ) {
				volume[y][z][x] = palette.get(stateIndex);
			}
		}

		return volume;
	}
}
