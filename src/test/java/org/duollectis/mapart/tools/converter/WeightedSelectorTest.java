package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedSelectorTest {

	@Test
	void single_createsSelectorWithOneElement() {
		WeightedSelector<String> selector = WeightedSelector.single("stone");

		assertThat(selector.pick(0)).isEqualTo("stone");
		assertThat(selector.pick(999)).isEqualTo("stone");
	}

	@Test
	void single_alwaysReturnsSameElement() {
		WeightedSelector<String> selector = WeightedSelector.single("dirt");

		Set<String> results = IntStream.range(0, 100)
			.mapToObj(selector::pick)
			.collect(Collectors.toSet());

		assertThat(results).containsOnly("dirt");
	}

	@Test
	void pickSequential_equalWeights_alternatesElements() {
		List<WeightedSelector.Entry<String>> entries = List.of(
			new WeightedSelector.Entry<>("A", 1),
			new WeightedSelector.Entry<>("B", 1)
		);
		WeightedSelector<String> selector = new WeightedSelector<>(entries, WeightedSelector.Mode.SEQUENTIAL);

		assertThat(selector.pick(0)).isEqualTo("A");
		assertThat(selector.pick(1)).isEqualTo("B");
		assertThat(selector.pick(2)).isEqualTo("A");
		assertThat(selector.pick(3)).isEqualTo("B");
	}

	@Test
	void pickSequential_proportionalWeights_distributesCorrectly() {
		List<WeightedSelector.Entry<String>> entries = List.of(
			new WeightedSelector.Entry<>("A", 3),
			new WeightedSelector.Entry<>("B", 1)
		);
		WeightedSelector<String> selector = new WeightedSelector<>(entries, WeightedSelector.Mode.SEQUENTIAL);

		assertThat(selector.pick(0)).isEqualTo("A");
		assertThat(selector.pick(1)).isEqualTo("A");
		assertThat(selector.pick(2)).isEqualTo("A");
		assertThat(selector.pick(3)).isEqualTo("B");
		assertThat(selector.pick(4)).isEqualTo("A");
	}

	@Test
	void pickRandom_returnsOnlyElementsFromList() {
		List<WeightedSelector.Entry<String>> entries = List.of(
			new WeightedSelector.Entry<>("stone", 50),
			new WeightedSelector.Entry<>("dirt", 50)
		);
		WeightedSelector<String> selector = new WeightedSelector<>(entries, WeightedSelector.Mode.RANDOM);

		Set<String> results = IntStream.range(0, 200)
			.mapToObj(selector::pick)
			.collect(Collectors.toSet());

		assertThat(results).isSubsetOf("stone", "dirt");
	}

	@Test
	void pickRandom_equalWeights_returnsBothElements() {
		List<WeightedSelector.Entry<String>> entries = List.of(
			new WeightedSelector.Entry<>("A", 50),
			new WeightedSelector.Entry<>("B", 50)
		);
		WeightedSelector<String> selector = new WeightedSelector<>(entries, WeightedSelector.Mode.RANDOM);

		Set<String> results = IntStream.range(0, 500)
			.mapToObj(selector::pick)
			.collect(Collectors.toSet());

		assertThat(results).contains("A", "B");
	}

	@Test
	void copy_createsIndependentCopy() {
		WeightedSelector<String> original = WeightedSelector.single("stone");

		WeightedSelector<String> copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.pick(0)).isEqualTo("stone");
	}

	@Test
	void constructor_emptyList_throwsException() {
		assertThatThrownBy(() -> new WeightedSelector<>(List.of(), WeightedSelector.Mode.SEQUENTIAL))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void constructor_nullList_throwsException() {
		assertThatThrownBy(() -> new WeightedSelector<>(null, WeightedSelector.Mode.SEQUENTIAL))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entry_zeroWeight_throwsException() {
		assertThatThrownBy(() -> new WeightedSelector.Entry<>("stone", 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entry_negativeWeight_throwsException() {
		assertThatThrownBy(() -> new WeightedSelector.Entry<>("stone", -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getEntries_returnsUnmodifiableList() {
		WeightedSelector<String> selector = WeightedSelector.single("stone");

		assertThatThrownBy(() -> selector.getEntries().add(new WeightedSelector.Entry<>("dirt", 1)))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
