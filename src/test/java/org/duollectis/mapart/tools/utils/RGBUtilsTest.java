package org.duollectis.mapart.tools.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RGBUtilsTest {

	@Test
	void color_packsComponentsIntoInt() {
		int result = RGBUtils.color(255, 128, 0);

		assertThat(RGBUtils.red(result)).isEqualTo(255);
		assertThat(RGBUtils.green(result)).isEqualTo(128);
		assertThat(RGBUtils.blue(result)).isEqualTo(0);
	}

	@Test
	void color_zeroComponents_returnsBlack() {
		int result = RGBUtils.color(0, 0, 0);

		assertThat(result).isEqualTo(0);
	}

	@Test
	void color_maxComponents_returnsWhite() {
		int result = RGBUtils.color(255, 255, 255);

		assertThat(result).isEqualTo(0xFFFFFF);
	}

	@Test
	void red_extractsRedChannel() {
		int color = 0xAB1234;

		assertThat(RGBUtils.red(color)).isEqualTo(0xAB);
	}

	@Test
	void green_extractsGreenChannel() {
		int color = 0xAB1234;

		assertThat(RGBUtils.green(color)).isEqualTo(0x12);
	}

	@Test
	void blue_extractsBlueChannel() {
		int color = 0xAB1234;

		assertThat(RGBUtils.blue(color)).isEqualTo(0x34);
	}

	@Test
	void scaleRGB_factor255_returnsOriginal() {
		int original = RGBUtils.color(100, 150, 200);

		int scaled = RGBUtils.scaleRGB(original, 255);

		assertThat(RGBUtils.red(scaled)).isEqualTo(100);
		assertThat(RGBUtils.green(scaled)).isEqualTo(150);
		assertThat(RGBUtils.blue(scaled)).isEqualTo(200);
	}

	@Test
	void scaleRGB_factor0_returnsBlack() {
		int original = RGBUtils.color(100, 150, 200);

		int scaled = RGBUtils.scaleRGB(original, 0);

		assertThat(scaled).isEqualTo(0);
	}

	@Test
	void scaleRGB_factor180_appliesBrightnessLow() {
		int white = RGBUtils.color(255, 255, 255);

		int scaled = RGBUtils.scaleRGB(white, 180);

		assertThat(RGBUtils.red(scaled)).isEqualTo(180);
		assertThat(RGBUtils.green(scaled)).isEqualTo(180);
		assertThat(RGBUtils.blue(scaled)).isEqualTo(180);
	}

	@Test
	void scaleRGB_clampsOverflow() {
		int white = RGBUtils.color(255, 255, 255);

		int scaled = RGBUtils.scaleRGB(white, 300);

		assertThat(RGBUtils.red(scaled)).isLessThanOrEqualTo(255);
		assertThat(RGBUtils.green(scaled)).isLessThanOrEqualTo(255);
		assertThat(RGBUtils.blue(scaled)).isLessThanOrEqualTo(255);
	}

	@Test
	void color_masksHighBits() {
		int result = RGBUtils.color(256, 257, 258);

		assertThat(RGBUtils.red(result)).isEqualTo(0);
		assertThat(RGBUtils.green(result)).isEqualTo(1);
		assertThat(RGBUtils.blue(result)).isEqualTo(2);
	}
}
