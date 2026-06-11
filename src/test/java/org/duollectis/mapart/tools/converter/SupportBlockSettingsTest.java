package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportBlockSettingsTest {

	@Test
	void single_createsSingleBlockSettings() {
		SupportBlockSettings settings = SupportBlockSettings.single("minecraft:stone");

		assertThat(settings.isEmpty()).isFalse();
		assertThat(settings.pickBlock(0)).isEqualTo("minecraft:stone");
	}

	@Test
	void single_alwaysReturnsSameBlock() {
		SupportBlockSettings settings = SupportBlockSettings.single("minecraft:stone");

		for (int i = 0; i < 100; i++) {
			assertThat(settings.pickBlock(i)).isEqualTo("minecraft:stone");
		}
	}

	@Test
	void isEmpty_emptyList_returnsTrue() {
		SupportBlockSettings settings = new SupportBlockSettings(List.of(), WeightedSelector.Mode.SEQUENTIAL);

		assertThat(settings.isEmpty()).isTrue();
	}

	@Test
	void isEmpty_nonEmptyList_returnsFalse() {
		SupportBlockSettings settings = SupportBlockSettings.single("minecraft:stone");

		assertThat(settings.isEmpty()).isFalse();
	}

	@Test
	void pickBlock_emptyList_returnsNull() {
		SupportBlockSettings settings = new SupportBlockSettings(List.of(), WeightedSelector.Mode.SEQUENTIAL);

		assertThat(settings.pickBlock(0)).isNull();
	}

	@Test
	void pickBlock_nullList_returnsNull() {
		SupportBlockSettings settings = new SupportBlockSettings(null, WeightedSelector.Mode.SEQUENTIAL);

		assertThat(settings.pickBlock(0)).isNull();
	}

	@Test
	void filtered_keepsOnlyAllowedBlocks() {
		List<SupportBlockSettings.Entry> entries = List.of(
			new SupportBlockSettings.Entry("minecraft:stone", 50),
			new SupportBlockSettings.Entry("minecraft:dirt", 50)
		);
		SupportBlockSettings settings = new SupportBlockSettings(entries, WeightedSelector.Mode.SEQUENTIAL);

		SupportBlockSettings filtered = settings.filtered(Set.of("minecraft:stone"));

		assertThat(filtered.getEntries()).hasSize(1);
		assertThat(filtered.getEntries().getFirst().blockId()).isEqualTo("minecraft:stone");
	}

	@Test
	void filtered_allBlocksFiltered_returnsEmpty() {
		SupportBlockSettings settings = SupportBlockSettings.single("minecraft:stone");

		SupportBlockSettings filtered = settings.filtered(Set.of("minecraft:dirt"));

		assertThat(filtered.isEmpty()).isTrue();
	}

	@Test
	void filtered_emptyAllowedSet_returnsEmpty() {
		SupportBlockSettings settings = SupportBlockSettings.single("minecraft:stone");

		SupportBlockSettings filtered = settings.filtered(Set.of());

		assertThat(filtered.isEmpty()).isTrue();
	}

	@Test
	void entry_zeroWeight_throwsException() {
		assertThatThrownBy(() -> new SupportBlockSettings.Entry("minecraft:stone", 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entry_negativeWeight_throwsException() {
		assertThatThrownBy(() -> new SupportBlockSettings.Entry("minecraft:stone", -5))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void multipleBlocks_sequential_alternates() {
		List<SupportBlockSettings.Entry> entries = List.of(
			new SupportBlockSettings.Entry("minecraft:stone", 1),
			new SupportBlockSettings.Entry("minecraft:dirt", 1)
		);
		SupportBlockSettings settings = new SupportBlockSettings(entries, WeightedSelector.Mode.SEQUENTIAL);

		assertThat(settings.pickBlock(0)).isEqualTo("minecraft:stone");
		assertThat(settings.pickBlock(1)).isEqualTo("minecraft:dirt");
		assertThat(settings.pickBlock(2)).isEqualTo("minecraft:stone");
	}
}
