package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.duollectis.mapart.tools.nativee.NativeBridge;
import org.duollectis.mapart.tools.nativee.NativeMethod;
import org.duollectis.mapart.tools.nativee.NativeWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.List;

public class Ditherer implements AutoCloseable {

	private final ImageDithererWrapper ditherer;

	@Setter
	private double errorDiffusionRate = 1;

	@Setter
	private double noiseLevel = 0;

	@Setter
	@NotNull
	@NonNull
	private List<PaletteEntry> palette = List.of();

	@Setter
	@NotNull
	@NonNull
	private Algorithm algorithm = Algorithm.FLOYD_STEINBERG;

	@Getter
	private int ditherTime;

	@Getter
	private int[][] dithered = new int[0][0];

	public Ditherer(File lib) {
		this.ditherer = NativeBridge.create(ImageDithererWrapper.class, lib);
	}

	public void processImage(BufferedImage img) {
		dithered = new int[img.getHeight()][img.getWidth()];

		if (palette.isEmpty()) {
			return;
		}

		byte[] pixels = getPixels(img);

		long time = System.currentTimeMillis();

		int[] _palette = new int[palette.size()];
		for (int i = 0; i < _palette.length; i++) {
			_palette[i] = palette.get(i).getRgb();
		}

		ditherer.dither(
				_palette,
				_palette.length,
				pixels,
				dithered[0].length,
				dithered.length,
				dithered,
				algorithm.getId(),
				errorDiffusionRate,
				noiseLevel
		);

		ditherTime = Math.toIntExact(System.currentTimeMillis() - time);
	}

	public BufferedImage createPreview() {
		if (dithered == null) {
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		}

		int width = dithered[0].length;
		int height = dithered.length;

		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				out.setRGB(x, y, palette.get(dithered[y][x]).getRgb());
			}
		}

		return out;
	}

	@Override
	public void close() {
		ditherer.close();
	}

	public static byte[] getPixels(BufferedImage image) {
		BufferedImage bgrImage = new BufferedImage(
				image.getWidth(),
				image.getHeight(),
				BufferedImage.TYPE_3BYTE_BGR
		);

		Graphics2D graphics = bgrImage.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();

		return ((DataBufferByte) bgrImage.getRaster().getDataBuffer()).getData();
	}

	@Getter
	public enum Algorithm {
		FLOYD_STEINBERG(0),
		STUCKI(1),
		JJN(2),
		BURKES(3),
		SIERRA3(4),
		SIERRA_LITE(5),
		ATKINSON(6);

		private final int id;

		Algorithm(int id) {
			this.id = id;
		}
	}

	private interface ImageDithererWrapper extends NativeWrapper {

		@NativeMethod
		void dither(
				int[] palette,
				int paletteSize,
				byte[] pixels,
				int width,
				int height,
				int[][] dithered,
				int algorithm,
				double errorDiffusionRate,
				double noiseLevel
		);
	}
}
