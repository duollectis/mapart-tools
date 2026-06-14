package org.duollectis.mapart.tools.converter;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.utils.NbtIo;

import java.io.File;
import java.util.List;

/**
 * Записывает результат дизеринга в формат карты Minecraft (.dat).
 *
 * <p>Структура файла: gzip-сжатый NBT вида:
 * <pre>
 * TAG_Compound("") {
 *     TAG_Compound("data") {
 *         TAG_Int("xCenter")           = 0
 *         TAG_Int("zCenter")           = 0
 *         TAG_Byte("scale")            = 0
 *         TAG_Byte("trackingPosition") = 0
 *         TAG_Byte("locked")           = 1
 *         TAG_String("dimension")      = "minecraft:overworld"
 *         TAG_Byte_Array("colors")     = byte[16384]  // 128×128 map color IDs
 *     }
 *     TAG_Int("DataVersion") = 4325  // 1.21.4
 * }
 * </pre>
 *
 * <p>Каждый байт в массиве {@code colors} — идентификатор цвета карты:
 * {@code (baseColorId << 2) | brightness.ordinal()}, где ordinal: LOW=0, NORMAL=1, HIGH=2.
 */
public final class MapDatWriter {

	private static final int MAP_SIZE = 128;
	private static final int COLORS_ARRAY_SIZE = MAP_SIZE * MAP_SIZE;
	private static final int DATA_VERSION = 4325;
	private static final String DIMENSION = "minecraft:overworld";

	private MapDatWriter() {}

	public static void write(
		int[][] dithered,
		List<PaletteEntry> palette,
		File outDir,
		int mapWidth,
		int mapHeight,
		int startId
	) throws Exception {
		for (int my = 0; my < mapHeight; my++) {
			for (int mx = 0; mx < mapWidth; mx++) {
				byte[] colors = buildColorsArray(dithered, palette, mx, my);
				int mapId = startId + my * mapWidth + mx;
				File outFile = new File(outDir, "map_%d.dat".formatted(mapId));
				NbtIo.writeCompressed(buildNbt(colors), outFile.toPath());
			}
		}
	}

	private static byte[] buildColorsArray(
		int[][] dithered,
		List<PaletteEntry> palette,
		int mx,
		int my
	) {
		byte[] colors = new byte[COLORS_ARRAY_SIZE];
		int startX = mx * MAP_SIZE;
		int startY = my * MAP_SIZE;

		for (int row = 0; row < MAP_SIZE; row++) {
			for (int col = 0; col < MAP_SIZE; col++) {
				int paletteIndex = dithered[startY + row][startX + col];
				int mapColorId = palette.get(paletteIndex).getMapColorId();
				// Байт знаковый в Java — значения > 127 записываются как отрицательные
				colors[row * MAP_SIZE + col] = (byte) mapColorId;
			}
		}

		return colors;
	}

	private static NbtCompound buildNbt(byte[] colors) {
		NbtCompound data = new NbtCompound();
		data.putInt("xCenter", 0);
		data.putInt("zCenter", 0);
		data.putByte("scale", (byte) 0);
		data.putByte("trackingPosition", (byte) 0);
		data.putByte("locked", (byte) 1);
		data.putString("dimension", DIMENSION);
		data.putByteArray("colors", colors);

		NbtCompound root = new NbtCompound();
		root.put("data", data);
		root.putInt("DataVersion", DATA_VERSION);

		return root;
	}
}
