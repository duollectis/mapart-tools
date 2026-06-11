#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include "dither_kernels.h"

using Catch::Matchers::WithinAbs;

// Вспомогательная функция: сумма весов ядра
static double kernel_weight_sum(const ErrorKernel* data, int size) {
	double sum = 0.0;

	for (int i = 0; i < size; ++i) {
		sum += data[i].weight;
	}

	return sum;
}

// ── get_kernel: размеры ───────────────────────────────────────────────────────

TEST_CASE("get_kernel: FLOYD_STEINBERG has 4 entries", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: STUCKI has 12 entries", "[kernels]") {
	auto kv = get_kernel(STUCKI);
	REQUIRE(kv.size == 12);
}

TEST_CASE("get_kernel: JJN has 12 entries", "[kernels]") {
	auto kv = get_kernel(JJN);
	REQUIRE(kv.size == 12);
}

TEST_CASE("get_kernel: BURKES has 7 entries", "[kernels]") {
	auto kv = get_kernel(BURKES);
	REQUIRE(kv.size == 7);
}

TEST_CASE("get_kernel: SIERRA3 has 10 entries", "[kernels]") {
	auto kv = get_kernel(SIERRA3);
	REQUIRE(kv.size == 10);
}

TEST_CASE("get_kernel: SIERRA_LITE has 3 entries", "[kernels]") {
	auto kv = get_kernel(SIERRA_LITE);
	REQUIRE(kv.size == 3);
}

TEST_CASE("get_kernel: ATKINSON has 6 entries", "[kernels]") {
	auto kv = get_kernel(ATKINSON);
	REQUIRE(kv.size == 6);
}

TEST_CASE("get_kernel: SIERRA2 has 7 entries", "[kernels]") {
	auto kv = get_kernel(SIERRA2);
	REQUIRE(kv.size == 7);
}

TEST_CASE("get_kernel: FILTER_LITE has 3 entries", "[kernels]") {
	auto kv = get_kernel(FILTER_LITE);
	REQUIRE(kv.size == 3);
}

TEST_CASE("get_kernel: FLOYD_STEINBERG_20 has 4 entries", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG_20);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: FLOYD_STEINBERG_24 has 4 entries", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG_24);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: FAN has 4 entries", "[kernels]") {
	auto kv = get_kernel(FAN);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: SHIAU_FAN has 4 entries", "[kernels]") {
	auto kv = get_kernel(SHIAU_FAN);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: SHIAU_FAN_2 has 5 entries", "[kernels]") {
	auto kv = get_kernel(SHIAU_FAN_2);
	REQUIRE(kv.size == 5);
}

TEST_CASE("get_kernel: PIGEON has 4 entries", "[kernels]") {
	auto kv = get_kernel(PIGEON);
	REQUIRE(kv.size == 4);
}

TEST_CASE("get_kernel: NAKANO has 12 entries", "[kernels]") {
	auto kv = get_kernel(NAKANO);
	REQUIRE(kv.size == 12);
}

TEST_CASE("get_kernel: ZHOU_FANG has 7 entries", "[kernels]") {
	auto kv = get_kernel(ZHOU_FANG);
	REQUIRE(kv.size == 7);
}

TEST_CASE("get_kernel: unknown algorithm falls back to FLOYD_STEINBERG (4 entries)", "[kernels]") {
	// BAYER_2X2 не является ядром диффузии — get_kernel вернёт дефолт
	auto kv = get_kernel(static_cast<DitherAlgorithm>(9999));
	REQUIRE(kv.size == 4);
}

// ── Суммы весов ───────────────────────────────────────────────────────────────

TEST_CASE("FLOYD_STEINBERG weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("STUCKI weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(STUCKI);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("JJN weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(JJN);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("BURKES weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(BURKES);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("SIERRA3 weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(SIERRA3);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("SIERRA_LITE weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(SIERRA_LITE);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("ATKINSON weights sum to 0.75 (intentional loss)", "[kernels]") {
	// Atkinson намеренно распределяет только 6/8 = 0.75 ошибки
	auto kv = get_kernel(ATKINSON);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(0.75, 1e-9));
}

TEST_CASE("SIERRA2 weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(SIERRA2);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("FILTER_LITE weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(FILTER_LITE);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("FLOYD_STEINBERG_20 weights sum to 16/20 = 0.8 (intentional soft diffusion)", "[kernels]") {
	// Делитель /20 вместо /16 — намеренно распределяет меньше ошибки для мягкого эффекта
	auto kv = get_kernel(FLOYD_STEINBERG_20);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(16.0 / 20.0, 1e-9));
}

TEST_CASE("FLOYD_STEINBERG_24 weights sum to 16/24 (intentional minimal diffusion)", "[kernels]") {
	// Делитель /24 вместо /16 — минимальный дизеринг, ещё меньше ошибки
	auto kv = get_kernel(FLOYD_STEINBERG_24);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(16.0 / 24.0, 1e-9));
}

TEST_CASE("FAN weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(FAN);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("SHIAU_FAN weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(SHIAU_FAN);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("SHIAU_FAN_2 weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(SHIAU_FAN_2);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("PIGEON weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(PIGEON);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("NAKANO weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(NAKANO);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

TEST_CASE("ZHOU_FANG weights sum to 1.0", "[kernels]") {
	auto kv = get_kernel(ZHOU_FANG);
	REQUIRE_THAT(kernel_weight_sum(kv.data, kv.size), WithinAbs(1.0, 1e-9));
}

// ── Структура ядра Floyd-Steinberg ────────────────────────────────────────────

TEST_CASE("FLOYD_STEINBERG: first entry propagates right (dx=1, dy=0)", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG);
	REQUIRE(kv.data[0].dx == 1);
	REQUIRE(kv.data[0].dy == 0);
	REQUIRE_THAT(kv.data[0].weight, WithinAbs(7.0 / 16.0, 1e-10));
}

TEST_CASE("FLOYD_STEINBERG: all dy values are 0 or 1 (two-row kernel)", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG);

	for (int i = 0; i < kv.size; ++i) {
		REQUIRE(kv.data[i].dy >= 0);
		REQUIRE(kv.data[i].dy <= 1);
	}
}

TEST_CASE("FLOYD_STEINBERG: all weights are positive", "[kernels]") {
	auto kv = get_kernel(FLOYD_STEINBERG);

	for (int i = 0; i < kv.size; ++i) {
		REQUIRE(kv.data[i].weight > 0.0);
	}
}

// ── Структура ядра Stucki ─────────────────────────────────────────────────────

TEST_CASE("STUCKI: spans 3 rows (dy in [0, 2])", "[kernels]") {
	auto kv = get_kernel(STUCKI);
	int max_dy = 0;

	for (int i = 0; i < kv.size; ++i) {
		if (kv.data[i].dy > max_dy) {
			max_dy = kv.data[i].dy;
		}
	}

	REQUIRE(max_dy == 2);
}

// ── Структура ядра Atkinson ───────────────────────────────────────────────────

TEST_CASE("ATKINSON: spans 3 rows (dy in [0, 2])", "[kernels]") {
	auto kv = get_kernel(ATKINSON);
	int max_dy = 0;

	for (int i = 0; i < kv.size; ++i) {
		if (kv.data[i].dy > max_dy) {
			max_dy = kv.data[i].dy;
		}
	}

	REQUIRE(max_dy == 2);
}

TEST_CASE("ATKINSON: all weights equal 1/8", "[kernels]") {
	auto kv = get_kernel(ATKINSON);

	for (int i = 0; i < kv.size; ++i) {
		REQUIRE_THAT(kv.data[i].weight, WithinAbs(1.0 / 8.0, 1e-10));
	}
}
