package org.duollectis.mapart.tools.converter;

/**
 * Иммутабельные настройки алгоритма дизеринга.
 * <p>
 * {@code errorDiffusionRate} применяется только для алгоритмов диффузии ошибки
 * (Floyd-Steinberg, Stucki, JJN и т.д.) и игнорируется для Байера и NONE.
 * {@code noiseLevel} применяется для всех алгоритмов, кроме NONE.
 *
 * @param errorDiffusionRate сила распространения ошибки, от 0.0 до 1.0
 * @param noiseLevel         уровень случайного шума, от 0.0 до 1.0
 */
public record DitherSettings(double errorDiffusionRate, double noiseLevel) {

	private static final double DEFAULT_ERROR_DIFFUSION_RATE = 0.8;
	private static final double DEFAULT_NOISE_LEVEL = 0.0;

	public static DitherSettings defaults() {
		return new DitherSettings(DEFAULT_ERROR_DIFFUSION_RATE, DEFAULT_NOISE_LEVEL);
	}
}
