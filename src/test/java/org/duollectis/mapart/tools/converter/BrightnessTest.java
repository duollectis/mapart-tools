package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrightnessTest {

	@Test
	void lowModifierIs180() {
		assertThat(Brightness.LOW.getModifier()).isEqualTo(180);
	}

	@Test
	void normalModifierIs220() {
		assertThat(Brightness.NORMAL.getModifier()).isEqualTo(220);
	}

	@Test
	void highModifierIs255() {
		assertThat(Brightness.HIGH.getModifier()).isEqualTo(255);
	}

	@Test
	void modifiersAreAscending() {
		assertThat(Brightness.LOW.getModifier())
			.isLessThan(Brightness.NORMAL.getModifier());

		assertThat(Brightness.NORMAL.getModifier())
			.isLessThan(Brightness.HIGH.getModifier());
	}

	@Test
	void threeValuesExist() {
		assertThat(Brightness.values()).hasSize(3);
	}
}
