#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include "dither_color.h"

using Catch::Matchers::WithinAbs;
using Catch::Matchers::WithinRel;

// ── srgb_to_linear ────────────────────────────────────────────────────────────

TEST_CASE("srgb_to_linear: black maps to 0", "[color]") {
	REQUIRE_THAT(srgb_to_linear(0.0), WithinAbs(0.0, 1e-10));
}

TEST_CASE("srgb_to_linear: white maps to 1", "[color]") {
	REQUIRE_THAT(srgb_to_linear(1.0), WithinAbs(1.0, 1e-6));
}

TEST_CASE("srgb_to_linear: mid-gray 0.5 maps to ~0.214", "[color]") {
	// Стандартное значение для sRGB 0.5 → linear ≈ 0.2140
	REQUIRE_THAT(srgb_to_linear(0.5), WithinAbs(0.2140, 0.001));
}

TEST_CASE("srgb_to_linear: low value uses linear branch (channel <= 0.04045)", "[color]") {
	// 0.04045 / 12.92 ≈ 0.003130
	REQUIRE_THAT(srgb_to_linear(0.04045), WithinAbs(0.003130, 1e-5));
}

TEST_CASE("srgb_to_linear: is monotonically increasing", "[color]") {
	REQUIRE(srgb_to_linear(0.2) < srgb_to_linear(0.5));
	REQUIRE(srgb_to_linear(0.5) < srgb_to_linear(0.8));
}

// ── xyz_to_lab_f ──────────────────────────────────────────────────────────────

TEST_CASE("xyz_to_lab_f: value above threshold uses cbrt branch", "[color]") {
	// t = 0.5 > 0.008856 → cbrt(0.5) ≈ 0.7937
	REQUIRE_THAT(xyz_to_lab_f(0.5), WithinAbs(std::pow(0.5, 1.0 / 3.0), 1e-10));
}

TEST_CASE("xyz_to_lab_f: value below threshold uses linear branch", "[color]") {
	// t = 0.001 < 0.008856 → 7.787 * 0.001 + 16/116 ≈ 0.14574
	REQUIRE_THAT(xyz_to_lab_f(0.001), WithinAbs(7.787 * 0.001 + 16.0 / 116.0, 1e-10));
}

// ── rgb_to_lab ────────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_lab: black (0,0,0) maps to L=0", "[color]") {
	auto lab = rgb_to_lab(0, 0, 0);
	REQUIRE_THAT(lab.L, WithinAbs(0.0, 0.5));
}

TEST_CASE("rgb_to_lab: white (255,255,255) maps to L≈100", "[color]") {
	auto lab = rgb_to_lab(255, 255, 255);
	REQUIRE_THAT(lab.L, WithinAbs(100.0, 0.5));
}

TEST_CASE("rgb_to_lab: neutral gray has a≈0 and b≈0", "[color]") {
	auto lab = rgb_to_lab(128, 128, 128);
	REQUIRE_THAT(lab.a, WithinAbs(0.0, 1.0));
	REQUIRE_THAT(lab.b, WithinAbs(0.0, 1.0));
}

TEST_CASE("rgb_to_lab: red has positive a and near-zero b", "[color]") {
	auto lab = rgb_to_lab(255, 0, 0);
	REQUIRE(lab.a > 30.0);
}

TEST_CASE("rgb_to_lab: blue has negative b", "[color]") {
	auto lab = rgb_to_lab(0, 0, 255);
	REQUIRE(lab.b < -20.0);
}

TEST_CASE("rgb_to_lab: L is in valid range [0, 100]", "[color]") {
	for (uint8_t v : {0, 64, 128, 192, 255}) {
		auto lab = rgb_to_lab(v, v, v);
		REQUIRE(lab.L >= 0.0);
		REQUIRE(lab.L <= 100.5);
	}
}

// ── rgb_to_lab_d50 ────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_lab_d50: white maps to L≈100", "[color]") {
	auto lab = rgb_to_lab_d50(255, 255, 255);
	REQUIRE_THAT(lab.L, WithinAbs(100.0, 0.5));
}

TEST_CASE("rgb_to_lab_d50: black maps to L≈0", "[color]") {
	auto lab = rgb_to_lab_d50(0, 0, 0);
	REQUIRE_THAT(lab.L, WithinAbs(0.0, 0.5));
}

TEST_CASE("rgb_to_lab_d50: neutral gray has a≈0 and b≈0", "[color]") {
	auto lab = rgb_to_lab_d50(128, 128, 128);
	REQUIRE_THAT(lab.a, WithinAbs(0.0, 2.0));
	REQUIRE_THAT(lab.b, WithinAbs(0.0, 2.0));
}

// ── rgb_to_oklab ──────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_oklab: black maps to L=0", "[color]") {
	auto ok = rgb_to_oklab(0, 0, 0);
	REQUIRE_THAT(ok.L, WithinAbs(0.0, 1e-6));
}

TEST_CASE("rgb_to_oklab: white maps to L≈1", "[color]") {
	auto ok = rgb_to_oklab(255, 255, 255);
	REQUIRE_THAT(ok.L, WithinAbs(1.0, 0.01));
}

TEST_CASE("rgb_to_oklab: neutral gray has a≈0 and b≈0", "[color]") {
	auto ok = rgb_to_oklab(128, 128, 128);
	REQUIRE_THAT(ok.a, WithinAbs(0.0, 0.01));
	REQUIRE_THAT(ok.b, WithinAbs(0.0, 0.01));
}

TEST_CASE("rgb_to_oklab: L is in range [0, 1]", "[color]") {
	for (uint8_t v : {0, 64, 128, 192, 255}) {
		auto ok = rgb_to_oklab(v, v, v);
		REQUIRE(ok.L >= 0.0);
		REQUIRE(ok.L <= 1.01);
	}
}

// ── rgb_to_hsl ────────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_hsl: black has L=0 and S=0", "[color]") {
	auto hsl = rgb_to_hsl(0, 0, 0);
	REQUIRE_THAT(hsl.b, WithinAbs(0.0, 1e-9));
	REQUIRE_THAT(hsl.a, WithinAbs(0.0, 1e-9));
}

TEST_CASE("rgb_to_hsl: white has L=1 and S=0", "[color]") {
	auto hsl = rgb_to_hsl(255, 255, 255);
	REQUIRE_THAT(hsl.b, WithinAbs(1.0, 1e-6));
	REQUIRE_THAT(hsl.a, WithinAbs(0.0, 1e-6));
}

TEST_CASE("rgb_to_hsl: pure red has H≈0 and S=1", "[color]") {
	auto hsl = rgb_to_hsl(255, 0, 0);
	// H нормализован в [0,1], красный = 0/360 = 0.0
	REQUIRE_THAT(hsl.L, WithinAbs(0.0, 0.01));
	REQUIRE_THAT(hsl.a, WithinAbs(1.0, 0.01));
}

TEST_CASE("rgb_to_hsl: pure green has H≈120/360", "[color]") {
	auto hsl = rgb_to_hsl(0, 255, 0);
	REQUIRE_THAT(hsl.L, WithinAbs(120.0 / 360.0, 0.01));
}

TEST_CASE("rgb_to_hsl: pure blue has H≈240/360", "[color]") {
	auto hsl = rgb_to_hsl(0, 0, 255);
	REQUIRE_THAT(hsl.L, WithinAbs(240.0 / 360.0, 0.01));
}

// ── rgb_to_hsv ────────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_hsv: black has V=0 and S=0", "[color]") {
	auto hsv = rgb_to_hsv(0, 0, 0);
	REQUIRE_THAT(hsv.b, WithinAbs(0.0, 1e-9));
	REQUIRE_THAT(hsv.a, WithinAbs(0.0, 1e-9));
}

TEST_CASE("rgb_to_hsv: white has V=1 and S=0", "[color]") {
	auto hsv = rgb_to_hsv(255, 255, 255);
	REQUIRE_THAT(hsv.b, WithinAbs(1.0, 1e-6));
	REQUIRE_THAT(hsv.a, WithinAbs(0.0, 1e-6));
}

TEST_CASE("rgb_to_hsv: pure red has S=1 and V=1", "[color]") {
	auto hsv = rgb_to_hsv(255, 0, 0);
	REQUIRE_THAT(hsv.a, WithinAbs(1.0, 0.01));
	REQUIRE_THAT(hsv.b, WithinAbs(1.0, 0.01));
}

// ── rgb_to_yuv ────────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_yuv: black has Y=0", "[color]") {
	auto yuv = rgb_to_yuv(0, 0, 0);
	REQUIRE_THAT(yuv.L, WithinAbs(0.0, 1e-9));
}

TEST_CASE("rgb_to_yuv: white has Y≈1", "[color]") {
	auto yuv = rgb_to_yuv(255, 255, 255);
	REQUIRE_THAT(yuv.L, WithinAbs(1.0, 0.001));
}

TEST_CASE("rgb_to_yuv: neutral gray has U≈0 and V≈0", "[color]") {
	auto yuv = rgb_to_yuv(128, 128, 128);
	REQUIRE_THAT(yuv.a, WithinAbs(0.0, 0.01));
	REQUIRE_THAT(yuv.b, WithinAbs(0.0, 0.01));
}

// ── rgb_to_ycbcr ──────────────────────────────────────────────────────────────

TEST_CASE("rgb_to_ycbcr: neutral gray has Cb≈0.5 and Cr≈0.5", "[color]") {
	auto ycbcr = rgb_to_ycbcr(128, 128, 128);
	REQUIRE_THAT(ycbcr.a, WithinAbs(0.5, 0.05));
	REQUIRE_THAT(ycbcr.b, WithinAbs(0.5, 0.05));
}

TEST_CASE("rgb_to_ycbcr: black has Y near offset", "[color]") {
	auto ycbcr = rgb_to_ycbcr(0, 0, 0);
	// Y = 0.0627 (offset)
	REQUIRE_THAT(ycbcr.L, WithinAbs(0.0627, 0.01));
}

// ── find_nearest_lab ──────────────────────────────────────────────────────────

TEST_CASE("find_nearest_lab: exact match returns correct index", "[color]") {
	std::vector<LabColor> palette = {
		{50.0, 0.0, 0.0},
		{80.0, 10.0, 5.0},
		{20.0, -5.0, 3.0}
	};

	REQUIRE(find_nearest_lab({80.0, 10.0, 5.0}, palette) == 1);
	REQUIRE(find_nearest_lab({20.0, -5.0, 3.0}, palette) == 2);
}

TEST_CASE("find_nearest_lab: single-element palette always returns 0", "[color]") {
	std::vector<LabColor> palette = {{50.0, 0.0, 0.0}};
	REQUIRE(find_nearest_lab({99.0, 99.0, 99.0}, palette) == 0);
}

TEST_CASE("find_nearest_lab: picks closest by euclidean distance", "[color]") {
	std::vector<LabColor> palette = {
		{10.0, 0.0, 0.0},
		{90.0, 0.0, 0.0}
	};
	// Пиксель L=85 ближе к 90
	REQUIRE(find_nearest_lab({85.0, 0.0, 0.0}, palette) == 1);
	// Пиксель L=15 ближе к 10
	REQUIRE(find_nearest_lab({15.0, 0.0, 0.0}, palette) == 0);
}

// ── find_nearest_rgb ──────────────────────────────────────────────────────────

TEST_CASE("find_nearest_rgb: exact match returns correct index", "[color]") {
	std::vector<RgbColor> palette = {
		{255.0, 0.0, 0.0},
		{0.0, 255.0, 0.0},
		{0.0, 0.0, 255.0}
	};

	REQUIRE(find_nearest_rgb({255.0, 0.0, 0.0}, palette) == 0);
	REQUIRE(find_nearest_rgb({0.0, 255.0, 0.0}, palette) == 1);
	REQUIRE(find_nearest_rgb({0.0, 0.0, 255.0}, palette) == 2);
}

TEST_CASE("find_nearest_rgb: picks closest color", "[color]") {
	std::vector<RgbColor> palette = {
		{0.0, 0.0, 0.0},
		{255.0, 255.0, 255.0}
	};
	// Тёмный пиксель ближе к чёрному
	REQUIRE(find_nearest_rgb({30.0, 30.0, 30.0}, palette) == 0);
	// Светлый пиксель ближе к белому
	REQUIRE(find_nearest_rgb({220.0, 220.0, 220.0}, palette) == 1);
}

// ── find_nearest_oklab ────────────────────────────────────────────────────────

TEST_CASE("find_nearest_oklab: exact match returns correct index", "[color]") {
	std::vector<OklabColor> palette = {
		{0.0, 0.0, 0.0},
		{0.5, 0.1, -0.1},
		{1.0, 0.0, 0.0}
	};

	REQUIRE(find_nearest_oklab({0.5, 0.1, -0.1}, palette) == 1);
}

// ── find_nearest_weighted_rgb ─────────────────────────────────────────────────

TEST_CASE("find_nearest_weighted_rgb: exact match returns correct index", "[color]") {
	std::vector<RgbColor> palette = {
		{255.0, 0.0, 0.0},
		{0.0, 255.0, 0.0}
	};

	REQUIRE(find_nearest_weighted_rgb({255.0, 0.0, 0.0}, palette) == 0);
	REQUIRE(find_nearest_weighted_rgb({0.0, 255.0, 0.0}, palette) == 1);
}

// ── convert_palette_to_lab ────────────────────────────────────────────────────

TEST_CASE("convert_palette_to_lab: converts palette of size 1", "[color]") {
	int32_t palette[] = {0xFFFFFF};
	auto result = convert_palette_to_lab(palette, 1);

	REQUIRE(result.size() == 1);
	REQUIRE_THAT(result[0].L, WithinAbs(100.0, 0.5));
}

TEST_CASE("convert_palette_to_lab: black entry has L≈0", "[color]") {
	int32_t palette[] = {0x000000};
	auto result = convert_palette_to_lab(palette, 1);

	REQUIRE_THAT(result[0].L, WithinAbs(0.0, 0.5));
}

TEST_CASE("convert_palette_to_lab: preserves palette size", "[color]") {
	int32_t palette[] = {0xFF0000, 0x00FF00, 0x0000FF};
	auto result = convert_palette_to_lab(palette, 3);

	REQUIRE(result.size() == 3);
}

// ── convert_palette_to_rgb ────────────────────────────────────────────────────

TEST_CASE("convert_palette_to_rgb: extracts channels correctly", "[color]") {
	int32_t palette[] = {0xFF8040};
	auto result = convert_palette_to_rgb(palette, 1);

	REQUIRE(result.size() == 1);
	REQUIRE_THAT(result[0].r, WithinAbs(255.0, 0.5));
	REQUIRE_THAT(result[0].g, WithinAbs(128.0, 0.5));
	REQUIRE_THAT(result[0].b, WithinAbs(64.0, 0.5));
}

// ── convert_palette_to_oklab ──────────────────────────────────────────────────

TEST_CASE("convert_palette_to_oklab: white entry has L≈1", "[color]") {
	int32_t palette[] = {0xFFFFFF};
	auto result = convert_palette_to_oklab(palette, 1);

	REQUIRE(result.size() == 1);
	REQUIRE_THAT(result[0].L, WithinAbs(1.0, 0.01));
}
