package org.duollectis.mapart.tools.gui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ImagePreviewPanel extends JPanel {

	private static final int MIN_SIZE = 300;
	private static final int TITLE_HEIGHT = 24;
	private static final int ARC = 10;
	private static final Color BG = GuiApp.BG_CARD;
	private static final Color BORDER_COLOR = GuiApp.BORDER;
	private static final Color TITLE_COLOR = GuiApp.TEXT_DIM;
	private static final Color PLACEHOLDER_COLOR = new Color(18, 20, 30);
	private static final Color PLACEHOLDER_TEXT_COLOR = new Color(60, 68, 90);
	private static final String PLACEHOLDER_TEXT = "Перетащите изображение сюда";

	private final String title;
	private BufferedImage image;

	public ImagePreviewPanel(String title) {
		this.title = title;
		setOpaque(false);
		setMinimumSize(new Dimension(MIN_SIZE, MIN_SIZE));
		setPreferredSize(new Dimension(MIN_SIZE, MIN_SIZE));
	}

	public void setImage(BufferedImage image) {
		this.image = image;
		repaint();
	}

	public void clear() {
		image = null;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		int w = getWidth();
		int h = getHeight();

		g2.setColor(BG);
		g2.fillRoundRect(0, 0, w, h, ARC, ARC);

		g2.setColor(BORDER_COLOR);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

		g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
		g2.setColor(TITLE_COLOR);
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(title, 10, fm.getAscent() + 5);

		int contentX = 4;
		int contentY = TITLE_HEIGHT;
		int contentW = w - 8;
		int contentH = h - TITLE_HEIGHT - 4;

		if (image == null) {
			drawPlaceholder(g2, contentX, contentY, contentW, contentH);
		} else {
			drawScaledImage(g2, contentX, contentY, contentW, contentH);
		}

		g2.dispose();
	}

	private void drawPlaceholder(Graphics2D g2, int x, int y, int w, int h) {
		g2.setColor(PLACEHOLDER_COLOR);
		g2.fillRoundRect(x, y, w, h, 6, 6);

		g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
		g2.setColor(PLACEHOLDER_TEXT_COLOR);
		FontMetrics fm = g2.getFontMetrics();
		int textX = x + (w - fm.stringWidth(PLACEHOLDER_TEXT)) / 2;
		int textY = y + h / 2 + fm.getAscent() / 2;
		g2.drawString(PLACEHOLDER_TEXT, textX, textY);
	}

	private void drawScaledImage(Graphics2D g2, int x, int y, int w, int h) {
		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();

		double scaleX = (double) w / imgWidth;
		double scaleY = (double) h / imgHeight;
		double scale = Math.min(scaleX, scaleY);

		int drawWidth = (int) (imgWidth * scale);
		int drawHeight = (int) (imgHeight * scale);
		int drawX = x + (w - drawWidth) / 2;
		int drawY = y + (h - drawHeight) / 2;

		g2.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
	}

	@Override
	public Insets getInsets() {
		return new Insets(TITLE_HEIGHT + 4, 4, 4, 4);
	}
}
