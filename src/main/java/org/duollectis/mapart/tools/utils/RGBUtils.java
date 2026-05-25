package org.duollectis.mapart.tools.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RGBUtils {

	public int scaleRGB(int color, int factor) {
		return color(
			Math.clamp((long) red(color) * factor / 255L, 0, 255),
			Math.clamp((long) green(color) * factor / 255L, 0, 255),
			Math.clamp((long) blue(color) * factor / 255L, 0, 255)
		);
	}

	public int color(long r, long g, long b) {
		return ((int) r & 0xFF) << 16 | ((int) g & 0xFF) << 8 | (int) b & 0xFF;
	}

	public int red(int color) {
		return (color >> 16) & 0xFF;
	}

	public int green(int color) {
		return (color >> 8) & 0xFF;
	}

	public int blue(int color) {
		return color & 0xFF;
	}
}
