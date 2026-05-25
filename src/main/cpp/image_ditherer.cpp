#include <vector>
#include <cmath>
#include <random>
#include <algorithm>
#include <cstdint>

// ── Идентификаторы алгоритмов ──────────────────────────────────────────────

enum DitherAlgorithm : uint32_t {
	// Диффузия ошибки (Error Diffusion)
	FLOYD_STEINBERG = 0,
	STUCKI          = 1,
	JJN             = 2,
	BURKES          = 3,
	SIERRA3         = 4,
	SIERRA_LITE     = 5,
	ATKINSON        = 6,
	SIERRA2         = 7,
	FILTER_LITE     = 8,

	// Упорядоченный дизеринг (Ordered / Bayer)
	BAYER_2X2       = 9,
	BAYER_4X4       = 10,
	BAYER_8X8       = 11,

	// Без дизеринга
	NONE            = 12
};

// ── Структуры ──────────────────────────────────────────────────────────────

struct ErrorKernel {
	int    dx;
	int    dy;
	double weight;
};

struct LabColor {
	double L;
	double a;
	double b;
};

// ── Ядра диффузии ошибки ───────────────────────────────────────────────────

// Ядра хранятся как static const — инициализируются один раз, не аллоцируют память при каждом вызове
static const ErrorKernel KERNEL_FLOYD_STEINBERG[] = {
	{1, 0, 7 / 16.0}, {-1, 1, 3 / 16.0}, {0, 1, 5 / 16.0}, {1, 1, 1 / 16.0}
};
static const ErrorKernel KERNEL_STUCKI[] = {
	{1, 0, 8 / 42.0}, {2, 0, 4 / 42.0},
	{-2, 1, 2 / 42.0}, {-1, 1, 4 / 42.0}, {0, 1, 8 / 42.0}, {1, 1, 4 / 42.0}, {2, 1, 2 / 42.0},
	{-2, 2, 1 / 42.0}, {-1, 2, 2 / 42.0}, {0, 2, 4 / 42.0}, {1, 2, 2 / 42.0}, {2, 2, 1 / 42.0}
};
static const ErrorKernel KERNEL_JJN[] = {
	{1, 0, 7 / 48.0}, {2, 0, 5 / 48.0},
	{-2, 1, 3 / 48.0}, {-1, 1, 5 / 48.0}, {0, 1, 7 / 48.0}, {1, 1, 5 / 48.0}, {2, 1, 3 / 48.0},
	{-2, 2, 1 / 48.0}, {-1, 2, 3 / 48.0}, {0, 2, 5 / 48.0}, {1, 2, 3 / 48.0}, {2, 2, 1 / 48.0}
};
static const ErrorKernel KERNEL_BURKES[] = {
	{1, 0, 8 / 32.0}, {2, 0, 4 / 32.0},
	{-2, 1, 2 / 32.0}, {-1, 1, 4 / 32.0}, {0, 1, 8 / 32.0}, {1, 1, 4 / 32.0}, {2, 1, 2 / 32.0}
};
static const ErrorKernel KERNEL_SIERRA3[] = {
	{1, 0, 5 / 32.0}, {2, 0, 3 / 32.0},
	{-2, 1, 2 / 32.0}, {-1, 1, 4 / 32.0}, {0, 1, 5 / 32.0}, {1, 1, 4 / 32.0}, {2, 1, 2 / 32.0},
	{-1, 2, 2 / 32.0}, {0, 2, 3 / 32.0}, {1, 2, 2 / 32.0}
};
static const ErrorKernel KERNEL_SIERRA_LITE[] = {
	{1, 0, 2 / 4.0}, {-1, 1, 1 / 4.0}, {0, 1, 1 / 4.0}
};
static const ErrorKernel KERNEL_ATKINSON[] = {
	{1, 0, 1 / 8.0}, {2, 0, 1 / 8.0},
	{-1, 1, 1 / 8.0}, {0, 1, 1 / 8.0}, {1, 1, 1 / 8.0},
	{0, 2, 1 / 8.0}
};
// Sierra Two-Row — компромисс между Sierra3 и Sierra Lite
static const ErrorKernel KERNEL_SIERRA2[] = {
	{1, 0, 4 / 16.0}, {2, 0, 3 / 16.0},
	{-2, 1, 1 / 16.0}, {-1, 1, 2 / 16.0}, {0, 1, 3 / 16.0}, {1, 1, 2 / 16.0}, {2, 1, 1 / 16.0}
};
// Filter Lite — минимальный однострочный фильтр
static const ErrorKernel KERNEL_FILTER_LITE[] = {
	{1, 0, 3 / 8.0}, {-1, 1, 3 / 8.0}, {0, 1, 2 / 8.0}
};

struct KernelView {
	const ErrorKernel* data;
	int                size;
};

static inline KernelView get_kernel(DitherAlgorithm algorithm) {
	switch (algorithm) {
		case FLOYD_STEINBERG: return {KERNEL_FLOYD_STEINBERG, 4};
		case STUCKI:          return {KERNEL_STUCKI,          12};
		case JJN:             return {KERNEL_JJN,             12};
		case BURKES:          return {KERNEL_BURKES,          7};
		case SIERRA3:         return {KERNEL_SIERRA3,         10};
		case SIERRA_LITE:     return {KERNEL_SIERRA_LITE,     3};
		case ATKINSON:        return {KERNEL_ATKINSON,        6};
		case SIERRA2:         return {KERNEL_SIERRA2,         7};
		case FILTER_LITE:     return {KERNEL_FILTER_LITE,     3};
		default:              return {KERNEL_FLOYD_STEINBERG, 4};
	}
}

// ── Матрицы Байера ─────────────────────────────────────────────────────────

// Нормализованные матрицы Байера (значения в диапазоне [0, 1))
// Формула: M_n = (1/n²) * (базовая_матрица)
// Базовая матрица строится рекурсивно: M_2n = [[4*M_n, 4*M_n+2], [4*M_n+3, 4*M_n+1]]

static const double BAYER_MATRIX_2X2[2][2] = {
	{0.0 / 4.0, 2.0 / 4.0},
	{3.0 / 4.0, 1.0 / 4.0}
};

static const double BAYER_MATRIX_4X4[4][4] = {
	{ 0.0 / 16.0,  8.0 / 16.0,  2.0 / 16.0, 10.0 / 16.0},
	{12.0 / 16.0,  4.0 / 16.0, 14.0 / 16.0,  6.0 / 16.0},
	{ 3.0 / 16.0, 11.0 / 16.0,  1.0 / 16.0,  9.0 / 16.0},
	{15.0 / 16.0,  7.0 / 16.0, 13.0 / 16.0,  5.0 / 16.0}
};

static const double BAYER_MATRIX_8X8[8][8] = {
	{ 0.0 / 64.0, 32.0 / 64.0,  8.0 / 64.0, 40.0 / 64.0,  2.0 / 64.0, 34.0 / 64.0, 10.0 / 64.0, 42.0 / 64.0},
	{48.0 / 64.0, 16.0 / 64.0, 56.0 / 64.0, 24.0 / 64.0, 50.0 / 64.0, 18.0 / 64.0, 58.0 / 64.0, 26.0 / 64.0},
	{12.0 / 64.0, 44.0 / 64.0,  4.0 / 64.0, 36.0 / 64.0, 14.0 / 64.0, 46.0 / 64.0,  6.0 / 64.0, 38.0 / 64.0},
	{60.0 / 64.0, 28.0 / 64.0, 52.0 / 64.0, 20.0 / 64.0, 62.0 / 64.0, 30.0 / 64.0, 54.0 / 64.0, 22.0 / 64.0},
	{ 3.0 / 64.0, 35.0 / 64.0, 11.0 / 64.0, 43.0 / 64.0,  1.0 / 64.0, 33.0 / 64.0,  9.0 / 64.0, 41.0 / 64.0},
	{51.0 / 64.0, 19.0 / 64.0, 59.0 / 64.0, 27.0 / 64.0, 49.0 / 64.0, 17.0 / 64.0, 57.0 / 64.0, 25.0 / 64.0},
	{15.0 / 64.0, 47.0 / 64.0,  7.0 / 64.0, 39.0 / 64.0, 13.0 / 64.0, 45.0 / 64.0,  5.0 / 64.0, 37.0 / 64.0},
	{63.0 / 64.0, 31.0 / 64.0, 55.0 / 64.0, 23.0 / 64.0, 61.0 / 64.0, 29.0 / 64.0, 53.0 / 64.0, 21.0 / 64.0}
};

// ── Цветовые преобразования ────────────────────────────────────────────────

// Гамма-коррекция sRGB → линейное пространство
static inline double srgb_to_linear(double channel) {
	return (channel > 0.04045)
		? std::pow((channel + 0.055) / 1.055, 2.4)
		: (channel / 12.92);
}

// Нелинейная функция XYZ → Lab (кубический корень с порогом)
static inline double xyz_to_lab_f(double t) {
	return (t > 0.008856)
		? std::pow(t, 1.0 / 3.0)
		: (7.787 * t + 16.0 / 116.0);
}

static inline LabColor rgb_to_lab(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	// Матрица sRGB → XYZ (D65)
	double x = (r * 0.4124 + g * 0.3576 + b * 0.1804) * 100.0;
	double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0;
	double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) * 100.0;

	double fx = xyz_to_lab_f(x / 95.047);
	double fy = xyz_to_lab_f(y / 100.000);
	double fz = xyz_to_lab_f(z / 108.883);

	return {
		116.0 * fy - 16.0,
		500.0 * (fx - fy),
		200.0 * (fy - fz)
	};
}

static std::vector<LabColor> convert_palette_to_lab(const int32_t* palette, int32_t size) {
	std::vector<LabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = rgb_to_lab(
			(color >> 16) & 0xFF,
			(color >> 8)  & 0xFF,
			(color)       & 0xFF
		);
	}

	return result;
}

// ── Поиск ближайшего цвета в палитре ──────────────────────────────────────

static inline int find_nearest(const LabColor& pixel, const std::vector<LabColor>& palette) {
	int    best_idx = 0;
	double min_dist = 1e30;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dL   = pixel.L - palette[p].L;
		double da   = pixel.a - palette[p].a;
		double db   = pixel.b - palette[p].b;
		double dist = dL * dL + da * da + db * db;

		if (dist >= min_dist) {
			continue;
		}

		min_dist = dist;
		best_idx = p;

		if (min_dist == 0.0) {
			break;
		}
	}

	return best_idx;
}

// ── Алгоритмы дизеринга ────────────────────────────────────────────────────

/**
 * Диффузия ошибки с заданным ядром.
 * Использует змейковый обход строк (boustrophedon) для уменьшения артефактов.
 */
static void dither_error_diffusion(
	const std::vector<LabColor>& lab_palette,
	std::vector<LabColor>&       lab_buf,
	int32_t*                     dithered,
	int                          w,
	int                          h,
	KernelView                   kernel,
	double                       err_diff_rate,
	double                       noise_level
) {
	std::mt19937 rng(42);
	std::uniform_real_distribution<double> noise_dist(-noise_level, noise_level);

	for (int y = 0; y < h; ++y) {
		// Змейковый обход строк (boustrophedon) для уменьшения артефактов дизеринга
		bool reversed = (y % 2 != 0);
		int start_x   = reversed ? (w - 1) : 0;
		int end_x     = reversed ? -1 : w;
		int step_x    = reversed ? -1 : 1;

		for (int x = start_x; x != end_x; x += step_x) {
			int idx = y * w + x;

			LabColor pixel = {
				lab_buf[idx].L + noise_dist(rng),
				lab_buf[idx].a + noise_dist(rng),
				lab_buf[idx].b + noise_dist(rng)
			};

			int best_idx = find_nearest(pixel, lab_palette);
			dithered[idx] = best_idx;

			double eL = (pixel.L - lab_palette[best_idx].L) * err_diff_rate;
			double ea = (pixel.a - lab_palette[best_idx].a) * err_diff_rate;
			double eb = (pixel.b - lab_palette[best_idx].b) * err_diff_rate;

			for (int k = 0; k < kernel.size; ++k) {
				// step_x автоматически зеркалит ядро при обратном проходе
				int nx = x + kernel.data[k].dx * step_x;
				int ny = y + kernel.data[k].dy;

				if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
					int n_idx = ny * w + nx;
					lab_buf[n_idx].L += eL * kernel.data[k].weight;
					lab_buf[n_idx].a += ea * kernel.data[k].weight;
					lab_buf[n_idx].b += eb * kernel.data[k].weight;
				}
			}
		}
	}
}

/**
 * Упорядоченный дизеринг Байера.
 * Для каждого пикселя добавляет смещение из матрицы порогов перед поиском
 * ближайшего цвета. Смещение масштабируется относительно диапазона яркости
 * палитры, что даёт корректный результат для произвольных палитр.
 *
 * @param matrix_size  размер матрицы (2, 4 или 8)
 * @param get_threshold  функция получения порога по координатам (x % size, y % size)
 */
template<int N, const double (*Matrix)[N]>
static void dither_bayer(
	const std::vector<LabColor>& lab_palette,
	const std::vector<LabColor>& lab_buf,
	int32_t*                     dithered,
	int                          w,
	int                          h
) {
	// Вычисляем диапазон яркости палитры для масштабирования порога
	double min_L = lab_palette[0].L;
	double max_L = lab_palette[0].L;

	for (const auto& c : lab_palette) {
		if (c.L < min_L) { min_L = c.L; }
		if (c.L > max_L) { max_L = c.L; }
	}

	// Масштаб смещения — половина шага между соседними уровнями яркости
	double L_range = (max_L - min_L) / static_cast<double>(lab_palette.size());
	double scale   = L_range * 0.5;

	for (int y = 0; y < h; ++y) {
		for (int x = 0; x < w; ++x) {
			int idx = y * w + x;

			// Порог из матрицы Байера, центрированный вокруг нуля: [-0.5, +0.5)
			double threshold = Matrix[y % N][x % N] - 0.5;

			LabColor pixel = {
				lab_buf[idx].L + threshold * scale,
				lab_buf[idx].a,
				lab_buf[idx].b
			};

			dithered[idx] = find_nearest(pixel, lab_palette);
		}
	}
}

/**
 * Без дизеринга — простой поиск ближайшего цвета в палитре.
 */
static void dither_none(
	const std::vector<LabColor>& lab_palette,
	const std::vector<LabColor>& lab_buf,
	int32_t*                     dithered,
	int                          w,
	int                          h
) {
	for (int i = 0; i < w * h; ++i) {
		dithered[i] = find_nearest(lab_buf[i], lab_palette);
	}
}

// ── Точка входа ────────────────────────────────────────────────────────────

extern "C" void dither(
	int32_t* palette,
	int32_t  psize,
	uint8_t* image,
	int32_t  w,
	int32_t  h,
	int32_t* dithered,
	int32_t  algorithm,
	double   err_diff_rate,
	double   noise_level
) {
	auto lab_palette = convert_palette_to_lab(palette, psize);

	// Конвертируем всё изображение в Lab заранее, чтобы не делать это в горячем цикле
	std::vector<LabColor> lab_buf(w * h);
	for (int i = 0; i < w * h; ++i) {
		// Порядок байт в BufferedImage.TYPE_3BYTE_BGR: B, G, R
		lab_buf[i] = rgb_to_lab(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
	}

	DitherAlgorithm algo = static_cast<DitherAlgorithm>(algorithm);

	switch (algo) {
		case BAYER_2X2:
			dither_bayer<2, BAYER_MATRIX_2X2>(lab_palette, lab_buf, dithered, w, h);
			break;

		case BAYER_4X4:
			dither_bayer<4, BAYER_MATRIX_4X4>(lab_palette, lab_buf, dithered, w, h);
			break;

		case BAYER_8X8:
			dither_bayer<8, BAYER_MATRIX_8X8>(lab_palette, lab_buf, dithered, w, h);
			break;

		case NONE:
			dither_none(lab_palette, lab_buf, dithered, w, h);
			break;

		default: {
			KernelView kernel = get_kernel(algo);
			dither_error_diffusion(lab_palette, lab_buf, dithered, w, h, kernel, err_diff_rate, noise_level);
			break;
		}
	}
}
