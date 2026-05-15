#include <vector>
#include <cmath>
#include <random>
#include <algorithm>

enum DitherAlgorithm {
    FLOYD_STEINBERG = 0,
    STUCKI = 1,
    JJN = 2,
    BURKES = 3,
    SIERRA3 = 4,
    SIERRA_LITE = 5,
    ATKINSON = 6
};

struct ErrorKernel {
    int dx, dy;
    double weight;
};

// Вспомогательная функция для вычисления квадрата евклидова расстояния в Lab
inline double get_dist_sq(double L1, double a1, double b1, double L2, double a2, double b2) {
    double dL = L1 - L2, da = a1 - a2, db = b1 - b2;
    return dL * dL + da * da + db * db;
}

inline void rgb_to_lab(uint8_t r_raw, uint8_t g_raw, uint8_t b_raw, double* lab) {
    // 1. sRGB to Linear
    auto pivot = [](double c) {
        return (c > 0.04045) ? pow((c + 0.055) / 1.055, 2.4) : (c / 12.92);
    };
    double r = pivot(r_raw / 255.0);
    double g = pivot(g_raw / 255.0);
    double b = pivot(b_raw / 255.0);

    // 2. Linear to XYZ (D65)
    double x = (r * 0.4124 + g * 0.3576 + b * 0.1804) * 100.0;
    double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0;
    double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) * 100.0;

    // 3. XYZ to Lab
    auto f = [](double t) {
        return (t > 0.008856) ? pow(t, 1.0 / 3.0) : (7.787 * t + 16.0 / 116.0);
    };
    double fx = f(x / 95.047);
    double fy = f(y / 100.000);
    double fz = f(z / 108.883);

    lab[0] = 116.0 * fy - 16.0; // L
    lab[1] = 500.0 * (fx - fy); // a
    lab[2] = 200.0 * (fy - fz); // b
}

inline std::vector<double> convert_palette_to_lab(int32_t* int_pal, int32_t psize) {
    std::vector<double> lab_pal(psize * 3);

    for (int i = 0; i < psize; ++i) {
        int32_t color = int_pal[i];
        uint8_t r = (color >> 16) & 0xFF;
        uint8_t g = (color >> 8) & 0xFF;
        uint8_t b = (color) & 0xFF;
        rgb_to_lab(r, g, b, &lab_pal[i * 3]);
    }

    return lab_pal; // Вектор переместится (move) без лишнего копирования
}

inline std::vector<ErrorKernel> get_kernel(uint32_t algorithm) {
    switch (algorithm) {
        case FLOYD_STEINBERG:
            return {
                {1, 0, 7 / 16.0},
                {-1, 1, 3 / 16.0},
                {0, 1, 5 / 16.0},
                {1, 1, 1 / 16.0}
            };

        case STUCKI:
            return {
                {1, 0, 8 / 42.0},
                {2, 0, 4 / 42.0},
                {-2, 1, 2 / 42.0},
                {-1, 1, 4 / 42.0},
                {0, 1, 8 / 42.0},
                {1, 1, 4 / 42.0},
                {2, 1, 2 / 42.0},
                {-2, 2, 1 / 42.0},
                {-1, 2, 2 / 42.0},
                {0, 2, 4 / 42.0},
                {1, 2, 2 / 42.0},
                {2, 2, 1 / 42.0}
            };

        case JJN:
            return {
                {1, 0, 7 / 48.0},
                {2, 0, 5 / 48.0},
                {-2, 1, 3 / 48.0},
                {-1, 1, 5 / 48.0},
                {0, 1, 7 / 48.0},
                {1, 1, 5 / 48.0},
                {2, 1, 3 / 48.0},
                {-2, 2, 1 / 48.0},
                {-1, 2, 3 / 48.0},
                {0, 2, 5 / 48.0},
                {1, 2, 3 / 48.0},
                {2, 2, 1 / 48.0}
            };

        case BURKES:
            return {
                {1, 0, 8 / 32.0},
                {2, 0, 4 / 32.0},
                {-2, 1, 2 / 32.0},
                {-1, 1, 4 / 32.0},
                {0, 1, 8 / 32.0},
                {1, 1, 4 / 32.0},
                {2, 1, 2 / 32.0}
            };
        case SIERRA3:
            return {
                {1, 0, 5 / 32.0},
                {2, 0, 3 / 32.0},
                {-2, 1, 2 / 32.0},
                {-1, 1, 4 / 32.0},
                {0, 1, 5 / 32.0},
                {1, 1, 4 / 32.0},
                {2, 1, 2 / 32.0},
                {-1, 2, 2 / 32.0},
                {0, 2, 3 / 32.0},
                {1, 2, 2 / 32.0}
            };
        case SIERRA_LITE:
            return {
                {1, 0, 2 / 4.0},
                {-1, 1, 1 / 4.0},
                {0, 1, 1 / 4.0}
            };
        case ATKINSON:
            return {
                {1, 0, 1 / 8.0},
                {2, 0, 1 / 8.0},
                {-1, 1, 1 / 8.0},
                {0, 1, 1 / 8.0},
                {1, 1, 1 / 8.0},
                {0, 2, 1 / 8.0}
            };
    }

    return get_kernel(FLOYD_STEINBERG);
}

extern "C" void dither(
    int32_t* palette,
    int32_t psize,
    uint8_t* image,
    int32_t w,
    int32_t h,
    int32_t* dithered,
    int32_t algorithm,
    double err_diff_rate,
    double noise_level
) {
    // 1. Палитра (palette[i] — это int 0xRRGGBB)
    auto lab_palette = convert_palette_to_lab(palette, psize);

    // 2. Изображение (raw_pixels — это байты BGR из BufferedImage)
    std::vector<double> lab_buf(w * h * 3);
    for (int i = 0; i < w * h; ++i) {
        // Порядок BGR -> RGB для конвертера
        rgb_to_lab(image[i * 3 + 2], image[i * 3 + 1], image[i * 3], &lab_buf[i * 3]);
    }

    // 3. Ядра (явно пишем .0, чтобы избежать целочисленного деления)
    std::vector<ErrorKernel> kernel = get_kernel(algorithm);

    std::mt19937 gen(42);
    std::uniform_real_distribution<double> dist(-noise_level, noise_level);

    for (int y = 0; y < h; ++y) {
        bool rev = (y % 2 != 0);

        // Определяем начало, конец и шаг для x
        int startX = rev ? (w - 1) : 0;
        int endX = rev ? -1 : w;
        int stepX = rev ? -1 : 1;

        for (int x = startX; x != endX; x += stepX) {
            int idx = (y * w + x) * 3;

            // Добавляем шум ко всем каналам для естественности
            double L = lab_buf[idx] + dist(gen);
            double a = lab_buf[idx + 1] + dist(gen);
            double b = lab_buf[idx + 2] + dist(gen);

            // Поиск ближайшего
            int best_p = 0;
            double min_d = 1e30;
            for (int p = 0; p < psize; ++p) {
                double pL = lab_palette[p * 3], pa = lab_palette[p * 3 + 1], pb = lab_palette[p * 3 + 2];
                double d = (L - pL) * (L - pL) + (a - pa) * (a - pa) + (b - pb) * (b - pb);
                if (d < min_d) {
                    min_d = d;
                    best_p = p;
                }
            }

            dithered[y * w + x] = best_p;

            // Ошибка (с учетом затухания 0.99, чтобы не было "перегруза")
            double eL = (L - lab_palette[best_p * 3]) * err_diff_rate;
            double ea = (a - lab_palette[best_p * 3 + 1]) * err_diff_rate;
            double eb = (b - lab_palette[best_p * 3 + 2]) * err_diff_rate;

            // 3. РАЗНОС ОШИБКИ
            // Важно: stepX здесь автоматически меняет направление матрицы!
            for (auto &k: kernel) {
                int nx = x + k.dx * stepX; // Сдвиг по горизонтали зависит от направления
                int ny = y + k.dy; // Сдвиг по вертикали всегда вниз

                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int n_idx = (ny * w + nx) * 3;
                    lab_buf[n_idx] += eL * k.weight;
                    lab_buf[n_idx + 1] += ea * k.weight;
                    lab_buf[n_idx + 2] += eb * k.weight;
                }
            }
        }
    }
}
