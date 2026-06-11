package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockLevelerTest {

	private static PaletteEntry entry(Brightness brightness) {
		return new PaletteEntry(List.of(new BlockData("minecraft:stone")), 0xFFFFFF, brightness);
	}

	// ─── processFlat ─────────────────────────────────────────────────────────────

	@Test
	void processFlat_singlePixelImage_createsTwoRows() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));

		leveler.process(false, StaircaseMode.FLAT);

		assertThat(leveler.getProcessed()).hasNumberOfRows(2);
		assertThat(leveler.getProcessedHeight()).isEqualTo(1);
	}

	@Test
	void processFlat_allBlocksAtLevel0() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0, 0}, {0, 0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));

		leveler.process(false, StaircaseMode.FLAT);

		LeveledEntry[][] processed = leveler.getProcessed();

		for (LeveledEntry[] row : processed) {
			for (LeveledEntry cell : row) {
				assertThat(cell.getLevel()).isEqualTo(0);
			}
		}
	}

	@Test
	void processFlat_heightEquals1() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0, 0, 0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));

		leveler.process(false, StaircaseMode.FLAT);

		assertThat(leveler.getProcessedHeight()).isEqualTo(1);
	}

	@Test
	void processFlat_customSupportBlock_usesConfiguredBlock() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));
		leveler.setSupportBlockId("minecraft:obsidian");

		leveler.process(false, StaircaseMode.FLAT);

		LeveledEntry support = leveler.getProcessed()[0][0];
		assertThat(support.getEntry().getBlocks().getFirst().getId()).isEqualTo("minecraft:obsidian");
	}

	// ─── process staircase ───────────────────────────────────────────────────────

	@Test
	void processStaircase_allNormal_allAtSameLevel() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0, 0, 0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));

		leveler.process(false, StaircaseMode.STAIRCASE);

		LeveledEntry[][] processed = leveler.getProcessed();
		int firstLevel = processed[1][0].getLevel();

		for (int x = 0; x < 3; x++) {
			assertThat(processed[1][x].getLevel()).isEqualTo(firstLevel);
		}
	}

	@Test
	void processStaircase_minimumLevel_isZero() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0, 1, 0}, {0, 1, 0}});
		leveler.setPalette(List.of(
			entry(Brightness.NORMAL),
			entry(Brightness.HIGH)
		));

		leveler.process(false, StaircaseMode.STAIRCASE);

		LeveledEntry[][] processed = leveler.getProcessed();
		int minLevel = Integer.MAX_VALUE;

		for (LeveledEntry[] row : processed) {
			for (LeveledEntry cell : row) {
				minLevel = Math.min(minLevel, cell.getLevel());
			}
		}

		assertThat(minLevel).isEqualTo(0);
	}

	@Test
	void processStaircase_processedHeight_atLeast1() {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setImage(new int[][]{{0}});
		leveler.setPalette(List.of(entry(Brightness.NORMAL)));

		leveler.process(false, StaircaseMode.STAIRCASE);

		assertThat(leveler.getProcessedHeight()).isGreaterThanOrEqualTo(1);
	}

	// ─── readTopLayer ─────────────────────────────────────────────────────────────

	@Test
	void readTopLayer_returnsTopNonAirBlock() {
		BlockData stone = new BlockData("minecraft:stone");
		BlockData air = new BlockData("minecraft:air");

		// volume[y][z][x]: sizeY=3, sizeZ=2, sizeX=1
		BlockData[][][] volume = {
			{{stone}, {stone}},  // y=0
			{{air},   {air}},    // y=1
			{{air},   {air}}     // y=2
		};

		BlockLeveler.TopLayerResult result = BlockLeveler.readTopLayer(volume, 1, 3, 2);

		assertThat(result.blocks()[0][0].getId()).isEqualTo("minecraft:stone");
	}

	@Test
	void readTopLayer_allAir_returnsAir() {
		BlockData air = new BlockData("minecraft:air");

		// volume[y][z][x]: sizeY=2, sizeZ=2, sizeX=1
		BlockData[][][] volume = {
			{{air}, {air}},
			{{air}, {air}}
		};

		BlockLeveler.TopLayerResult result = BlockLeveler.readTopLayer(volume, 1, 2, 2);

		assertThat(result.blocks()[0][0].getId()).isEqualTo("minecraft:air");
	}

	@Test
	void readTopLayer_topLevels_hasCorrectSize() {
		BlockData stone = new BlockData("minecraft:stone");

		// volume[y][z][x]: sizeY=2, sizeZ=2, sizeX=2
		BlockData[][][] volume = {
			{{stone, stone}, {stone, stone}},
			{{stone, stone}, {stone, stone}}
		};

		BlockLeveler.TopLayerResult result = BlockLeveler.readTopLayer(volume, 2, 2, 2);

		assertThat(result.levels()).hasNumberOfRows(2);
		assertThat(result.levels()[0]).hasSize(2);
	}

	@Test
	void readTopLayer_nullBlock_treatedAsAir() {
		BlockData stone = new BlockData("minecraft:stone");

		// volume[y][z][x]: sizeY=2, sizeZ=2, sizeX=1
		BlockData[][][] volume = {
			{{stone}, {stone}},
			{{null},  {null}}
		};

		BlockLeveler.TopLayerResult result = BlockLeveler.readTopLayer(volume, 1, 2, 2);

		assertThat(result.blocks()[0][0].getId()).isEqualTo("minecraft:stone");
	}
}
