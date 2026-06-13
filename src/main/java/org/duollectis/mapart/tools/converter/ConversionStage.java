package org.duollectis.mapart.tools.converter;

/**
 * Несёт текущий этап конвертации и числовой прогресс (0–100).
 * Публикуется из {@link ImageConverter#dither} на каждом значимом шаге.
 */
public record ConversionStage(Phase phase, int percent) {

	public enum Phase {
		LOADING_PALETTE,
		PREPARING_IMAGE,
		DITHERING
	}
}
