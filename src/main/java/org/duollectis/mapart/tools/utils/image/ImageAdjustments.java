package org.duollectis.mapart.tools.utils.image;

/**
 * Иммутабельный набор параметров коррекции изображения перед дизерингом.
 * Все значения нейтральны (не изменяют изображение) при дефолтных настройках.
 *
 * @param brightness яркость: [-100, +100], 0 = без изменений
 * @param contrast   контраст: [-100, +100], 0 = без изменений
 * @param saturation насыщенность: [-100, +100], 0 = без изменений
 * @param gamma      гамма: [10, 300], 100 = без изменений (линейная)
 * @param hue        сдвиг оттенка: [-180, +180] градусов, 0 = без изменений
 */
public record ImageAdjustments(
	int brightness,
	int contrast,
	int saturation,
	int gamma,
	int hue
) {

	public static ImageAdjustments defaults() {
		return new ImageAdjustments(0, 0, 0, 100, 0);
	}

	public boolean isNeutral() {
		return brightness == 0
			&& contrast == 0
			&& saturation == 0
			&& gamma == 100
			&& hue == 0;
	}
}
