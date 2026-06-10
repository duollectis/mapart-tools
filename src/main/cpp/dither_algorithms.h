#pragma once

#include "dither_types.h"
#include "dither_color.h"

#include <vector>
#include <random>
#include <cstdint>

// Все предвычисленные палитры в нужных цветовых пространствах.
struct PaletteBuffers {
	std::vector<LabColor> lab;
	std::vector<LabColor> lab_d50;
	std::vector<LabColor> hct;
	std::vector<LabColor> hsl;
	std::vector<LabColor> hsv;
	std::vector<LabColor> yuv;
	std::vector<LabColor> ycbcr;
	std::vector<LabColor> ipt;
	std::vector<LabColor> jzazbz;
	std::vector<RgbColor> rgb;
	std::vector<OklabColor> oklab;
};

// Вспомогательная функция: диффузия ошибки в буфер типа LabColor.
static inline void diffuse_lab_error(
	std::vector<LabColor>& buf,
	int idx,
	const LabColor& palette_color,
	double noise,
	double chan_avg,
	const KernelView& kernel,
	int x,
	int y,
	int w,
	int h,
	int step_x,
	int clip_x,
	int clip_x2,
	int clip_y,
	int clip_y2
) {
	double eL = (buf[idx].L + noise - palette_color.L) * chan_avg;
	double ea = (buf[idx].a + noise - palette_color.a) * chan_avg;
	double eb = (buf[idx].b + noise - palette_color.b) * chan_avg;

	for (int k = 0; k < kernel.size; ++k) {
		int nx = x + kernel.data[k].dx * step_x;
		int ny = y + kernel.data[k].dy;

		if (nx >= 0 && nx < w && ny >= 0 && ny < h
			&& nx >= clip_x && nx < clip_x2
			&& ny >= clip_y && ny < clip_y2
		) {
			int n_idx = ny * w + nx;
			buf[n_idx].L += eL * kernel.data[k].weight;
			buf[n_idx].a += ea * kernel.data[k].weight;
			buf[n_idx].b += eb * kernel.data[k].weight;
		}
	}
}

static constexpr double noise_scale_for_metric(uint32_t m) {
	switch (m) {
		case METRIC_OKLAB:
		case METRIC_OKLAB_CHROMA:
		case METRIC_HSL:
		case METRIC_HSV:
		case METRIC_YUV:
		case METRIC_YCBCR:
		case METRIC_IPT:
		case METRIC_JZAZBZ:
			return 0.01;
		default:
			return 1.0;
	}
}

/**
 * Диффузия ошибки с заданным ядром.
 * Использует змейковый обход строк (boustrophedon) для уменьшения артефактов.
 * Ошибка вычисляется и распространяется в пространстве метрики (LAB, OKLab или RGB).
 * Параметры err_rate_r/g/b независимо масштабируют ошибку по каждому RGB-каналу
 * (1.0 = 100%, диапазон 0.0–2.0). Для не-RGB метрик применяется среднее трёх значений.
 */
static void dither_error_diffusion(
	const PaletteBuffers& pal,
	PixelBuffers& bufs,
	int32_t* dithered,
	int w,
	int h,
	KernelView kernel,
	double err_rate_r,
	double err_rate_g,
	double err_rate_b,
	double noise_level,
	ColorMetric metric,
	ProgressCallback on_progress,
	int clip_x,
	int clip_y,
	int clip_w,
	int clip_h
) {
	std::mt19937 rng(42);

	// OKLab и нормализованные пространства (HSL/HSV/YUV/YCbCr/IPT/JzAzBz) имеют
	// компоненты в диапазоне [0..1], тогда как Lab — [0..100]. Шум масштабируется
	// соответственно, чтобы ±100% слайдера давало одинаковый визуальный эффект.
	double noise_scale = noise_scale_for_metric(static_cast<uint32_t>(metric));
	std::uniform_real_distribution<double> noise_dist(-noise_level * noise_scale, noise_level * noise_scale);

	int clip_x2 = clip_x + clip_w;
	int clip_y2 = clip_y + clip_h;
	double chan_avg = (err_rate_r + err_rate_g + err_rate_b) / 3.0;

	for (int y = 0; y < h; ++y) {
		// Змейковый обход строк (boustrophedon) для уменьшения артефактов дизеринга
		bool reversed = (y % 2 != 0);
		int start_x = reversed ? (w - 1) : 0;
		int end_x = reversed ? -1 : w;
		int step_x = reversed ? -1 : 1;

		for (int x = start_x; x != end_x; x += step_x) {
			int idx = y * w + x;

			// Пиксели вне clip_rect не дизерятся — sentinel -1 означает "пустой пиксель".
			// Java-сторона отображает -1 как чёрный в превью и null в resolved.
			bool in_clip = (x >= clip_x && x < clip_x2 && y >= clip_y && y < clip_y2);

			if (!in_clip) {
				dithered[idx] = -1;
				continue;
			}

			int best_idx = find_nearest(
				idx, bufs,
				pal.lab, pal.lab_d50, pal.hct, pal.rgb, pal.oklab,
				pal.hsl, pal.hsv, pal.yuv, pal.ycbcr, pal.ipt, pal.jzazbz,
				metric
			);
			dithered[idx] = best_idx;

			double noise = noise_dist(rng);

			if (metric == METRIC_RGB || metric == METRIC_WEIGHTED_RGB) {
				double er = (bufs.rgb_buf[idx].r + noise - pal.rgb[best_idx].r) * err_rate_r;
				double eg = (bufs.rgb_buf[idx].g + noise - pal.rgb[best_idx].g) * err_rate_g;
				double eb = (bufs.rgb_buf[idx].b + noise - pal.rgb[best_idx].b) * err_rate_b;

				for (int k = 0; k < kernel.size; ++k) {
					int nx = x + kernel.data[k].dx * step_x;
					int ny = y + kernel.data[k].dy;

					if (nx >= 0 && nx < w && ny >= 0 && ny < h
						&& nx >= clip_x && nx < clip_x2
						&& ny >= clip_y && ny < clip_y2
					) {
						int n_idx = ny * w + nx;
						bufs.rgb_buf[n_idx].r += er * kernel.data[k].weight;
						bufs.rgb_buf[n_idx].g += eg * kernel.data[k].weight;
						bufs.rgb_buf[n_idx].b += eb * kernel.data[k].weight;
					}
				}
			} else if (metric == METRIC_OKLAB || metric == METRIC_OKLAB_CHROMA) {
				double eL = (bufs.oklab_buf[idx].L + noise - pal.oklab[best_idx].L) * chan_avg;
				double ea = (bufs.oklab_buf[idx].a + noise - pal.oklab[best_idx].a) * chan_avg;
				double eb = (bufs.oklab_buf[idx].b + noise - pal.oklab[best_idx].b) * chan_avg;

				for (int k = 0; k < kernel.size; ++k) {
					int nx = x + kernel.data[k].dx * step_x;
					int ny = y + kernel.data[k].dy;

					if (nx >= 0 && nx < w && ny >= 0 && ny < h
						&& nx >= clip_x && nx < clip_x2
						&& ny >= clip_y && ny < clip_y2
					) {
						int n_idx = ny * w + nx;
						bufs.oklab_buf[n_idx].L += eL * kernel.data[k].weight;
						bufs.oklab_buf[n_idx].a += ea * kernel.data[k].weight;
						bufs.oklab_buf[n_idx].b += eb * kernel.data[k].weight;
					}
				}
			} else if (metric == METRIC_LAB_D50 || metric == METRIC_CIEDE2000_D50) {
				diffuse_lab_error(
					bufs.lab_d50_buf, idx, pal.lab_d50[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_HCT) {
				diffuse_lab_error(
					bufs.hct_buf, idx, pal.hct[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_HSL) {
				diffuse_lab_error(
					bufs.hsl_buf, idx, pal.hsl[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_HSV) {
				diffuse_lab_error(
					bufs.hsv_buf, idx, pal.hsv[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_YUV) {
				diffuse_lab_error(
					bufs.yuv_buf, idx, pal.yuv[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_YCBCR) {
				diffuse_lab_error(
					bufs.ycbcr_buf, idx, pal.ycbcr[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_IPT) {
				diffuse_lab_error(
					bufs.ipt_buf, idx, pal.ipt[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else if (metric == METRIC_JZAZBZ) {
				diffuse_lab_error(
					bufs.jzazbz_buf, idx, pal.jzazbz[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			} else {
				diffuse_lab_error(
					bufs.lab_buf, idx, pal.lab[best_idx],
					noise, chan_avg, kernel, x, y, w, h, step_x,
					clip_x, clip_x2, clip_y, clip_y2
				);
			}
		}

		if (on_progress != nullptr && on_progress((y + 1) * 100 / h) != 0) {
			return;
		}
	}
}

// Вспомогательная функция: упорядоченный дизеринг для буфера типа LabColor.
// buf_field — указатель на поле PixelBuffers, в которое нужно положить смещённый пиксель.
template<int N, const double (*Matrix)[N]>
static inline void ordered_lab_pass(
	std::vector<LabColor>& buf,
	const std::vector<LabColor>& palette,
	const PaletteBuffers& pal,
	std::vector<LabColor> PixelBuffers::*buf_field,
	int32_t* dithered,
	int w,
	int h,
	ColorMetric metric,
	ProgressCallback on_progress
) {
	double min_L = palette[0].L;
	double max_L = palette[0].L;

	for (const auto& c : palette) {
		if (c.L < min_L) { min_L = c.L; }
		if (c.L > max_L) { max_L = c.L; }
	}

	double scale = (max_L - min_L) * 0.25;

	for (int y = 0; y < h; ++y) {
		for (int x = 0; x < w; ++x) {
			int idx = y * w + x;
			double threshold = Matrix[y % N][x % N] - 0.5;

			LabColor pixel = {
				buf[idx].L + threshold * scale,
				buf[idx].a,
				buf[idx].b
			};

			PixelBuffers tmp;
			(tmp.*buf_field) = {pixel};
			dithered[idx] = find_nearest(
				0, tmp,
				pal.lab, pal.lab_d50, pal.hct, pal.rgb, pal.oklab,
				pal.hsl, pal.hsv, pal.yuv, pal.ycbcr, pal.ipt, pal.jzazbz,
				metric
			);
		}

		if (on_progress != nullptr && on_progress((y + 1) * 100 / h) != 0) {
			return;
		}
	}
}

/**
 * Упорядоченный дизеринг с произвольной матрицей порогов.
 * Для каждого пикселя добавляет смещение из матрицы перед поиском ближайшего цвета.
 * Смещение масштабируется относительно диапазона яркости палитры.
 *
 * @tparam N  размер матрицы (2, 4, 6, 8, 16 и т.д.)
 */
template<int N, const double (*Matrix)[N]>
static void dither_ordered(
	const PaletteBuffers& pal,
	PixelBuffers& bufs,
	int32_t* dithered,
	int w,
	int h,
	ColorMetric metric,
	ProgressCallback on_progress
) {
	if (metric == METRIC_RGB || metric == METRIC_WEIGHTED_RGB) {
		double min_r = pal.rgb[0].r;
		double max_r = pal.rgb[0].r;

		for (const auto& c : pal.rgb) {
			if (c.r < min_r) { min_r = c.r; }
			if (c.r > max_r) { max_r = c.r; }
		}

		// Масштаб = четверть полного диапазона яркости палитры.
		// Это гарантирует, что смещение пересекает пороги между уровнями.
		double scale = (max_r - min_r) * 0.25;

		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				int idx = y * w + x;
				double threshold = Matrix[y % N][x % N] - 0.5;

				RgbColor pixel = {
					bufs.rgb_buf[idx].r + threshold * scale,
					bufs.rgb_buf[idx].g,
					bufs.rgb_buf[idx].b
				};

				PixelBuffers tmp;
				tmp.rgb_buf = {pixel};
				dithered[idx] = find_nearest(
					0, tmp,
					pal.lab, pal.lab_d50, pal.hct, pal.rgb, pal.oklab,
					pal.hsl, pal.hsv, pal.yuv, pal.ycbcr, pal.ipt, pal.jzazbz,
					metric
				);
			}

			if (on_progress != nullptr && on_progress((y + 1) * 100 / h) != 0) {
				return;
			}
		}

		return;
	}

	if (metric == METRIC_OKLAB || metric == METRIC_OKLAB_CHROMA) {
		double min_L = pal.oklab[0].L;
		double max_L = pal.oklab[0].L;

		for (const auto& c : pal.oklab) {
			if (c.L < min_L) { min_L = c.L; }
			if (c.L > max_L) { max_L = c.L; }
		}

		double scale = (max_L - min_L) * 0.25;

		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				int idx = y * w + x;
				double threshold = Matrix[y % N][x % N] - 0.5;

				OklabColor pixel = {
					bufs.oklab_buf[idx].L + threshold * scale,
					bufs.oklab_buf[idx].a,
					bufs.oklab_buf[idx].b
				};

				PixelBuffers tmp;
				tmp.oklab_buf = {pixel};
				dithered[idx] = find_nearest(
					0, tmp,
					pal.lab, pal.lab_d50, pal.hct, pal.rgb, pal.oklab,
					pal.hsl, pal.hsv, pal.yuv, pal.ycbcr, pal.ipt, pal.jzazbz,
					metric
				);
			}

			if (on_progress != nullptr && on_progress((y + 1) * 100 / h) != 0) {
				return;
			}
		}

		return;
	}

	if (metric == METRIC_LAB_D50 || metric == METRIC_CIEDE2000_D50) {
		ordered_lab_pass<N, Matrix>(
			bufs.lab_d50_buf, pal.lab_d50, pal, &PixelBuffers::lab_d50_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_HCT) {
		ordered_lab_pass<N, Matrix>(
			bufs.hct_buf, pal.hct, pal, &PixelBuffers::hct_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_HSL) {
		ordered_lab_pass<N, Matrix>(
			bufs.hsl_buf, pal.hsl, pal, &PixelBuffers::hsl_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_HSV) {
		ordered_lab_pass<N, Matrix>(
			bufs.hsv_buf, pal.hsv, pal, &PixelBuffers::hsv_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_YUV) {
		ordered_lab_pass<N, Matrix>(
			bufs.yuv_buf, pal.yuv, pal, &PixelBuffers::yuv_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_YCBCR) {
		ordered_lab_pass<N, Matrix>(
			bufs.ycbcr_buf, pal.ycbcr, pal, &PixelBuffers::ycbcr_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_IPT) {
		ordered_lab_pass<N, Matrix>(
			bufs.ipt_buf, pal.ipt, pal, &PixelBuffers::ipt_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	if (metric == METRIC_JZAZBZ) {
		ordered_lab_pass<N, Matrix>(
			bufs.jzazbz_buf, pal.jzazbz, pal, &PixelBuffers::jzazbz_buf, dithered, w, h, metric, on_progress
		);
		return;
	}

	// LAB и CIEDE2000 — смещение по каналу L
	ordered_lab_pass<N, Matrix>(
		bufs.lab_buf, pal.lab, pal, &PixelBuffers::lab_buf, dithered, w, h, metric, on_progress
	);
}

static void dither_none(
	const PaletteBuffers& pal,
	const PixelBuffers& bufs,
	int32_t* dithered,
	int w,
	int h,
	ColorMetric metric,
	ProgressCallback on_progress
) {
	int total = w * h;

	for (int i = 0; i < total; ++i) {
		dithered[i] = find_nearest(
			i, bufs,
			pal.lab, pal.lab_d50, pal.hct, pal.rgb, pal.oklab,
			pal.hsl, pal.hsv, pal.yuv, pal.ycbcr, pal.ipt, pal.jzazbz,
			metric
		);

		if (on_progress != nullptr && (i + 1) % w == 0 && on_progress((i + 1) * 100 / total) != 0) {
			return;
		}
	}
}
