package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.BlockLeveler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Читает схематику формата Sponge Schematic v2 и v3 (.schem).
 * <p>
 * Версия определяется автоматически по тегу {@code Version} в корне:
 * <ul>
 *   <li>v3: данные вложены в {@code root.Schematic}, палитра в {@code Blocks.Palette}</li>
 *   <li>v2: данные в корне напрямую, палитра в {@code Palette}, данные в {@code BlockData}</li>
 * </ul>
 * Порядок блоков в обоих версиях: {@code index = x + z*Width + y*Width*Length} (XZY).
 */
public class SpongeSchematicReader extends SchematicReader {

	private static final String AIR_ID = "minecraft:air";
	private static final int SPONGE_V3 = 3;

	@Override
	protected SchematicImportResult parse(NbtCompound root) {
		int version = root.contains("Version") ? root.getInt("Version") : SPONGE_V3;

		return version >= SPONGE_V3
				? parseV3(root)
				: parseV2(root);
	}

	private SchematicImportResult parseV3(NbtCompound root) {
		NbtCompound schematic = root.getCompound("Schematic");

		int width = schematic.getShort("Width") & 0xFFFF;
		int height = schematic.getShort("Height") & 0xFFFF;
		int length = schematic.getShort("Length") & 0xFFFF;

		NbtCompound blocksNbt = schematic.getCompound("Blocks");
		List<BlockData> palette = parsePalette(blocksNbt.getCompound("Palette"));
		byte[] rawData = blocksNbt.getByteArray("Data");

		return buildResult(rawData, palette, width, height, length);
	}

	/**
	 * Парсит Sponge Schematic v2: данные и палитра находятся непосредственно в корне.
	 * Ключ данных — {@code "BlockData"} (ByteArray/VarInt), палитра — {@code "Palette"} (Compound).
	 */
	private SchematicImportResult parseV2(NbtCompound root) {
		int width = root.getShort("Width") & 0xFFFF;
		int height = root.getShort("Height") & 0xFFFF;
		int length = root.getShort("Length") & 0xFFFF;

		List<BlockData> palette = parsePalette(root.getCompound("Palette"));
		byte[] rawData = root.getByteArray("BlockData");

		return buildResult(rawData, palette, width, height, length);
	}

	private SchematicImportResult buildResult(
		byte[] rawData,
		List<BlockData> palette,
		int width,
		int height,
		int length
	) {
		BlockData[][][] volume = buildVolume(rawData, palette, width, height, length);

		BlockLeveler.TopLayerResult topLayerResult = BlockLeveler.readTopLayer(volume, width, height, length);
		int[] mapCount = computeMapCount(width, length - 1);

		return new SchematicImportResult(
			topLayerResult.blocks(),
			topLayerResult.levels(),
			mapCount[0],
			mapCount[1],
			collectBlockIds(palette)
		);
	}

	/**
	 * Парсит палитру Sponge: Compound с ключами вида {@code "minecraft:block[prop=val]"} → Int-индекс.
	 * Результирующий список индексируется по значению Int из палитры.
	 */
	private static List<BlockData> parsePalette(NbtCompound paletteNbt) {
		Map<Integer, BlockData> indexToBlock = new HashMap<>();

		for (String blockStateStr : paletteNbt.getKeys()) {
			int index = paletteNbt.getInt(blockStateStr);
			indexToBlock.put(index, parseBlockStateString(blockStateStr));
		}

		int maxIndex = indexToBlock.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
		List<BlockData> palette = new ArrayList<>(maxIndex + 1);
		BlockData air = new BlockData(AIR_ID);

		for (int i = 0; i <= maxIndex; i++) {
			palette.add(indexToBlock.getOrDefault(i, air));
		}

		return palette;
	}

	/**
	 * Строит трёхмерный объём блоков из ByteArray.
	 * Поддерживает как однобайтовое кодирование (палитра ≤ 127),
	 * так и VarInt-кодирование (палитра > 127, байты со старшим битом = 1).
	 */
	private static BlockData[][][] buildVolume(
		byte[] data,
		List<BlockData> palette,
		int width,
		int height,
		int length
	) {
		BlockData[][][] volume = new BlockData[height][length][width];
		BlockData air = new BlockData(AIR_ID);
		int dataOffset = 0;

		for (int y = 0; y < height; y++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					if (dataOffset >= data.length) {
						volume[y][z][x] = air;
						continue;
					}

					int paletteIndex;
					int firstByte = data[dataOffset] & 0xFF;

					if ((firstByte & 0x80) == 0) {
						paletteIndex = firstByte;
						dataOffset++;
					} else {
						int[] result = readVarInt(data, dataOffset);
						paletteIndex = result[0];
						dataOffset = result[1];
					}

					volume[y][z][x] = paletteIndex < palette.size() ? palette.get(paletteIndex) : air;
				}
			}
		}

		return volume;
	}

	/**
	 * Читает VarInt из байтового массива начиная с {@code offset}.
	 *
	 * @return int[2]: [0] — значение, [1] — новый offset после прочитанных байт
	 */
	private static int[] readVarInt(byte[] data, int offset) {
		int value = 0;
		int shift = 0;

		while (offset < data.length) {
			int b = data[offset++] & 0xFF;
			value |= (b & 0x7F) << shift;
			shift += 7;

			if ((b & 0x80) == 0) {
				break;
			}
		}

		return new int[]{value, offset};
	}

	/**
	 * Парсит строку blockstate формата Sponge в {@link BlockData}.
	 * Примеры: {@code "minecraft:air"}, {@code "minecraft:oak_slab[type=top,waterlogged=false]"}.
	 */
	private static BlockData parseBlockStateString(String blockState) {
		int bracketPos = blockState.indexOf('[');

		if (bracketPos < 0) {
			return new BlockData(blockState);
		}

		String id = blockState.substring(0, bracketPos);
		String propsStr = blockState.substring(bracketPos + 1, blockState.length() - 1);
		Map<String, String> props = new HashMap<>();

		for (String pair : propsStr.split(",")) {
			int eqPos = pair.indexOf('=');

			if (eqPos > 0) {
				props.put(pair.substring(0, eqPos), pair.substring(eqPos + 1));
			}
		}

		return new BlockData(id, props);
	}
}
