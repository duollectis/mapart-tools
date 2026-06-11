package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorMetricTest {

	@Test
	void labHasIdZero() {
		assertThat(ColorMetric.LAB.getId()).isZero();
	}

	@Test
	void ciede2000HasIdOne() {
		assertThat(ColorMetric.CIEDE2000.getId()).isEqualTo(1);
	}

	@Test
	void rgbHasIdTwo() {
		assertThat(ColorMetric.RGB.getId()).isEqualTo(2);
	}

	@Test
	void oklabHasIdThree() {
		assertThat(ColorMetric.OKLAB.getId()).isEqualTo(3);
	}

	@Test
	void weightedRgbHasIdFour() {
		assertThat(ColorMetric.WEIGHTED_RGB.getId()).isEqualTo(4);
	}

	@Test
	void labD50HasIdFive() {
		assertThat(ColorMetric.LAB_D50.getId()).isEqualTo(5);
	}

	@Test
	void ciede2000D50HasIdSix() {
		assertThat(ColorMetric.CIEDE2000_D50.getId()).isEqualTo(6);
	}

	@Test
	void hctHasIdSeven() {
		assertThat(ColorMetric.HCT.getId()).isEqualTo(7);
	}

	@Test
	void oklabChromaHasIdEight() {
		assertThat(ColorMetric.OKLAB_CHROMA.getId()).isEqualTo(8);
	}

	@Test
	void hslHasIdNine() {
		assertThat(ColorMetric.HSL.getId()).isEqualTo(9);
	}

	@Test
	void hsvHasIdTen() {
		assertThat(ColorMetric.HSV.getId()).isEqualTo(10);
	}

	@Test
	void yuvHasIdEleven() {
		assertThat(ColorMetric.YUV.getId()).isEqualTo(11);
	}

	@Test
	void ycbcrHasIdTwelve() {
		assertThat(ColorMetric.YCBCR.getId()).isEqualTo(12);
	}

	@Test
	void iptHasIdThirteen() {
		assertThat(ColorMetric.IPT.getId()).isEqualTo(13);
	}

	@Test
	void jzazbzHasIdFourteen() {
		assertThat(ColorMetric.JZAZBZ.getId()).isEqualTo(14);
	}

	@Test
	void allIdsAreUnique() {
		long uniqueCount = java.util.Arrays.stream(ColorMetric.values())
			.mapToInt(ColorMetric::getId)
			.distinct()
			.count();

		assertThat(uniqueCount).isEqualTo(ColorMetric.values().length);
	}

	@Test
	void idsAreSequentialFromZero() {
		ColorMetric[] values = ColorMetric.values();

		for (int i = 0; i < values.length; i++) {
			assertThat(values[i].getId()).isEqualTo(i);
		}
	}
}
