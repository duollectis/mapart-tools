package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDataTest {

	@Test
	void isSlab_forSlabBlock_returnsTrue() {
		BlockData slab = new BlockData("minecraft:oak_slab");

		assertThat(slab.isSlab()).isTrue();
	}

	@Test
	void isSlab_forRegularBlock_returnsFalse() {
		BlockData stone = new BlockData("minecraft:stone");

		assertThat(stone.isSlab()).isFalse();
	}

	@Test
	void isSlab_slabWordInMiddle_returnsFalse() {
		BlockData block = new BlockData("minecraft:slab_stone");

		assertThat(block.isSlab()).isFalse();
	}

	@Test
	void getProperties_withoutProperties_returnsEmptyMap() {
		BlockData block = new BlockData("minecraft:stone");

		assertThat(block.getProperties()).isEmpty();
	}

	@Test
	void getProperties_withProperties_returnsMap() {
		BlockData block = new BlockData("minecraft:oak_log", Map.of("axis", "y"));

		assertThat(block.getProperties()).containsEntry("axis", "y");
	}

	@Test
	void withProperty_addsNewProperty() {
		BlockData block = new BlockData("minecraft:oak_slab");

		BlockData modified = block.withProperty("type", "top");

		assertThat(modified.getProperties()).containsEntry("type", "top");
		assertThat(modified.getId()).isEqualTo("minecraft:oak_slab");
	}

	@Test
	void withProperty_doesNotMutateOriginal() {
		BlockData original = new BlockData("minecraft:oak_slab");

		original.withProperty("type", "top");

		assertThat(original.getProperties()).isEmpty();
	}

	@Test
	void withProperty_overridesExistingProperty() {
		BlockData block = new BlockData("minecraft:oak_slab", Map.of("type", "bottom"));

		BlockData modified = block.withProperty("type", "top");

		assertThat(modified.getProperties()).containsEntry("type", "top");
	}

	@Test
	void asTopSlab_setsTypeTop() {
		BlockData slab = new BlockData("minecraft:oak_slab");

		BlockData topSlab = slab.asTopSlab();

		assertThat(topSlab.getProperties()).containsEntry("type", "top");
	}

	@Test
	void getUniqueKey_withoutProperties_returnsId() {
		BlockData block = new BlockData("minecraft:stone");

		assertThat(block.getUniqueKey()).isEqualTo("minecraft:stone");
	}

	@Test
	void getUniqueKey_withProperties_appendsSuffix() {
		BlockData block = new BlockData("minecraft:oak_log", Map.of("axis", "y"));

		assertThat(block.getUniqueKey()).isEqualTo("minecraft:oak_log_axis_y");
	}

	@Test
	void getUniqueKey_multipleProperties_sortedByKey() {
		BlockData block = new BlockData("minecraft:oak_log", Map.of("axis", "y", "waterlogged", "false"));

		assertThat(block.getUniqueKey()).isEqualTo("minecraft:oak_log_axis_y_waterlogged_false");
	}

	@Test
	void equals_sameBlocks_areEqual() {
		BlockData a = new BlockData("minecraft:stone");
		BlockData b = new BlockData("minecraft:stone");

		assertThat(a).isEqualTo(b);
	}

	@Test
	void equals_differentIds_areNotEqual() {
		BlockData a = new BlockData("minecraft:stone");
		BlockData b = new BlockData("minecraft:dirt");

		assertThat(a).isNotEqualTo(b);
	}
}
