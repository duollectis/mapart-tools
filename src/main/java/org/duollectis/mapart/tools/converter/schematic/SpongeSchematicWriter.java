package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtList;
import org.duollectis.mapart.tools.converter.BlockData;

import java.util.Map;

/**
 * Экспортёр схематики в формат Sponge Schematic v3 (.schem).
 * <p>
 * Структура файла:
 * <pre>
 * root (Compound "")
 *   Schematic (Compound)
 *     Version: Int = 3
 *     DataVersion: Int = 4671
 *     Metadata: Compound
 *     Width: Short
 *     Height: Short
 *     Length: Short
 *     Offset: IntArray[3] = {0, 0, 0}
 *     Blocks: Compound
 *       Palette: Compound  — "minecraft:block[prop=val]" → Int (индекс)
 *       Data: ByteArray    — один байт на блок (VarInt при палитре > 127)
 *       BlockEntities: List<Compound>
 * </pre>
 * Порядок блоков в Data: {@code index = x + z*Width + y*Width*Length} (XZY).
 */
public class SpongeSchematicWriter extends SchematicWriter {

	private static final int SPONGE_VERSION = 3;
	private static final int MINECRAFT_DATA_VERSION = 4671;
	private static final int VARINT_THRESHOLD = 127;

	private final int width;
	private final int height;
	private final int length;
	private final String name;
	private final int[] blockData;

	public SpongeSchematicWriter(int width, int height, int length, String name) {
		this.width = width;
		this.height = height;
		this.length = length;
		this.name = name;
		this.blockData = new int[width * height * length];

		resolveIndex(new BlockData("minecraft:air"));
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData block) {
		blockData[x + z * width + y * width * length] = resolveIndex(block);
	}

	@Override
	protected NbtCompound buildRootNbt() {
		NbtCompound schematic = new NbtCompound();
		schematic.putInt("Version", SPONGE_VERSION);
		schematic.putInt("DataVersion", MINECRAFT_DATA_VERSION);
		schematic.put("Metadata", buildMetadataNbt());
		schematic.putShort("Width", (short) width);
		schematic.putShort("Height", (short) height);
		schematic.putShort("Length", (short) length);
		schematic.putIntArray("Offset", new int[]{0, 0, 0});
		schematic.put("Blocks", buildBlocksNbt());

		NbtCompound root = new NbtCompound();
		root.put("Schematic", schematic);

		return root;
	}

	private NbtCompound buildMetadataNbt() {
		NbtCompound meta = new NbtCompound();
		meta.putLong("Date", System.currentTimeMillis());
		meta.putString("Name", name);
		meta.putString("Author", AUTHOR);
		return meta;
	}

	private NbtCompound buildBlocksNbt() {
		NbtCompound blocks = new NbtCompound();
		blocks.put("Palette", buildSpongePaletteNbt());
		blocks.putByteArray("Data", encodeData());
		blocks.put("BlockEntities", new NbtList());
		return blocks;
	}

	/**
	 * Строит палитру Sponge: ключ — строка blockstate вида {@code "minecraft:block[prop=val,...]"},
	 * значение — Int-индекс. Порядок соответствует {@link #paletteList}.
	 */
	private NbtCompound buildSpongePaletteNbt() {
		NbtCompound palette = new NbtCompound();

		for (int i = 0; i < paletteList.size(); i++) {
			palette.putInt(toBlockStateString(paletteList.get(i)), i);
		}

		return palette;
	}

	/**
	 * Кодирует массив индексов блоков в байты.
	 * При палитре ≤ 127 — один байт на блок (прямое значение).
	 * При палитре > 127 — VarInt-кодирование (каждый индекс может занимать 1–5 байт).
	 */
	private byte[] encodeData() {
		if (paletteList.size() <= VARINT_THRESHOLD) {
			return encodeAsSingleBytes();
		}

		return encodeAsVarInts();
	}

	private byte[] encodeAsSingleBytes() {
		byte[] result = new byte[blockData.length];

		for (int i = 0; i < blockData.length; i++) {
			result[i] = (byte) blockData[i];
		}

		return result;
	}

	/**
	 * VarInt-кодирование для больших палитр (> 127 блоков).
	 * Каждый индекс кодируется как VarInt: 7 бит на байт, старший бит — признак продолжения.
	 */
	private byte[] encodeAsVarInts() {
		byte[] buffer = new byte[blockData.length * 5];
		int pos = 0;

		for (int value : blockData) {
			do {
				byte part = (byte) (value & 0x7F);
				value >>>= 7;

				if (value != 0) {
					part |= 0x80;
				}

				buffer[pos++] = part;
			} while (value != 0);
		}

		byte[] result = new byte[pos];
		System.arraycopy(buffer, 0, result, 0, pos);

		return result;
	}

	/**
	 * Преобразует {@link BlockData} в строку blockstate формата Sponge:
	 * {@code "minecraft:block"} или {@code "minecraft:block[prop1=val1,prop2=val2]"}.
	 * Properties сортируются по ключу для детерминированного вывода.
	 */
	private static String toBlockStateString(BlockData block) {
		Map<String, String> props = block.getProperties();

		if (props.isEmpty()) {
			return block.getId();
		}

		String propsStr = props.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(java.util.stream.Collectors.joining(","));

		return block.getId() + "[" + propsStr + "]";
	}
}
