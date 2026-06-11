#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include "dither_algorithms.h"

using Catch::Matchers::WithinAbs;

// ── noise_scale_for_metric ────────────────────────────────────────────────────

TEST_CASE("noise_scale_for_metric: OKLAB returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_OKLAB), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: OKLAB_CHROMA returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_OKLAB_CHROMA), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: HSL returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_HSL), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: HSV returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_HSV), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: YUV returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_YUV), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: YCBCR returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_YCBCR), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: IPT returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_IPT), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: JZAZBZ returns 0.01", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_JZAZBZ), WithinAbs(0.01, 1e-15));
}

TEST_CASE("noise_scale_for_metric: LAB returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_LAB), WithinAbs(1.0, 1e-15));
}

TEST_CASE("noise_scale_for_metric: CIEDE2000 returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_CIEDE2000), WithinAbs(1.0, 1e-15));
}

TEST_CASE("noise_scale_for_metric: RGB returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_RGB), WithinAbs(1.0, 1e-15));
}

TEST_CASE("noise_scale_for_metric: WEIGHTED_RGB returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_WEIGHTED_RGB), WithinAbs(1.0, 1e-15));
}

TEST_CASE("noise_scale_for_metric: LAB_D50 returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_LAB_D50), WithinAbs(1.0, 1e-15));
}

TEST_CASE("noise_scale_for_metric: HCT returns 1.0", "[algorithms]") {
	REQUIRE_THAT(noise_scale_for_metric(METRIC_HCT), WithinAbs(1.0, 1e-15));
}
