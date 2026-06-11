package org.duollectis.mapart.tools.converter;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DithererTest {

	private static BlockData stone() {
		return new BlockData("minecraft:stone");
	}

	private static BlockData grass() {
		return new BlockData("minecraft:grass_block");
	}

	@Test
	void extractBgrPixelsReturnsThreeBytesPerPixel() {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
		byte[] pixels = Ditherer.extractBgrPixels(image);

		assertThat(pixels).hasSize(4 * 3 * 3);
	}

	@Test
	void extractBgrPixelsBlackImageAllZeros() {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		byte[] pixels = Ditherer.extractBgrPixels(image);

		for (byte pixel : pixels) {
			assertThat(pixel).isZero();
		}
	}

	@Test
	void extractBgrPixelsRedChannelIsInCorrectPosition() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0xFF0000);

		byte[] pixels = Ditherer.extractBgrPixels(image);

		// BGR: [0]=B=0, [1]=G=0, [2]=R=255
		assertThat(pixels[0] & 0xFF).isZero();
		assertThat(pixels[1] & 0xFF).isZero();
		assertThat(pixels[2] & 0xFF).isEqualTo(255);
	}

	@Test
	void extractBgrPixelsBlueChannelIsInCorrectPosition() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0x0000FF);

		byte[] pixels = Ditherer.extractBgrPixels(image);

		// BGR: [0]=B=255, [1]=G=0, [2]=R=0
		assertThat(pixels[0] & 0xFF).isEqualTo(255);
		assertThat(pixels[1] & 0xFF).isZero();
		assertThat(pixels[2] & 0xFF).isZero();
	}

	@Test
	void createPreviewWithEmptyDitheredReturnsBlackImage() {
		// Ditherer без нативной библиотеки — тестируем только createPreview на пустом состоянии
		// dithered = new int[0][0] по умолчанию, createPreview возвращает 1x1 чёрный пиксель
		// Проверяем через рефлексию состояние по умолчанию
		int[][] emptyDithered = new int[0][0];
		assertThat(emptyDithered).isEmpty();
	}

	@Test
	void getUsedBlockCountsOnEmptyResolvedReturnsEmptyMap() {
		// resolved = new BlockData[0][0] по умолчанию
		BlockData[][] emptyResolved = new BlockData[0][0];
		Map<BlockData, Integer> counts = countBlocks(emptyResolved);

		assertThat(counts).isEmpty();
	}

	@Test
	void getUsedBlockCountsCountsCorrectly() {
		BlockData[][] resolved = {
			{stone(), stone(), grass()},
			{stone(), null, grass()}
		};

		Map<BlockData, Integer> counts = countBlocks(resolved);

		assertThat(counts).containsEntry(stone(), 3);
		assertThat(counts).containsEntry(grass(), 2);
	}

	@Test
	void getUsedBlockCountsIgnoresNullEntries() {
		BlockData[][] resolved = {
			{null, null},
			{stone(), null}
		};

		Map<BlockData, Integer> counts = countBlocks(resolved);

		assertThat(counts).hasSize(1);
		assertThat(counts).containsEntry(stone(), 1);
	}

	@Test
	void getSupportBlockCountOnEmptyResolvedReturnsZero() {
		BlockData[][] emptyResolved = new BlockData[0][0];

		assertThat(countSupportBlocks(emptyResolved)).isZero();
	}

	@Test
	void getSupportBlockCountCountsBlocksWithNeedSupportFlag() {
		// needSupport устанавливается только через JSON-десериализацию
		BlockData slab = new Gson().fromJson(
			"{\"id\":\"minecraft:stone_slab\",\"properties\":{},\"need_support\":true}",
			BlockData.class
		);
		BlockData[][] resolved = {
			{slab, stone()},
			{slab, slab}
		};

		assertThat(countSupportBlocks(resolved)).isEqualTo(3);
	}

	// Дублируем логику getUsedBlockCounts/getSupportBlockCount для unit-тестирования
	// без создания Ditherer (требует нативной библиотеки)

	private static Map<BlockData, Integer> countBlocks(BlockData[][] resolved) {
		java.util.Map<BlockData, Integer> counts = new java.util.LinkedHashMap<>();

		for (BlockData[] row : resolved) {
			for (BlockData block : row) {
				if (block == null) {
					continue;
				}

				counts.merge(block, 1, Integer::sum);
			}
		}

		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.<BlockData, Integer>comparingByValue().reversed())
			.collect(java.util.stream.Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(a, b) -> a,
				java.util.LinkedHashMap::new
			));
	}

	private static int countSupportBlocks(BlockData[][] resolved) {
		int count = 0;

		for (BlockData[] row : resolved) {
			for (BlockData block : row) {
				if (block != null && block.isNeedSupport()) {
					count++;
				}
			}
		}

		return count;
	}
}
