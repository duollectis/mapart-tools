package org.duollectis.mapart.tools.gui;

import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Кастомный слайдер с ручной отрисовкой трека и ползунка.
 * Стандартный JSlider с setOpaque(false) становится невидимым на тёмной теме,
 * поэтому весь рендеринг реализован через BasicSliderUI с переопределёнными методами.
 */
public class ModernSlider extends JSlider {

	private static final Color TRACK_BG = new Color(45, 50, 70);
	private static final Color TRACK_FILL = new Color(99, 120, 255);
	private static final Color THUMB_COLOR = new Color(140, 158, 255);
	private static final Color THUMB_HOVER = new Color(170, 185, 255);

	private static final int TRACK_HEIGHT = 4;
	private static final int THUMB_SIZE = 12;

	public ModernSlider(int min, int max, int value) {
		super(min, max, value);
		setOpaque(false);
		setFocusable(false);
		setPaintTicks(false);
		setPaintLabels(false);
		setPreferredSize(new Dimension(0, 20));
		setUI(new ModernSliderUI(this));
	}

	private static class ModernSliderUI extends BasicSliderUI {

		private boolean thumbHovered = false;

		ModernSliderUI(JSlider slider) {
			super(slider);
			slider.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseEntered(java.awt.event.MouseEvent e) {
					thumbHovered = true;
					slider.repaint();
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e) {
					thumbHovered = false;
					slider.repaint();
				}
			});
		}

		@Override
		public void paintTrack(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int trackY = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2;
			int trackX = trackRect.x;
			int trackW = trackRect.width;
			int arc = TRACK_HEIGHT;

			g2.setColor(TRACK_BG);
			g2.fillRoundRect(trackX, trackY, trackW, TRACK_HEIGHT, arc, arc);

			int thumbX = thumbRect.x + thumbRect.width / 2;
			int fillW = thumbX - trackX;

			if (fillW > 0) {
				g2.setColor(TRACK_FILL);
				g2.fillRoundRect(trackX, trackY, fillW, TRACK_HEIGHT, arc, arc);
			}

			g2.dispose();
		}

		@Override
		public void paintThumb(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int cx = thumbRect.x + thumbRect.width / 2;
			int cy = thumbRect.y + thumbRect.height / 2;
			int r = THUMB_SIZE / 2;

			g2.setColor(thumbHovered ? THUMB_HOVER : THUMB_COLOR);
			g2.fillOval(cx - r, cy - r, THUMB_SIZE, THUMB_SIZE);

			g2.dispose();
		}

		@Override
		public void paintFocus(Graphics g) {
			// Фокусный прямоугольник не нужен в тёмной теме
		}

		@Override
		protected Dimension getThumbSize() {
			return new Dimension(THUMB_SIZE, THUMB_SIZE);
		}
	}
}
