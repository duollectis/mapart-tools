package org.duollectis.mapart.tools.converter.schematic;

import net.minecraft.nbt.elements.NbtCompound;
import net.minecraft.nbt.elements.NbtList;
import org.duollectis.mapart.tools.converter.BlockData;

import java.util.Map;

/**
 * Экспортёр схематики в формат Litematica (.litematic).
 * <p>
 * Порядок блоков в {@code BlockStates}: {@code index = x + z*sizeX + y*sizeX*sizeZ}.
 * Биты упакованы в {@code long[]} без переноса через границу long:
 * {@code bitsPerBlock = max(4, ceil(log2(paletteSize)))}.
 * <p>
 * Воздух регистрируется первым (индекс 0), чтобы незаполненные ячейки
 * массива blockIndices корректно интерпретировались как воздух.
 */
public class LitematicSchematicWriter extends SchematicWriter {

	private static final int MINECRAFT_DATA_VERSION = 4671;
	private static final int LITEMATIC_VERSION = 7;
	private static final int LITEMATIC_SUB_VERSION = 1;
	private static final int MIN_BITS_PER_BLOCK = 4;
	private static final String AIR_BLOCK_ID = "minecraft:air";

	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final String regionName;
	private final int[] blockIndices;

	public LitematicSchematicWriter(int sizeX, int sizeY, int sizeZ, String regionName) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.regionName = regionName;
		this.blockIndices = new int[sizeX * sizeY * sizeZ];

		// Воздух должен быть первым в палитре (индекс 0),
		// чтобы нули в blockIndices означали воздух, а не случайный блок
		resolveIndex(new BlockData(AIR_BLOCK_ID));
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData block) {
		blockIndices[x + z * sizeX + y * sizeX * sizeZ] = resolveIndex(block);
	}

	@Override
	protected NbtCompound buildRootNbt() {
		long now = System.currentTimeMillis();

		NbtCompound root = new NbtCompound();
		root.putInt("MinecraftDataVersion", MINECRAFT_DATA_VERSION);
		root.putInt("Version", LITEMATIC_VERSION);
		root.putInt("SubVersion", LITEMATIC_SUB_VERSION);
		root.put("Metadata", buildMetadataNbt(now));
		root.put("Regions", buildRegionsNbt());

		return root;
	}

	private NbtCompound buildMetadataNbt(long timestamp) {
		NbtCompound meta = new NbtCompound();
		meta.putString("Name", regionName);
		meta.putString("Author", AUTHOR);
		meta.putString("Description", "");
		meta.putLong("TimeCreated", timestamp);
		meta.putLong("TimeModified", timestamp);
		meta.put("EnclosingSize", buildVectorNbt(sizeX, sizeY, sizeZ));
		meta.putInt("RegionCount", 1);
		meta.putInt("TotalBlocks", countNonAirBlocks());
		meta.putInt("TotalVolume", sizeX * sizeY * sizeZ);

		return meta;
	}

	private NbtCompound buildRegionsNbt() {
		NbtCompound regions = new NbtCompound();
		regions.put(regionName, buildRegionNbt());
		return regions;
	}

	private NbtCompound buildRegionNbt() {
		NbtCompound region = new NbtCompound();
		region.put("BlockStatePalette", buildPaletteNbt());
		region.putLongArray("BlockStates", packBlockStates());
		// Litematica кодирует направление региона знаком: отрицательный Z означает
		// что регион идёт в сторону убывания Z от точки Position
		region.put("Size", buildVectorNbt(sizeX, sizeY, -sizeZ));
		region.put("Position", buildVectorNbt(0, 0, sizeZ - 1));
		region.put("Entities", new NbtList());
		region.put("TileEntities", new NbtList());
		region.put("PendingBlockTicks", new NbtList());
		region.put("PendingFluidTicks", new NbtList());

		return region;
	}

	/**
	 * Упаковывает индексы блоков в long[] с переносом через границу long.
	 * Стандарт Minecraft 1.16+: индекс блока i занимает биты [i*bpp .. (i+1)*bpp),
	 * при этом один индекс может быть разбит между двумя соседними long-ами.
	 * Размер массива: {@code ceil(totalBlocks * bitsPerBlock / 64)}.
	 */
	private long[] packBlockStates() {
		int paletteSize = Math.max(1, paletteList.size());
		int bitsPerBlock = Math.max(MIN_BITS_PER_BLOCK, ceilLog2(paletteSize));
		int totalBlocks = sizeX * sizeY * sizeZ;
		int longCount = (totalBlocks * bitsPerBlock + 63) / 64;

		long[] longs = new long[longCount];
		long mask = (1L << bitsPerBlock) - 1;

		for (int i = 0; i < totalBlocks; i++) {
			int bitStart = i * bitsPerBlock;
			int longIndex = bitStart / 64;
			int bitOffset = bitStart % 64;
			long value = (long) blockIndices[i] & mask;

			longs[longIndex] |= value << bitOffset;

			int bitsInFirstLong = 64 - bitOffset;

			if (bitsInFirstLong < bitsPerBlock) {
				longs[longIndex + 1] |= value >>> bitsInFirstLong;
			}
		}

		return longs;
	}

	private int countNonAirBlocks() {
		int airIndex = paletteIndex.entrySet().stream()
			.filter(e -> e.getKey().getId().equals("minecraft:air"))
			.mapToInt(Map.Entry::getValue)
			.findFirst()
			.orElse(-1);

		int count = 0;

		for (int idx : blockIndices) {
			if (idx != airIndex) {
				count++;
			}
		}

		return count;
	}

	private static int ceilLog2(int value) {
		return value <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(value - 1);
	}
}
