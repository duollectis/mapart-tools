package org.duollectis.mapart.tools.utils;

public class RGBUtils {

	public static int scaleRGB(int color, int value) {
		return color(
				Math.clamp((long) red(color) * value / 255L, 0, 255),
				Math.clamp((long) green(color) * value / 255L, 0, 255),
				Math.clamp((long) blue(color) * value / 255L, 0, 255)
		);
	}

	public static int color(int r, int g, int b) {
		return (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
	}

	public static int red(int color) {
		return (color >> 16) & 0xFF;
	}

	public static int green(int color) {
		return (color >> 8) & 0xFF;
	}

	public static int blue(int color) {
		return (color) & 0xFF;
	}

}
