package org.duollectis.mapart.tools.utils.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageAdjustmentsTest {

	@Test
	void defaults_returnsNeutralSettings() {
		ImageAdjustments adj = ImageAdjustments.defaults();

		assertThat(adj.brightness()).isEqualTo(0);
		assertThat(adj.contrast()).isEqualTo(0);
		assertThat(adj.saturation()).isEqualTo(0);
		assertThat(adj.gamma()).isEqualTo(100);
		assertThat(adj.hue()).isEqualTo(0);
	}

	@Test
	void isNeutral_forDefaults_returnsTrue() {
		assertThat(ImageAdjustments.defaults().isNeutral()).isTrue();
	}

	@Test
	void isNeutral_withChangedBrightness_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(10, 0, 0, 100, 0);

		assertThat(adj.isNeutral()).isFalse();
	}

	@Test
	void isNeutral_withChangedContrast_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(0, -50, 0, 100, 0);

		assertThat(adj.isNeutral()).isFalse();
	}

	@Test
	void isNeutral_withChangedSaturation_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(0, 0, 30, 100, 0);

		assertThat(adj.isNeutral()).isFalse();
	}

	@Test
	void isNeutral_withChangedGamma_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(0, 0, 0, 150, 0);

		assertThat(adj.isNeutral()).isFalse();
	}

	@Test
	void isNeutral_withChangedHue_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(0, 0, 0, 100, 45);

		assertThat(adj.isNeutral()).isFalse();
	}

	@Test
	void isNeutral_gamma99_returnsFalse() {
		ImageAdjustments adj = new ImageAdjustments(0, 0, 0, 99, 0);

		assertThat(adj.isNeutral()).isFalse();
	}
}
