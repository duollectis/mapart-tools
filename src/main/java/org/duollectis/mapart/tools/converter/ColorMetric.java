package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.duollectis.mapart.tools.gui.HasDescription;
import org.duollectis.mapart.tools.gui.Lang;

/**
 * Метрика цветового расстояния, используемая при поиске ближайшего цвета в палитре.
 * <p>
 * Идентификаторы {@code id} должны строго соответствовать константам в C++ коде
 * ({@code ColorMetric} enum в {@code dither_types.h}).
 */
@Getter
@RequiredArgsConstructor
public enum ColorMetric implements HasDescription {

	LAB(0),
	CIEDE2000(1),
	RGB(2),
	OKLAB(3),
	WEIGHTED_RGB(4),

	/** CIE76 с белой точкой D50 — стандарт печати, точнее для тёплых тонов. */
	LAB_D50(5),

	/** CIEDE2000 с белой точкой D50 — максимальная точность для полиграфии. */
	CIEDE2000_D50(6),

	/**
	 * HCT (Hue, Chroma, Tone) — цветовое пространство Material Design 3.
	 * Основано на CAM16 + L*, обеспечивает наилучшее соответствие восприятию
	 * при работе с насыщенными цветами.
	 */
	HCT(7),

	/** OKLab с усиленным весом хроматических компонент — лучше различает насыщенные цвета. */
	OKLAB_CHROMA(8),

	/** HSL (Hue, Saturation, Lightness) — цилиндрическое представление RGB. */
	HSL(9),

	/** HSV (Hue, Saturation, Value) — цилиндрическое представление RGB, ориентированное на яркость. */
	HSV(10),

	/** YUV (BT.601) — разделение яркости и цветности, стандарт аналогового видео. */
	YUV(11),

	/** YCbCr (BT.601) — цифровой стандарт видео, используется в JPEG/MPEG. */
	YCBCR(12),

	/**
	 * IPT (Ebner & Fairchild, 1998) — перцептивно равномерное пространство
	 * с хорошей предсказуемостью оттенков.
	 */
	IPT(13),

	/**
	 * JzAzBz (Safdar et al., 2017) — перцептивно равномерное пространство
	 * с поддержкой HDR, превосходит OKLab для широкого диапазона яркости.
	 */
	JZAZBZ(14);

	private final int id;

	@Override
	public String toString() {
		try {
			return Lang.t("metric." + name());
		} catch (Exception ignored) {
			return name();
		}
	}

	@Override
	public String getDescription() {
		try {
			return Lang.t("metric." + name() + ".desc");
		} catch (Exception ignored) {
			return null;
		}
	}
}
