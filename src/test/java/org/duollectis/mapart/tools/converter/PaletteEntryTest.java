package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaletteEntryTest {

	private static BlockData stone() {
		return new BlockData("minecraft:stone");
	}

	private static BlockData grass() {
		return new BlockData("minecraft:grass_block");
	}

	@Test
	void simpleConstructorStoresRgb() {
		PaletteEntry entry = new PaletteEntry(List.of(stone()), 0xFF0000, Brightness.NORMAL);

		assertThat(entry.getRgb()).isEqualTo(0xFF0000);
	}

	@Test
	void simpleConstructorStoresBrightness() {
		PaletteEntry entry = new PaletteEntry(List.of(stone()), 0x00FF00, Brightness.HIGH);

		assertThat(entry.getBrightness()).isEqualTo(Brightness.HIGH);
	}

	@Test
	void simpleConstructorBlocksAreImmutable() {
		PaletteEntry entry = new PaletteEntry(List.of(stone()), 0x000000, Brightness.LOW);

		assertThat(entry.getBlocks()).hasSize(1);
	}

	@Test
	void pickBlockDefaultSelectorAlwaysReturnsFirstBlock() {
		PaletteEntry entry = new PaletteEntry(List.of(stone()), 0x000000, Brightness.NORMAL);

		assertThat(entry.pickBlock(0)).isEqualTo(stone());
		assertThat(entry.pickBlock(1)).isEqualTo(stone());
		assertThat(entry.pickBlock(99)).isEqualTo(stone());
	}

	@Test
	void pickBlockWithCustomSelectorUsesSelector() {
		List<WeightedSelector.Entry<BlockData>> entries = List.of(
			new WeightedSelector.Entry<>(stone(), 100),
			new WeightedSelector.Entry<>(grass(), 100)
		);
		WeightedSelector<BlockData> selector = new WeightedSelector<>(entries, WeightedSelector.Mode.SEQUENTIAL);
		PaletteEntry entry = new PaletteEntry(List.of(stone(), grass()), 0x000000, Brightness.NORMAL, selector);

		// totalWeight=200, thresholds=[100, 200]
		// pick(0): 0 % 200 = 0 < 100 → stone
		// pick(100): 100 % 200 = 100, не < 100, но < 200 → grass
		assertThat(entry.pickBlock(0)).isEqualTo(stone());
		assertThat(entry.pickBlock(100)).isEqualTo(grass());
	}

	@Test
	void fullConstructorStoresRgbAndBrightness() {
		WeightedSelector<BlockData> selector = WeightedSelector.single(stone());
		PaletteEntry entry = new PaletteEntry(List.of(stone()), 0x123456, Brightness.LOW, selector);

		assertThat(entry.getRgb()).isEqualTo(0x123456);
		assertThat(entry.getBrightness()).isEqualTo(Brightness.LOW);
	}
}
