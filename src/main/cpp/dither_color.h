#pragma once

#include "dither_types.h"

#include <vector>
#include <cmath>
#include <cstdint>

// ── Цветовые преобразования ────────────────────────────────────────────────

static inline double srgb_to_linear(double channel) {
	return (channel > 0.04045)
		? std::pow((channel + 0.055) / 1.055, 2.4)
		: (channel / 12.92);
}

static inline double xyz_to_lab_f(double t) {
	return (t > 0.008856)
		? std::pow(t, 1.0 / 3.0)
		: (7.787 * t + 16.0 / 116.0);
}

// CIE L*a*b* с белой точкой D65 (стандарт sRGB)
static inline LabColor rgb_to_lab(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

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

// CIE L*a*b* с белой точкой D50 — стандарт ICC/печати.
// Матрица sRGB→XYZ адаптирована под D50 через Bradford chromatic adaptation.
static inline LabColor rgb_to_lab_d50(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	double x = (r * 0.4360747 + g * 0.3850649 + b * 0.1430804) * 100.0;
	double y = (r * 0.2225045 + g * 0.7168786 + b * 0.0606169) * 100.0;
	double z = (r * 0.0139322 + g * 0.0971045 + b * 0.7141733) * 100.0;

	// Белая точка D50: X=96.422, Y=100.000, Z=82.521
	double fx = xyz_to_lab_f(x / 96.422);
	double fy = xyz_to_lab_f(y / 100.000);
	double fz = xyz_to_lab_f(z / 82.521);

	return {
		116.0 * fy - 16.0,
		500.0 * (fx - fy),
		200.0 * (fy - fz)
	};
}

// HCT (Hue, Chroma, Tone) — цветовое пространство Material Design 3.
// Tone = L* из CIE L*a*b* D65, Hue/Chroma из CAM16.
// Для метрики расстояния используем евклидово в пространстве (J, a, b) CAM16-UCS.
static inline LabColor rgb_to_hct(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	// XYZ D65
	double x = r * 0.4124 + g * 0.3576 + b * 0.1804;
	double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
	double z = r * 0.0193 + g * 0.1192 + b * 0.9505;

	// CAM16 адаптация: условия просмотра sRGB (D65, Lw=64, Yb=20, surround=average)
	static constexpr double F = 1.0;
	static constexpr double c = 0.69;
	static constexpr double Nc = 1.0;
	static constexpr double Yw = 100.0;
	static constexpr double Lw = 64.0;
	static constexpr double Yb = 20.0;

	double La = Lw * Yb / Yw;
	double k = 1.0 / (5.0 * La + 1.0);
	double k4 = k * k * k * k;
	double FL = 0.2 * k4 * (5.0 * La) + 0.1 * (1.0 - k4) * (1.0 - k4) * std::cbrt(5.0 * La);
	double n = Yb / Yw;
	double Nbb = 0.725 * std::pow(1.0 / n, 0.2);
	double z_cam = 1.48 + std::sqrt(50.0 * n);

	// Матрица CAT16: XYZ → RGB_CAT16
	double Rw = 0.401288 * 95.047 + 0.650173 * 100.0 - 0.051461 * 108.883;
	double Gw = -0.250268 * 95.047 + 1.204414 * 100.0 + 0.045854 * 108.883;
	double Bw = -0.002079 * 95.047 + 0.048952 * 100.0 + 0.953127 * 108.883;

	double D = F * (1.0 - (1.0 / 3.6) * std::exp((-La - 42.0) / 92.0));
	D = std::max(0.0, std::min(1.0, D));

	double Rc = x * 100.0 * (D * Yw / Rw + 1.0 - D);
	double Gc = y * 100.0 * (D * Yw / Gw + 1.0 - D);
	double Bc = z * 100.0 * (D * Yw / Bw + 1.0 - D);

	// HPE матрица: RGB_CAT16 → RGB_HPE
	double Rp = 0.38971 * Rc + 0.68898 * Gc - 0.07868 * Bc;
	double Gp = -0.22981 * Rc + 1.18340 * Gc + 0.04641 * Bc;
	double Bp = Bc;

	// Нелинейная адаптация
	auto adapt = [&](double v) -> double {
		double vf = std::pow(FL * std::abs(v) / 100.0, 0.42);
		return std::copysign(400.0 * vf / (vf + 27.13) + 0.1, v);
	};

	double Ra = adapt(Rp);
	double Ga = adapt(Gp);
	double Ba = adapt(Bp);

	double A = (2.0 * Ra + Ga + 0.05 * Ba - 0.305) * Nbb;
	double J = 100.0 * std::pow(A / (Nbb * 100.0 * std::pow(FL / 100.0, 0.42) * 400.0 / (400.0 + 27.13) + 0.1 * Nbb), c * z_cam);

	double a = Ra - 12.0 * Ga / 11.0 + Ba / 11.0;
	double b_cam = (Ra + Ga - 2.0 * Ba) / 9.0;

	// CAM16-UCS: J' = 1.7 * J / (1 + 0.007 * J), M' = ln(1 + 0.0228 * M) / 0.0228
	double Jp = 1.7 * J / (1.0 + 0.007 * J);
	double t = std::sqrt(a * a + b_cam * b_cam);
	double Mp = std::log1p(0.0228 * t * Nc * Nbb) / 0.0228;
	double h = std::atan2(b_cam, a);

	return {
		Jp,
		Mp * std::cos(h),
		Mp * std::sin(h)
	};
}

// Конвертация sRGB → OKLab (алгоритм Björn Ottosson, 2020).
// Обеспечивает лучшую линейность оттенков по сравнению с CIE L*a*b*.
static inline OklabColor rgb_to_oklab(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
	double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
	double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

	double l_ = std::cbrt(l);
	double m_ = std::cbrt(m);
	double s_ = std::cbrt(s);

	return {
		0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
		1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
		0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
	};
}

// HSL: H∈[0,360), S∈[0,1], L∈[0,1]. Хранится в LabColor (L=H/360, a=S, b=L_hsl).
static inline LabColor rgb_to_hsl(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = r_raw / 255.0;
	double g = g_raw / 255.0;
	double b = b_raw / 255.0;

	double cmax = std::max(r, std::max(g, b));
	double cmin = std::min(r, std::min(g, b));
	double delta = cmax - cmin;
	double lightness = (cmax + cmin) * 0.5;

	double saturation = (delta < 1e-10)
		? 0.0
		: delta / (1.0 - std::abs(2.0 * lightness - 1.0));

	double hue = 0.0;

	if (delta > 1e-10) {
		if (cmax == r) {
			hue = 60.0 * std::fmod((g - b) / delta, 6.0);
		} else if (cmax == g) {
			hue = 60.0 * ((b - r) / delta + 2.0);
		} else {
			hue = 60.0 * ((r - g) / delta + 4.0);
		}

		if (hue < 0.0) {
			hue += 360.0;
		}
	}

	// Нормализуем H в [0,1] для равномерного масштаба при евклидовом расстоянии
	return {hue / 360.0, saturation, lightness};
}

// HSV: H∈[0,360), S∈[0,1], V∈[0,1]. Хранится в LabColor (L=H/360, a=S, b=V).
static inline LabColor rgb_to_hsv(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = r_raw / 255.0;
	double g = g_raw / 255.0;
	double b = b_raw / 255.0;

	double cmax = std::max(r, std::max(g, b));
	double cmin = std::min(r, std::min(g, b));
	double delta = cmax - cmin;

	double saturation = (cmax < 1e-10) ? 0.0 : delta / cmax;

	double hue = 0.0;

	if (delta > 1e-10) {
		if (cmax == r) {
			hue = 60.0 * std::fmod((g - b) / delta, 6.0);
		} else if (cmax == g) {
			hue = 60.0 * ((b - r) / delta + 2.0);
		} else {
			hue = 60.0 * ((r - g) / delta + 4.0);
		}

		if (hue < 0.0) {
			hue += 360.0;
		}
	}

	return {hue / 360.0, saturation, cmax};
}

// YUV (BT.601): Y∈[0,1], U∈[-0.436,0.436], V∈[-0.615,0.615].
static inline LabColor rgb_to_yuv(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = r_raw / 255.0;
	double g = g_raw / 255.0;
	double b = b_raw / 255.0;

	double Y = 0.299 * r + 0.587 * g + 0.114 * b;
	double U = -0.14713 * r - 0.28886 * g + 0.436 * b;
	double V = 0.615 * r - 0.51499 * g - 0.10001 * b;

	return {Y, U, V};
}

// YCbCr (BT.601, цифровой диапазон): Y∈[16,235], Cb/Cr∈[16,240] → нормализуем в [0,1].
static inline LabColor rgb_to_ycbcr(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = r_raw / 255.0;
	double g = g_raw / 255.0;
	double b = b_raw / 255.0;

	double Y = 0.257 * r + 0.504 * g + 0.098 * b + 0.0627;
	double Cb = -0.148 * r - 0.291 * g + 0.439 * b + 0.502;
	double Cr = 0.439 * r - 0.368 * g - 0.071 * b + 0.502;

	return {Y, Cb, Cr};
}

// IPT (Ebner & Fairchild, 1998): перцептивно равномерное пространство с хорошей
// предсказуемостью оттенков. I — яркость, P — красно-зелёная ось, T — жёлто-синяя.
static inline LabColor rgb_to_ipt(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	// sRGB linear → XYZ D65
	double x = r * 0.4124 + g * 0.3576 + b * 0.1804;
	double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
	double z = r * 0.0193 + g * 0.1192 + b * 0.9505;

	// XYZ D65 → LMS (матрица Hunt-Pointer-Estevez, адаптированная к D65)
	double lms_l = 0.4002 * x + 0.7076 * y - 0.0808 * z;
	double lms_m = -0.2263 * x + 1.1653 * y + 0.0457 * z;
	double lms_s = 0.9182 * z;

	// Нелинейная компрессия (степень 0.43)
	auto compress = [](double v) -> double {
		return std::copysign(std::pow(std::abs(v), 0.43), v);
	};

	double lp = compress(lms_l);
	double mp = compress(lms_m);
	double sp = compress(lms_s);

	// LMS' → IPT
	double I = 0.4000 * lp + 0.4000 * mp + 0.2000 * sp;
	double P = 4.4550 * lp - 4.8510 * mp + 0.3960 * sp;
	double T = 0.8056 * lp + 0.3572 * mp - 1.1628 * sp;

	return {I, P, T};
}

// JzAzBz (Safdar et al., 2017): перцептивно равномерное пространство с поддержкой
// HDR. Jz — яркость, Az/Bz — хроматические оси. Превосходит OKLab для HDR-контента.
static inline LabColor rgb_to_jzazbz(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw) {
	double r = srgb_to_linear(r_raw / 255.0);
	double g = srgb_to_linear(g_raw / 255.0);
	double b = srgb_to_linear(b_raw / 255.0);

	// Абсолютная яркость: sRGB → линейный свет (предполагаем пиковую яркость 203 нит)
	static constexpr double PEAK_NITS = 203.0;

	double ra = r * PEAK_NITS;
	double ga = g * PEAK_NITS;
	double ba = b * PEAK_NITS;

	// sRGB linear → XYZ D65 (абсолютные значения)
	double x = ra * 0.4124 + ga * 0.3576 + ba * 0.1804;
	double y = ra * 0.2126 + ga * 0.7152 + ba * 0.0722;
	double z = ra * 0.0193 + ga * 0.1192 + ba * 0.9505;

	// XYZ → LMS (матрица для JzAzBz)
	double lms_l = 0.41478972 * x + 0.57999902 * y + 0.01464800 * z;
	double lms_m = -0.20151000 * x + 1.12064900 * y + 0.05310080 * z;
	double lms_s = -0.01660080 * x + 0.26480900 * y + 0.66847990 * z;

	// Адаптация к абсолютной яркости (D65)
	double lp = lms_l + 1.6295499532821566e-11;
	double mp = lms_m + 1.6295499532821566e-11;
	double sp = lms_s + 1.6295499532821566e-11;

	// PQ (Perceptual Quantizer) EOTF — ST 2084
	static constexpr double c1 = 0.8359375;
	static constexpr double c2 = 18.8515625;
	static constexpr double c3 = 18.6875;
	static constexpr double n = 0.15930175664;
	static constexpr double p = 134.034375;

	auto pq = [&](double v) -> double {
		double vn = std::pow(v / 10000.0, n);
		return std::pow((c1 + c2 * vn) / (1.0 + c3 * vn), p);
	};

	double lhat = pq(lp);
	double mhat = pq(mp);
	double shat = pq(sp);

	// LMS_hat → Izazbz
	double Iz = 0.5 * lhat + 0.5 * mhat;
	double Az = 3.524000 * lhat - 4.066708 * mhat + 0.542708 * shat;
	double Bz = 0.199076 * lhat + 1.096799 * mhat - 1.295875 * shat;

	// Jz из Iz
	static constexpr double d = -0.56;
	static constexpr double d0 = 1.6295499532821566e-11;
	double Jz = (1.0 + d) * Iz / (1.0 + d * Iz) - d0;

	return {Jz, Az, Bz};
}

// ── Конвертация палитры ────────────────────────────────────────────────────

static std::vector<LabColor> convert_palette_to_lab(const int32_t* palette, int32_t size) {
	std::vector<LabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = rgb_to_lab(
			(color >> 16) & 0xFF,
			(color >> 8) & 0xFF,
			color & 0xFF
		);
	}

	return result;
}

static std::vector<RgbColor> convert_palette_to_rgb(const int32_t* palette, int32_t size) {
	std::vector<RgbColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = {
			static_cast<double>((color >> 16) & 0xFF),
			static_cast<double>((color >> 8) & 0xFF),
			static_cast<double>(color & 0xFF)
		};
	}

	return result;
}

static std::vector<OklabColor> convert_palette_to_oklab(const int32_t* palette, int32_t size) {
	std::vector<OklabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = rgb_to_oklab(
			(color >> 16) & 0xFF,
			(color >> 8) & 0xFF,
			color & 0xFF
		);
	}

	return result;
}

// ── Предвычисление палитры для CIEDE2000 ───────────────────────────────────

static std::vector<LabColorCiede> convert_palette_to_ciede(const std::vector<LabColor>& lab_palette) {
	int size = static_cast<int>(lab_palette.size());
	std::vector<LabColorCiede> result(size);

	double C_sum = 0.0;

	for (int i = 0; i < size; ++i) {
		C_sum += std::sqrt(lab_palette[i].a * lab_palette[i].a + lab_palette[i].b * lab_palette[i].b);
	}

	double C_avg = C_sum / size;
	double C_avg2 = C_avg * C_avg;
	double C_avg4 = C_avg2 * C_avg2;
	double C_avg7 = C_avg4 * C_avg2 * C_avg;
	double G = 0.5 * (1.0 - std::sqrt(C_avg7 / (C_avg7 + 6103515625.0)));

	for (int i = 0; i < size; ++i) {
		double ap = lab_palette[i].a * (1.0 + G);
		double b = lab_palette[i].b;
		double Cp = std::sqrt(ap * ap + b * b);
		double hp = std::atan2(b, ap) * (180.0 / M_PI);

		if (hp < 0.0) {
			hp += 360.0;
		}

		double Cp2 = Cp * Cp;
		double Cp4 = Cp2 * Cp2;
		double Cp7 = Cp4 * Cp2 * Cp;

		result[i] = {lab_palette[i].L, ap, b, Cp, hp, Cp7};
	}

	return result;
}

// ── Метрики цветового расстояния ───────────────────────────────────────────

static inline int find_nearest_lab(const LabColor& pixel, const std::vector<LabColor>& palette) {
	int best_idx = 0;
	double min_dist = 1e30;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dL = pixel.L - palette[p].L;
		double da = pixel.a - palette[p].a;
		double db = pixel.b - palette[p].b;
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

/**
 * Расстояние CIEDE2000 между пикселем и предвычисленным цветом палитры.
 * Данные палитры (ap, Cp, hp, Cp7) вычислены заранее в convert_palette_to_ciede().
 * Пиксель передаётся как LabColor — его ap/Cp/hp вычисляются здесь, но только один раз на вызов.
 */
static inline double ciede2000_fast(
	double pixel_L,
	double pixel_ap,
	double pixel_Cp,
	double pixel_hp,
	const LabColorCiede& pal
) {
	double dLp = pal.L - pixel_L;
	double dCp = pal.Cp - pixel_Cp;

	double dhp;

	if (pixel_Cp * pal.Cp == 0.0) {
		dhp = 0.0;
	} else if (std::abs(pal.hp - pixel_hp) <= 180.0) {
		dhp = pal.hp - pixel_hp;
	} else if (pal.hp - pixel_hp > 180.0) {
		dhp = pal.hp - pixel_hp - 360.0;
	} else {
		dhp = pal.hp - pixel_hp + 360.0;
	}

	double dHp = 2.0 * std::sqrt(pixel_Cp * pal.Cp) * std::sin(dhp * (M_PI / 360.0));

	double Lp_avg = (pixel_L + pal.L) * 0.5;
	double Cp_avg = (pixel_Cp + pal.Cp) * 0.5;

	double Hp_avg;

	if (pixel_Cp * pal.Cp == 0.0) {
		Hp_avg = pixel_hp + pal.hp;
	} else if (std::abs(pixel_hp - pal.hp) <= 180.0) {
		Hp_avg = (pixel_hp + pal.hp) * 0.5;
	} else if (pixel_hp + pal.hp < 360.0) {
		Hp_avg = (pixel_hp + pal.hp + 360.0) * 0.5;
	} else {
		Hp_avg = (pixel_hp + pal.hp - 360.0) * 0.5;
	}

	double T = 1.0
		- 0.17 * std::cos((Hp_avg - 30.0) * (M_PI / 180.0))
		+ 0.24 * std::cos(2.0 * Hp_avg * (M_PI / 180.0))
		+ 0.32 * std::cos((3.0 * Hp_avg + 6.0) * (M_PI / 180.0))
		- 0.20 * std::cos((4.0 * Hp_avg - 63.0) * (M_PI / 180.0));

	double Lp50sq = (Lp_avg - 50.0) * (Lp_avg - 50.0);
	double S_L = 1.0 + 0.015 * Lp50sq / std::sqrt(20.0 + Lp50sq);
	double S_C = 1.0 + 0.045 * Cp_avg;
	double S_H = 1.0 + 0.015 * Cp_avg * T;

	double Cp_avg2 = Cp_avg * Cp_avg;
	double Cp_avg4 = Cp_avg2 * Cp_avg2;
	double Cp_avg7 = Cp_avg4 * Cp_avg2 * Cp_avg;
	double R_C = 2.0 * std::sqrt(Cp_avg7 / (Cp_avg7 + 6103515625.0));
	double d_theta = 30.0 * std::exp(-((Hp_avg - 275.0) / 25.0) * ((Hp_avg - 275.0) / 25.0));
	double R_T = -std::sin(2.0 * d_theta * (M_PI / 180.0)) * R_C;

	double term_L = dLp / S_L;
	double term_C = dCp / S_C;
	double term_H = dHp / S_H;

	return term_L * term_L + term_C * term_C + term_H * term_H + R_T * term_C * term_H;
}

/**
 * Поиск ближайшего цвета в палитре по метрике CIEDE2000.
 * Использует предвычисленные данные палитры и LAB-расстояние как дешёвый фильтр
 * для раннего пропуска заведомо далёких цветов.
 */
static inline int find_nearest_ciede2000(
	const LabColor& pixel,
	const std::vector<LabColor>& lab_palette,
	const std::vector<LabColorCiede>& ciede_palette
) {
	double C_pixel = std::sqrt(pixel.a * pixel.a + pixel.b * pixel.b);
	double min_dist = 1e30;
	int best_idx = 0;

	for (int p = 0; p < static_cast<int>(ciede_palette.size()); ++p) {
		// Дешёвый LAB-фильтр: пропускаем заведомо далёкие цвета без вычисления CIEDE2000
		double dL = pixel.L - lab_palette[p].L;
		double da = pixel.a - lab_palette[p].a;
		double db = pixel.b - lab_palette[p].b;
		double lab_dist = dL * dL + da * da + db * db;

		if (lab_dist > min_dist * 16.0) {
			continue;
		}

		double C_avg = (C_pixel + ciede_palette[p].Cp) * 0.5;
		double Ca2 = C_avg * C_avg;
		double Ca4 = Ca2 * Ca2;
		double Ca7 = Ca4 * Ca2 * C_avg;
		double G = 0.5 * (1.0 - std::sqrt(Ca7 / (Ca7 + 6103515625.0)));
		double pixel_ap = pixel.a * (1.0 + G);
		double pixel_Cp = std::sqrt(pixel_ap * pixel_ap + pixel.b * pixel.b);
		double pixel_hp = std::atan2(pixel.b, pixel_ap) * (180.0 / M_PI);

		if (pixel_hp < 0.0) {
			pixel_hp += 360.0;
		}

		double dist = ciede2000_fast(pixel.L, pixel_ap, pixel_Cp, pixel_hp, ciede_palette[p]);

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

static inline int find_nearest_rgb(const RgbColor& pixel, const std::vector<RgbColor>& palette) {
	int best_idx = 0;
	double min_dist = 1e30;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dr = pixel.r - palette[p].r;
		double dg = pixel.g - palette[p].g;
		double db = pixel.b - palette[p].b;
		double dist = dr * dr + dg * dg + db * db;

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

static inline int find_nearest_oklab(const OklabColor& pixel, const std::vector<OklabColor>& palette) {
	int best_idx = 0;
	double min_dist = 1e30;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dL = pixel.L - palette[p].L;
		double da = pixel.a - palette[p].a;
		double db = pixel.b - palette[p].b;
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

// OKLab с усиленным весом хроматических компонент (a, b) относительно яркости (L).
// Улучшает различимость насыщенных цветов за счёт снижения влияния яркости.
static inline int find_nearest_oklab_chroma(const OklabColor& pixel, const std::vector<OklabColor>& palette) {
	static constexpr double CHROMA_WEIGHT = 2.0;

	int best_idx = 0;
	double min_dist = 1e30;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dL = pixel.L - palette[p].L;
		double da = pixel.a - palette[p].a;
		double db = pixel.b - palette[p].b;
		double dist = dL * dL + CHROMA_WEIGHT * (da * da + db * db);

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

// Взвешенное RGB-расстояние с учётом чувствительности глаза к каналам.
// Формула: (2 + r/256)*dr² + 4*dg² + (2 + (255-r)/256)*db²
// Быстрее LAB, точнее обычного RGB для большинства цветов.
static inline int find_nearest_weighted_rgb(const RgbColor& pixel, const std::vector<RgbColor>& palette) {
	int best_idx = 0;
	double min_dist = 1e30;
	double r_norm = pixel.r / 256.0;

	for (int p = 0; p < static_cast<int>(palette.size()); ++p) {
		double dr = pixel.r - palette[p].r;
		double dg = pixel.g - palette[p].g;
		double db = pixel.b - palette[p].b;
		double dist = (2.0 + r_norm) * dr * dr
			+ 4.0 * dg * dg
			+ (3.0 - r_norm) * db * db;

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

// ── Конвертация палитры для D50 и HCT ─────────────────────────────────────

static std::vector<LabColor> convert_palette_to_lab_d50(const int32_t* palette, int32_t size) {
	std::vector<LabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = rgb_to_lab_d50(
			(color >> 16) & 0xFF,
			(color >> 8) & 0xFF,
			color & 0xFF
		);
	}

	return result;
}

static std::vector<LabColor> convert_palette_to_hct(const int32_t* palette, int32_t size) {
	std::vector<LabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = rgb_to_hct(
			(color >> 16) & 0xFF,
			(color >> 8) & 0xFF,
			color & 0xFF
		);
	}

	return result;
}

// ── Конвертация палитры для новых метрик ──────────────────────────────────

static std::vector<LabColor> convert_palette_generic(
	const int32_t* palette,
	int32_t size,
	LabColor (*converter)(uint8_t, uint8_t, uint8_t)
) {
	std::vector<LabColor> result(size);

	for (int i = 0; i < size; ++i) {
		int32_t color = palette[i];
		result[i] = converter(
			(color >> 16) & 0xFF,
			(color >> 8) & 0xFF,
			color & 0xFF
		);
	}

	return result;
}

// ── Унифицированный буфер пикселей ─────────────────────────────────────────

// Хранит пиксели в нужном цветовом пространстве в зависимости от метрики.
struct PixelBuffers {
	std::vector<LabColor> lab_buf;
	std::vector<LabColor> lab_d50_buf;
	std::vector<LabColor> hct_buf;
	std::vector<LabColor> hsl_buf;
	std::vector<LabColor> hsv_buf;
	std::vector<LabColor> yuv_buf;
	std::vector<LabColor> ycbcr_buf;
	std::vector<LabColor> ipt_buf;
	std::vector<LabColor> jzazbz_buf;
	std::vector<RgbColor> rgb_buf;
	std::vector<OklabColor> oklab_buf;
	std::vector<LabColorCiede> ciede_palette;
	std::vector<LabColorCiede> ciede_d50_palette;
};

static PixelBuffers build_pixel_buffers(
	const uint8_t* image,
	int w,
	int h,
	ColorMetric metric,
	const std::vector<LabColor>& lab_palette,
	const std::vector<LabColor>& lab_d50_palette,
	const std::vector<LabColor>& hct_palette
) {
	PixelBuffers result;
	int total = w * h;

	if (metric == METRIC_RGB || metric == METRIC_WEIGHTED_RGB) {
		result.rgb_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			// Порядок байт в BufferedImage.TYPE_3BYTE_BGR: B, G, R
			result.rgb_buf[i] = {
				static_cast<double>(image[i * 3 + 2]),
				static_cast<double>(image[i * 3 + 1]),
				static_cast<double>(image[i * 3])
			};
		}
	} else if (metric == METRIC_OKLAB || metric == METRIC_OKLAB_CHROMA) {
		result.oklab_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.oklab_buf[i] = rgb_to_oklab(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_LAB_D50 || metric == METRIC_CIEDE2000_D50) {
		result.lab_d50_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.lab_d50_buf[i] = rgb_to_lab_d50(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}

		if (metric == METRIC_CIEDE2000_D50) {
			result.ciede_d50_palette = convert_palette_to_ciede(lab_d50_palette);
		}
	} else if (metric == METRIC_HCT) {
		result.hct_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.hct_buf[i] = rgb_to_hct(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_HSL) {
		result.hsl_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.hsl_buf[i] = rgb_to_hsl(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_HSV) {
		result.hsv_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.hsv_buf[i] = rgb_to_hsv(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_YUV) {
		result.yuv_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.yuv_buf[i] = rgb_to_yuv(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_YCBCR) {
		result.ycbcr_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.ycbcr_buf[i] = rgb_to_ycbcr(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_IPT) {
		result.ipt_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.ipt_buf[i] = rgb_to_ipt(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else if (metric == METRIC_JZAZBZ) {
		result.jzazbz_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.jzazbz_buf[i] = rgb_to_jzazbz(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}
	} else {
		result.lab_buf.resize(total);

		for (int i = 0; i < total; ++i) {
			result.lab_buf[i] = rgb_to_lab(image[i * 3 + 2], image[i * 3 + 1], image[i * 3]);
		}

		if (metric == METRIC_CIEDE2000) {
			result.ciede_palette = convert_palette_to_ciede(lab_palette);
		}
	}

	return result;
}

// ── Поиск ближайшего цвета (диспетчер по метрике) ─────────────────────────

static inline int find_nearest(
	int idx,
	const PixelBuffers& bufs,
	const std::vector<LabColor>& lab_palette,
	const std::vector<LabColor>& lab_d50_palette,
	const std::vector<LabColor>& hct_palette,
	const std::vector<RgbColor>& rgb_palette,
	const std::vector<OklabColor>& oklab_palette,
	const std::vector<LabColor>& hsl_palette,
	const std::vector<LabColor>& hsv_palette,
	const std::vector<LabColor>& yuv_palette,
	const std::vector<LabColor>& ycbcr_palette,
	const std::vector<LabColor>& ipt_palette,
	const std::vector<LabColor>& jzazbz_palette,
	ColorMetric metric
) {
	switch (metric) {
		case METRIC_CIEDE2000:
			return find_nearest_ciede2000(bufs.lab_buf[idx], lab_palette, bufs.ciede_palette);
		case METRIC_RGB:
			return find_nearest_rgb(bufs.rgb_buf[idx], rgb_palette);
		case METRIC_OKLAB:
			return find_nearest_oklab(bufs.oklab_buf[idx], oklab_palette);
		case METRIC_OKLAB_CHROMA:
			return find_nearest_oklab_chroma(bufs.oklab_buf[idx], oklab_palette);
		case METRIC_WEIGHTED_RGB:
			return find_nearest_weighted_rgb(bufs.rgb_buf[idx], rgb_palette);
		case METRIC_LAB_D50:
			return find_nearest_lab(bufs.lab_d50_buf[idx], lab_d50_palette);
		case METRIC_CIEDE2000_D50:
			return find_nearest_ciede2000(bufs.lab_d50_buf[idx], lab_d50_palette, bufs.ciede_d50_palette);
		case METRIC_HCT:
			return find_nearest_lab(bufs.hct_buf[idx], hct_palette);
		case METRIC_HSL:
			return find_nearest_lab(bufs.hsl_buf[idx], hsl_palette);
		case METRIC_HSV:
			return find_nearest_lab(bufs.hsv_buf[idx], hsv_palette);
		case METRIC_YUV:
			return find_nearest_lab(bufs.yuv_buf[idx], yuv_palette);
		case METRIC_YCBCR:
			return find_nearest_lab(bufs.ycbcr_buf[idx], ycbcr_palette);
		case METRIC_IPT:
			return find_nearest_lab(bufs.ipt_buf[idx], ipt_palette);
		case METRIC_JZAZBZ:
			return find_nearest_lab(bufs.jzazbz_buf[idx], jzazbz_palette);
		default:
			return find_nearest_lab(bufs.lab_buf[idx], lab_palette);
	}
}
