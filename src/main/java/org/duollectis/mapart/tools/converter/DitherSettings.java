package org.duollectis.mapart.tools.converter;

/**
 * Иммутабельные настройки алгоритма дизеринга.
 * <p>
 * {@code errRateR/G/B} — независимые коэффициенты распространения ошибки по каналам
 * R, G, B (1.0 = 100%, диапазон 0.0–2.0). Для не-RGB метрик применяется среднее трёх значений.
 * {@code noiseLevel} применяется для всех алгоритмов, кроме NONE.
 * {@code colorMetric} определяет метрику расстояния при поиске ближайшего цвета в палитре.
 *
 * @param errRateR    коэффициент канала R, от 0.0 до 2.0 (1.0 = 100%)
 * @param errRateG    коэффициент канала G, от 0.0 до 2.0 (1.0 = 100%)
 * @param errRateB    коэффициент канала B, от 0.0 до 2.0 (1.0 = 100%)
 * @param noiseLevel  уровень случайного шума, от 0.0 до 1.0
 * @param colorMetric метрика цветового расстояния
 */
public record DitherSettings(
	double errRateR,
	double errRateG,
	double errRateB,
	double noiseLevel,
	ColorMetric colorMetric
) {

	private static final double DEFAULT_ERR_RATE_CHANNEL = 0.8;
	private static final double DEFAULT_NOISE_LEVEL = 0.0;
	private static final ColorMetric DEFAULT_COLOR_METRIC = ColorMetric.LAB;

	public static DitherSettings defaults() {
		return new DitherSettings(
			DEFAULT_ERR_RATE_CHANNEL,
			DEFAULT_ERR_RATE_CHANNEL,
			DEFAULT_ERR_RATE_CHANNEL,
			DEFAULT_NOISE_LEVEL,
			DEFAULT_COLOR_METRIC
		);
	}
}
