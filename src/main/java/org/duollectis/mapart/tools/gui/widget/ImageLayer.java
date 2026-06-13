package org.duollectis.mapart.tools.gui.widget;

import java.awt.image.BufferedImage;

/**
 * Модель одного слоя в {@link ImagePreviewPanel}.
 * Хранит исходное изображение, его оригинал до коррекции и трансформ (масштаб + смещение).
 */
public final class ImageLayer {

	private BufferedImage image;
	private BufferedImage rawImage;
	private String name;
	private boolean visible = true;
	private String sourcePath;

	double scaleX = 1.0;
	double scaleY = 1.0;
	double offsetX = 0.0;
	double offsetY = 0.0;

	public ImageLayer(BufferedImage image, String name) {
		this.image = image;
		this.rawImage = image;
		this.name = name;
	}

	public BufferedImage getImage() {
		return image;
	}

	public void setImage(BufferedImage image) {
		this.image = image;
	}

	public BufferedImage getRawImage() {
		return rawImage;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public double getScaleX() {
		return scaleX;
	}

	public double getScaleY() {
		return scaleY;
	}

	public double getOffsetX() {
		return offsetX;
	}

	public double getOffsetY() {
		return offsetY;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}
}
