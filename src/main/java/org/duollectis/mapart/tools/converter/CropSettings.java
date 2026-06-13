package org.duollectis.mapart.tools.converter;

import java.awt.Color;

/**
 * Настройки подгонки исходного изображения под целевой размер карт.
 * <p>
 * Поддерживает независимое масштабирование по X и Y — необходимо для корректного
 * отображения деформированных изображений (когда пользователь resize'ом изменил
 * пропорции картинки в левой панели).
 *
 * @param offsetX смещение по X в пикселях целевого изображения
 * @param offsetY смещение по Y в пикселях целевого изображения
 * @param scaleX  пользовательский зум по X (1.0 = без зума, >1.0 = увеличение)
 * @param scaleY  пользовательский зум по Y (1.0 = без зума, >1.0 = увеличение)
 * @param bgColor цвет фона холста (заполняет область вне картинки)
 */
public record CropSettings(int offsetX, int offsetY, double scaleX, double scaleY, Color bgColor) {

	public static CropSettings defaultFit() {
		return new CropSettings(0, 0, 1.0, 1.0, Color.BLACK);
	}

	public static CropSettings fit(int offsetX, int offsetY, double scaleX, double scaleY, Color bgColor) {
		return new CropSettings(offsetX, offsetY, scaleX, scaleY, bgColor);
	}
}
