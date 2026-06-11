package org.duollectis.mapart.tools.utils.image;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUtilsTest {

	private static BufferedImage solidImage(int width, int height, int rgb) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				img.setRGB(x, y, rgb);
			}
		}

		return img;
	}

	// ─── resizeImage ─────────────────────────────────────────────────────────────

	@Test
	void resizeImage_returnsImageOfTargetSize() {
		BufferedImage source = solidImage(100, 100, 0xFF0000);

		BufferedImage result = ImageUtils.resizeImage(source, 50, 75);

		assertThat(result.getWidth()).isEqualTo(50);
		assertThat(result.getHeight()).isEqualTo(75);
	}

	@Test
	void resizeImage_upscale_returnsCorrectSize() {
		BufferedImage source = solidImage(10, 10, 0x00FF00);

		BufferedImage result = ImageUtils.resizeImage(source, 200, 200);

		assertThat(result.getWidth()).isEqualTo(200);
		assertThat(result.getHeight()).isEqualTo(200);
	}

	@Test
	void resizeImage_returnsArgbType() {
		BufferedImage source = solidImage(50, 50, 0x0000FF);

		BufferedImage result = ImageUtils.resizeImage(source, 25, 25);

		assertThat(result.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
	}

	// ─── fitImage ────────────────────────────────────────────────────────────────

	@Test
	void fitImage_returnsCanvasOfTargetSize() {
		BufferedImage source = solidImage(100, 100, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 0, 0, 1.0, 1.0);

		assertThat(result.image().getWidth()).isEqualTo(128);
		assertThat(result.image().getHeight()).isEqualTo(128);
	}

	@Test
	void fitImage_squareImage_clipRectFillsEntireCanvas() {
		BufferedImage source = solidImage(100, 100, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 0, 0, 1.0, 1.0);

		assertThat(result.clipX()).isEqualTo(0);
		assertThat(result.clipY()).isEqualTo(0);
		assertThat(result.clipW()).isEqualTo(128);
		assertThat(result.clipH()).isEqualTo(128);
	}

	@Test
	void fitImage_wideImage_clipRectHeightIsSmaller() {
		// 200x100 fits into 128x128 — width=128, height=64
		BufferedImage source = solidImage(200, 100, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 0, 0, 1.0, 1.0);

		assertThat(result.clipW()).isEqualTo(128);
		assertThat(result.clipH()).isEqualTo(64);
	}

	@Test
	void fitImage_tallImage_clipRectWidthIsSmaller() {
		// 100x200 fits into 128x128 — height=128, width=64
		BufferedImage source = solidImage(100, 200, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 0, 0, 1.0, 1.0);

		assertThat(result.clipW()).isEqualTo(64);
		assertThat(result.clipH()).isEqualTo(128);
	}

	@Test
	void fitImage_imageOutOfBounds_clipRectIsZero() {
		BufferedImage source = solidImage(100, 100, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 1000, 1000, 1.0, 1.0);

		assertThat(result.clipW()).isEqualTo(0);
		assertThat(result.clipH()).isEqualTo(0);
	}

	@Test
	void fitImage_scaleX2_clipRectWidthIs128() {
		// Square image with userScaleX=2 — width doubles but is clipped to canvas
		BufferedImage source = solidImage(64, 64, 0xFF0000);

		FitResult result = ImageUtils.fitImage(source, 128, 128, 0, 0, 2.0, 1.0);

		// Scaled width = 256, but canvas is 128 — clipW = 128
		assertThat(result.clipW()).isEqualTo(128);
	}

	// ─── applyAdjustments ────────────────────────────────────────────────────────

	@Test
	void applyAdjustments_neutral_returnsSameInstance() {
		BufferedImage source = solidImage(10, 10, 0xFF8040);
		ImageAdjustments neutral = ImageAdjustments.defaults();

		BufferedImage result = ImageUtils.applyAdjustments(source, neutral);

		assertThat(result).isSameAs(source);
	}

	@Test
	void applyAdjustments_maxBrightness_brightensImage() {
		BufferedImage source = solidImage(4, 4, 0x404040);
		ImageAdjustments adj = new ImageAdjustments(100, 0, 0, 100, 0);

		BufferedImage result = ImageUtils.applyAdjustments(source, adj);

		int pixel = result.getRGB(2, 2) & 0xFFFFFF;
		int r = (pixel >> 16) & 0xFF;

		assertThat(r).isGreaterThan(0x40);
	}

	@Test
	void applyAdjustments_minBrightness_darkensImage() {
		BufferedImage source = solidImage(4, 4, 0xC0C0C0);
		ImageAdjustments adj = new ImageAdjustments(-100, 0, 0, 100, 0);

		BufferedImage result = ImageUtils.applyAdjustments(source, adj);

		int pixel = result.getRGB(2, 2) & 0xFFFFFF;
		int r = (pixel >> 16) & 0xFF;

		assertThat(r).isLessThan(0xC0);
	}

	@Test
	void applyAdjustments_returnsImageOfSameSize() {
		BufferedImage source = solidImage(30, 20, 0x123456);
		ImageAdjustments adj = new ImageAdjustments(10, 0, 0, 100, 0);

		BufferedImage result = ImageUtils.applyAdjustments(source, adj);

		assertThat(result.getWidth()).isEqualTo(30);
		assertThat(result.getHeight()).isEqualTo(20);
	}

	@Test
	void applyAdjustments_preservesAlphaChannel() {
		BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		int argb = (128 << 24) | 0x808080;

		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 4; x++) {
				source.setRGB(x, y, argb);
			}
		}

		ImageAdjustments adj = new ImageAdjustments(50, 0, 0, 100, 0);

		BufferedImage result = ImageUtils.applyAdjustments(source, adj);

		int alpha = (result.getRGB(2, 2) >> 24) & 0xFF;
		assertThat(alpha).isEqualTo(128);
	}
}
