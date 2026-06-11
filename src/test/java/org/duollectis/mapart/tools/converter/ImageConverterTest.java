package org.duollectis.mapart.tools.converter;

import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageConverterTest {

	private static final String PALETTE_JSON = """
		{
			"16711680": [{"id": "minecraft:red_wool", "properties": {}}],
			"65280":    [{"id": "minecraft:lime_wool", "properties": {}}],
			"255":      [{"id": "minecraft:blue_wool", "properties": {}}]
		}
		""";

	/** Создаёт конвертер без нативной библиотеки — только для тестирования loadPalette. */
	private static ImageConverter converterForTest() {
		return new ImageConverter(null);
	}

	// ─── loadPalette ───────────────────────────────────────────────────────────

	@Test
	void loadPalettePopulatesThreeBrightnessVariantsPerColor() {
		ImageConverter converter = converterForTest();
		Set<String> whitelist = Set.of("minecraft:red_wool", "minecraft:lime_wool", "minecraft:blue_wool");

		converter.loadPalette(PALETTE_JSON, whitelist, Map.of(), StaircaseMode.STAIRCASE);

		// 3 цвета × 3 яркости = 9 записей
		assertThat(converter.getPaletteSize()).isEqualTo(9);
	}

	@Test
	void loadPaletteFiltersBlocksNotInWhitelist() {
		ImageConverter converter = converterForTest();
		Set<String> whitelist = Set.of("minecraft:red_wool");

		converter.loadPalette(PALETTE_JSON, whitelist, Map.of(), StaircaseMode.STAIRCASE);

		// Только 1 цвет прошёл фильтр × 3 яркости = 3 записи
		assertThat(converter.getPaletteSize()).isEqualTo(3);
	}

	@Test
	void loadPaletteThrowsIfCalledTwice() {
		ImageConverter converter = converterForTest();
		Set<String> whitelist = Set.of("minecraft:red_wool");

		converter.loadPalette(PALETTE_JSON, whitelist, Map.of(), StaircaseMode.STAIRCASE);

		assertThatThrownBy(() -> converter.loadPalette(PALETTE_JSON, whitelist, Map.of(), StaircaseMode.STAIRCASE))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	void loadPaletteWithFlatModeUsesOnlyNormalBrightness() {
		ImageConverter converter = converterForTest();
		Set<String> whitelist = Set.of("minecraft:red_wool");

		converter.loadPalette(PALETTE_JSON, whitelist, Map.of(), StaircaseMode.FLAT);

		// FLAT допускает только NORMAL яркость → 1 цвет × 1 яркость = 1 запись
		assertThat(converter.getPaletteSize()).isEqualTo(1);
	}

	@Test
	void loadPaletteEmptyWhitelistProducesEmptyPalette() {
		ImageConverter converter = converterForTest();

		converter.loadPalette(PALETTE_JSON, Set.of(), Map.of(), StaircaseMode.STAIRCASE);

		assertThat(converter.getPaletteSize()).isZero();
	}

	// ─── renderPreview ─────────────────────────────────────────────────────────

	@Test
	void renderPreviewReturnsImageOfCorrectSize() {
		BlockData[][] blocks = {
			{new BlockData("minecraft:red_wool"), new BlockData("minecraft:lime_wool")},
			{new BlockData("minecraft:blue_wool"), new BlockData("minecraft:red_wool")}
		};
		int[][] topLevels = {{0, 0}, {5, 5}, {5, 5}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, topLevels, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		assertThat(preview.getWidth()).isEqualTo(2);
		assertThat(preview.getHeight()).isEqualTo(2);
	}

	@Test
	void renderPreviewNullBlockRendersGray() {
		BlockData[][] blocks = {{null}};
		int[][] topLevels = {{0}, {5}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, topLevels, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		assertThat(preview.getRGB(0, 0)).isEqualTo(Color.GRAY.getRGB());
	}

	@Test
	void renderPreviewUnknownBlockRendersGray() {
		BlockData[][] blocks = {{new BlockData("minecraft:unknown_block")}};
		int[][] topLevels = {{0}, {5}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, topLevels, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		assertThat(preview.getRGB(0, 0)).isEqualTo(Color.GRAY.getRGB());
	}

	@Test
	void renderPreviewNullTopLevelsRendersWithoutException() {
		BlockData[][] blocks = {{new BlockData("minecraft:red_wool")}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, null, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		// Не должно бросать исключение, цвет должен быть не серым (блок известен)
		assertThat(preview.getRGB(0, 0)).isNotEqualTo(Color.GRAY.getRGB());
	}

	// ─── resolveBrightness (через renderPreview) ───────────────────────────────

	@Test
	void renderPreviewHigherBlockIsHighBrightness() {
		// topLevels[y+1][x] > topLevels[y][x] → HIGH
		// blocks высотой 3, mapHeight=1 → tileRows=3
		// y=1: isFirstRowOfTile = 1%3==0 && 1>0 = false, isLastRowOfTile = 2%3==0 = false → сравниваем уровни
		BlockData redWool = new BlockData("minecraft:red_wool");
		BlockData[][] blocks = {{redWool}, {redWool}, {redWool}};
		// topLevels размером [height+1][width] = [4][1]
		// y=0: topLevels[1][0]=5 vs topLevels[0][0]=5 → NORMAL
		// y=1: topLevels[2][0]=10 vs topLevels[1][0]=5 → HIGH (10 > 5)
		int[][] topLevels = {{5}, {5}, {10}, {10}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, topLevels, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		int highPixel = preview.getRGB(0, 1);
		int normalPixel = preview.getRGB(0, 0);

		int highRed = (highPixel >> 16) & 0xFF;
		int normalRed = (normalPixel >> 16) & 0xFF;

		assertThat(highRed).isGreaterThan(normalRed);
	}

	@Test
	void renderPreviewLowerBlockIsLowBrightness() {
		// topLevels[y+1][x] < topLevels[y][x] → LOW
		// blocks высотой 3, mapHeight=1 → tileRows=3
		// y=1: isFirstRowOfTile = false, isLastRowOfTile = false → сравниваем уровни
		BlockData redWool = new BlockData("minecraft:red_wool");
		BlockData[][] blocks = {{redWool}, {redWool}, {redWool}};
		// y=0: topLevels[1][0]=10 vs topLevels[0][0]=10 → NORMAL
		// y=1: topLevels[2][0]=5 vs topLevels[1][0]=10 → LOW (5 < 10)
		int[][] topLevels = {{10}, {10}, {5}, {5}};

		SchematicImportResult importResult = new SchematicImportResult(blocks, topLevels, 1, 1, Set.of());
		BufferedImage preview = ImageConverter.renderPreview(importResult, PALETTE_JSON);

		int lowPixel = preview.getRGB(0, 1);
		int normalPixel = preview.getRGB(0, 0);

		int lowRed = (lowPixel >> 16) & 0xFF;
		int normalRed = (normalPixel >> 16) & 0xFF;

		assertThat(lowRed).isLessThan(normalRed);
	}
}
