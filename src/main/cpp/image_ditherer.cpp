#include "dither_types.h"
#include "dither_matrices.h"
#include "dither_kernels.h"
#include "dither_color.h"
#include "dither_algorithms.h"

#include <cstdint>

// ── Точка входа ────────────────────────────────────────────────────────────

extern "C" void dither(
	int32_t* palette,
	int32_t psize,
	uint8_t* image,
	int32_t w,
	int32_t h,
	int32_t* dithered,
	int32_t algorithm,
	double err_rate_r,
	double err_rate_g,
	double err_rate_b,
	double noise_level,
	int32_t color_metric,
	ProgressCallback on_progress,
	int32_t clip_x,
	int32_t clip_y,
	int32_t clip_w,
	int32_t clip_h
) {
	ColorMetric metric = static_cast<ColorMetric>(color_metric);

	bool has_clip = (clip_w > 0 && clip_h > 0);
	int eff_clip_x = has_clip ? clip_x : 0;
	int eff_clip_y = has_clip ? clip_y : 0;
	int eff_clip_w = has_clip ? clip_w : w;
	int eff_clip_h = has_clip ? clip_h : h;

	PaletteBuffers pal;
	pal.lab = convert_palette_to_lab(palette, psize);
	pal.lab_d50 = convert_palette_to_lab_d50(palette, psize);
	pal.hct = convert_palette_to_hct(palette, psize);
	pal.rgb = convert_palette_to_rgb(palette, psize);
	pal.oklab = convert_palette_to_oklab(palette, psize);
	pal.hsl = convert_palette_generic(palette, psize, rgb_to_hsl);
	pal.hsv = convert_palette_generic(palette, psize, rgb_to_hsv);
	pal.yuv = convert_palette_generic(palette, psize, rgb_to_yuv);
	pal.ycbcr = convert_palette_generic(palette, psize, rgb_to_ycbcr);
	pal.ipt = convert_palette_generic(palette, psize, rgb_to_ipt);
	pal.jzazbz = convert_palette_generic(palette, psize, rgb_to_jzazbz);

	PixelBuffers bufs = build_pixel_buffers(image, w, h, metric, pal.lab, pal.lab_d50, pal.hct);

	DitherAlgorithm algo = static_cast<DitherAlgorithm>(algorithm);

	switch (algo) {
		case BAYER_2X2:
			dither_ordered<2, BAYER_MATRIX_2X2>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BAYER_4X4:
			dither_ordered<4, BAYER_MATRIX_4X4>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BAYER_8X8:
			dither_ordered<8, BAYER_MATRIX_8X8>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BAYER_16X16:
			dither_ordered<16, BAYER_MATRIX_16X16>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case CLUSTERED_DOT:
			dither_ordered<6, CLUSTERED_DOT_MATRIX>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case HALFTONE:
			dither_ordered<8, HALFTONE_MATRIX>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case VOID_AND_CLUSTER:
			dither_ordered<8, VOID_AND_CLUSTER_MATRIX>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BAYER_3X3:
			dither_ordered<3, BAYER_MATRIX_3X3>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case ORDERED_3X3:
			dither_ordered<3, ORDERED_MATRIX_3X3>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case CLUSTERED_DOT_4X4:
			dither_ordered<4, CLUSTERED_DOT_MATRIX_4X4>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case VOID_AND_CLUSTER_14X14:
			dither_ordered<14, VOID_AND_CLUSTER_MATRIX_14X14>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case DISPERSED_DOT_4X4:
			dither_ordered<4, DISPERSED_DOT_MATRIX_4X4>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case DISPERSED_DOT_8X8:
			dither_ordered<8, DISPERSED_DOT_MATRIX_8X8>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BAYER_32X32:
			dither_ordered<32, BAYER_MATRIX_32X32>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case MAGIC_SQUARE_5X5:
			dither_ordered<5, MAGIC_SQUARE_MATRIX_5X5>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case BLUE_NOISE_16X16:
			dither_ordered<16, BLUE_NOISE_MATRIX_16X16>(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		case NONE:
			dither_none(pal, bufs, dithered, w, h, metric, on_progress);
			break;

		default: {
			KernelView kernel = get_kernel(algo);
			dither_error_diffusion(
				pal, bufs, dithered, w, h, kernel,
				err_rate_r, err_rate_g, err_rate_b,
				noise_level, metric, on_progress,
				eff_clip_x, eff_clip_y, eff_clip_w, eff_clip_h
			);
			break;
		}
	}
}
