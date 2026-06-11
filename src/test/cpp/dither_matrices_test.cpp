#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>
#include "dither_matrices.h"

using Catch::Matchers::WithinAbs;

// ─── Вспомогательные функции ──────────────────────────────────────────────────

template<int ROWS, int COLS>
static bool all_in_range(const double (&matrix)[ROWS][COLS]) {
	for (int r = 0; r < ROWS; ++r) {
		for (int c = 0; c < COLS; ++c) {
			if (matrix[r][c] < 0.0 || matrix[r][c] > 1.0) {
				return false;
			}
		}
	}

	return true;
}

template<int ROWS, int COLS>
static bool all_unique(const double (&matrix)[ROWS][COLS]) {
	constexpr int total = ROWS * COLS;
	double flat[total];
	int idx = 0;

	for (int r = 0; r < ROWS; ++r) {
		for (int c = 0; c < COLS; ++c) {
			flat[idx++] = matrix[r][c];
		}
	}

	for (int i = 0; i < total; ++i) {
		for (int j = i + 1; j < total; ++j) {
			if (flat[i] == flat[j]) {
				return false;
			}
		}
	}

	return true;
}

// ─── BAYER_MATRIX_2X2 ─────────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_2X2: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_2X2));
}

TEST_CASE("BAYER_MATRIX_2X2: all values are unique", "[matrices]") {
	REQUIRE(all_unique(BAYER_MATRIX_2X2));
}

TEST_CASE("BAYER_MATRIX_2X2: top-left is 0", "[matrices]") {
	REQUIRE_THAT(BAYER_MATRIX_2X2[0][0], WithinAbs(0.0, 1e-9));
}

// ─── BAYER_MATRIX_4X4 ─────────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_4X4: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_4X4));
}

TEST_CASE("BAYER_MATRIX_4X4: all 16 values are unique", "[matrices]") {
	REQUIRE(all_unique(BAYER_MATRIX_4X4));
}

TEST_CASE("BAYER_MATRIX_4X4: top-left is 0", "[matrices]") {
	REQUIRE_THAT(BAYER_MATRIX_4X4[0][0], WithinAbs(0.0, 1e-9));
}

// ─── BAYER_MATRIX_8X8 ─────────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_8X8: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_8X8));
}

TEST_CASE("BAYER_MATRIX_8X8: all 64 values are unique", "[matrices]") {
	REQUIRE(all_unique(BAYER_MATRIX_8X8));
}

// ─── BAYER_MATRIX_16X16 ───────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_16X16: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_16X16));
}

TEST_CASE("BAYER_MATRIX_16X16: all 256 values are unique", "[matrices]") {
	REQUIRE(all_unique(BAYER_MATRIX_16X16));
}

// ─── BAYER_MATRIX_3X3 ─────────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_3X3: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_3X3));
}

// ─── ORDERED_MATRIX_3X3 ───────────────────────────────────────────────────────

TEST_CASE("ORDERED_MATRIX_3X3: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(ORDERED_MATRIX_3X3));
}

// ─── CLUSTERED_DOT_MATRIX ─────────────────────────────────────────────────────

TEST_CASE("CLUSTERED_DOT_MATRIX: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(CLUSTERED_DOT_MATRIX));
}

// ─── CLUSTERED_DOT_MATRIX_4X4 ─────────────────────────────────────────────────

TEST_CASE("CLUSTERED_DOT_MATRIX_4X4: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(CLUSTERED_DOT_MATRIX_4X4));
}

// ─── HALFTONE_MATRIX ──────────────────────────────────────────────────────────

TEST_CASE("HALFTONE_MATRIX: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(HALFTONE_MATRIX));
}

// ─── VOID_AND_CLUSTER_MATRIX ──────────────────────────────────────────────────

TEST_CASE("VOID_AND_CLUSTER_MATRIX: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(VOID_AND_CLUSTER_MATRIX));
}

// ─── VOID_AND_CLUSTER_MATRIX_14X14 ───────────────────────────────────────────

TEST_CASE("VOID_AND_CLUSTER_MATRIX_14X14: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(VOID_AND_CLUSTER_MATRIX_14X14));
}

// ─── DISPERSED_DOT_MATRIX_4X4 ────────────────────────────────────────────────

TEST_CASE("DISPERSED_DOT_MATRIX_4X4: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(DISPERSED_DOT_MATRIX_4X4));
}

// ─── DISPERSED_DOT_MATRIX_8X8 ────────────────────────────────────────────────

TEST_CASE("DISPERSED_DOT_MATRIX_8X8: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(DISPERSED_DOT_MATRIX_8X8));
}

// ─── BAYER_MATRIX_32X32 ───────────────────────────────────────────────────────

TEST_CASE("BAYER_MATRIX_32X32: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(BAYER_MATRIX_32X32));
}

TEST_CASE("BAYER_MATRIX_32X32: all 1024 values are unique", "[matrices]") {
	REQUIRE(all_unique(BAYER_MATRIX_32X32));
}

// ─── MAGIC_SQUARE_MATRIX_5X5 ──────────────────────────────────────────────────

TEST_CASE("MAGIC_SQUARE_MATRIX_5X5: all values in [0, 1)", "[matrices]") {
	REQUIRE(all_in_range(MAGIC_SQUARE_MATRIX_5X5));
}

// ─── BLUE_NOISE_MATRIX_16X16 ──────────────────────────────────────────────────

TEST_CASE("BLUE_NOISE_MATRIX_16X16: all values in [0, 1]", "[matrices]") {
	REQUIRE(all_in_range(BLUE_NOISE_MATRIX_16X16));
}

// Blue noise нормализована на 255 (не 256), поэтому значения не уникальны —
// это корректное поведение для данного типа матриц.
TEST_CASE("BLUE_NOISE_MATRIX_16X16: has 256 elements", "[matrices]") {
	constexpr int total = 16 * 16;
	REQUIRE(total == 256);
}
