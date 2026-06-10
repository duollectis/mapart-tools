#pragma once

#include <cstdint>

// ── Идентификаторы алгоритмов ──────────────────────────────────────────────

enum DitherAlgorithm : uint32_t {
	// Диффузия ошибки (Error Diffusion)
	FLOYD_STEINBERG = 0,
	STUCKI = 1,
	JJN = 2,
	BURKES = 3,
	SIERRA3 = 4,
	SIERRA_LITE = 5,
	ATKINSON = 6,
	SIERRA2 = 7,
	FILTER_LITE = 8,

	// Упорядоченный дизеринг (Ordered / Bayer)
	BAYER_2X2 = 9,
	BAYER_4X4 = 10,
	BAYER_8X8 = 11,

	// Без дизеринга
	NONE = 12,

	// Расширенный упорядоченный дизеринг
	BAYER_16X16 = 13,
	CLUSTERED_DOT = 14,
	HALFTONE = 15,
	VOID_AND_CLUSTER = 16,
	BAYER_3X3 = 17,
	ORDERED_3X3 = 18,
	CLUSTERED_DOT_4X4 = 19,
	VOID_AND_CLUSTER_14X14 = 20,

	// Floyd-Steinberg с нестандартными делителями (мягче/резче)
	FLOYD_STEINBERG_20 = 21,
	FLOYD_STEINBERG_24 = 22,

	// Однострочные фильтры диффузии
	FAN = 23,
	SHIAU_FAN = 24,
	SHIAU_FAN_2 = 25,

	// Дополнительные алгоритмы диффузии ошибки
	PIGEON = 26,
	NAKANO = 27,
	ZHOU_FANG = 28,

	// Дополнительные матрицы упорядоченного дизеринга
	DISPERSED_DOT_4X4 = 29,
	DISPERSED_DOT_8X8 = 30,
	BAYER_32X32 = 31,
	MAGIC_SQUARE_5X5 = 32,
	BLUE_NOISE_16X16 = 33
};

// ── Идентификаторы метрик цветового расстояния ─────────────────────────────

enum ColorMetric : uint32_t {
	METRIC_LAB = 0,
	METRIC_CIEDE2000 = 1,
	METRIC_RGB = 2,
	METRIC_OKLAB = 3,
	METRIC_WEIGHTED_RGB = 4,
	METRIC_LAB_D50 = 5,
	METRIC_CIEDE2000_D50 = 6,
	METRIC_HCT = 7,
	METRIC_OKLAB_CHROMA = 8,
	METRIC_HSL = 9,
	METRIC_HSV = 10,
	METRIC_YUV = 11,
	METRIC_YCBCR = 12,
	METRIC_IPT = 13,
	METRIC_JZAZBZ = 14
};

// ── Структуры данных ───────────────────────────────────────────────────────

struct ErrorKernel {
	int dx;
	int dy;
	double weight;
};

struct LabColor {
	double L;
	double a;
	double b;
};

// Предвычисленные данные цвета палитры для ускорения CIEDE2000.
// Вычисляются один раз при подготовке палитры, не повторяются в горячем цикле.
struct LabColorCiede {
	double L;
	double ap;  // скорректированный a' с учётом G
	double b;
	double Cp;  // C' = sqrt(ap^2 + b^2)
	double hp;  // h' = atan2(b, ap) в градусах [0, 360)
	double Cp7; // Cp^7 — для вычисления R_C
};

struct RgbColor {
	double r;
	double g;
	double b;
};

struct OklabColor {
	double L;
	double a;
	double b;
};

struct KernelView {
	const ErrorKernel* data;
	int size;
};

// Возвращает 0 — продолжать, 1 — отмена запрошена
typedef int32_t (*ProgressCallback)(int32_t progress);
