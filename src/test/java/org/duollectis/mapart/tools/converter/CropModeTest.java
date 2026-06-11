package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CropModeTest {

	@Test
	void fourValuesExist() {
		assertThat(CropMode.values()).hasSize(4);
	}

	@Test
	void stretchExists() {
		assertThat(CropMode.valueOf("STRETCH")).isEqualTo(CropMode.STRETCH);
	}

	@Test
	void centerExists() {
		assertThat(CropMode.valueOf("CENTER")).isEqualTo(CropMode.CENTER);
	}

	@Test
	void manualExists() {
		assertThat(CropMode.valueOf("MANUAL")).isEqualTo(CropMode.MANUAL);
	}

	@Test
	void fitExists() {
		assertThat(CropMode.valueOf("FIT")).isEqualTo(CropMode.FIT);
	}
}
