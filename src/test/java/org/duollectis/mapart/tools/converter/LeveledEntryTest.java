package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeveledEntryTest {

	private static PaletteEntry buildEntry() {
		return new PaletteEntry(List.of(new BlockData("minecraft:stone")), 0xFFFFFF, Brightness.NORMAL);
	}

	@Test
	void getLevel_returnsInitialLevel() {
		LeveledEntry entry = new LeveledEntry(buildEntry(), 5);

		assertThat(entry.getLevel()).isEqualTo(5);
	}

	@Test
	void addLevel_increasesLevel() {
		LeveledEntry entry = new LeveledEntry(buildEntry(), 3);

		entry.addLevel(2);

		assertThat(entry.getLevel()).isEqualTo(5);
	}

	@Test
	void addLevel_decreasesLevel() {
		LeveledEntry entry = new LeveledEntry(buildEntry(), 10);

		entry.addLevel(-4);

		assertThat(entry.getLevel()).isEqualTo(6);
	}

	@Test
	void addLevel_zeroDelta_doesNotChangeLevel() {
		LeveledEntry entry = new LeveledEntry(buildEntry(), 7);

		entry.addLevel(0);

		assertThat(entry.getLevel()).isEqualTo(7);
	}

	@Test
	void addLevel_multipleCalls_accumulates() {
		LeveledEntry entry = new LeveledEntry(buildEntry(), 0);

		entry.addLevel(3);
		entry.addLevel(2);
		entry.addLevel(-1);

		assertThat(entry.getLevel()).isEqualTo(4);
	}

	@Test
	void getEntry_returnsSameReference() {
		PaletteEntry paletteEntry = buildEntry();
		LeveledEntry entry = new LeveledEntry(paletteEntry, 0);

		assertThat(entry.getEntry()).isSameAs(paletteEntry);
	}
}
