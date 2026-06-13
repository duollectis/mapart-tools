package org.duollectis.mapart.tools.gui.widget;

import lombok.Getter;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UiAnimator;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Полностью кастомный слайдер на базе JPanel — без JSlider.
 * Поддерживает drag, click-to-set, hover-анимацию thumb и плавную отрисовку трека.
 */
public class StyledSlider extends JPanel {

	private static final int TRACK_HEIGHT = 4;
	private static final int THUMB_SIZE_NORMAL = 12;
	private static final int THUMB_SIZE_HOVER = 16;
	static final int TRACK_PADDING = 10;

	private final int min;
	private final int max;
	@Getter
	private int value;

	private float hoverProgress = 0f;
	private Timer hoverTimer;
	private boolean thumbHovered = false;
	private boolean dragging = false;

	private final List<ChangeListener> changeListeners = new ArrayList<>();

	public StyledSlider(int min, int max, int value) {
		this.min = min;
		this.max = max;
		this.value = Math.clamp(value, min, max);

		setOpaque(false);
		setPreferredSize(new Dimension(0, 20));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		MouseAdapter mouseHandler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dragging = true;
				updateValueFromX(e.getX());
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				dragging = false;
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				updateValueFromX(e.getX());
				updateThumbHover(e.getX(), e.getY());
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				updateThumbHover(e.getX(), e.getY());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (thumbHovered) {
					thumbHovered = false;
					animateHover(false);
				}
			}
		};

		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
	}

	public void setValue(int newValue) {
		int clamped = Math.clamp(newValue, min, max);

		if (clamped == value) {
			return;
		}

		value = clamped;
		repaint();
		fireChangeEvent();
	}

	/** Устанавливает значение без уведомления слушателей — для программной синхронизации. */
	public void setValueSilently(int newValue) {
		int clamped = Math.clamp(newValue, min, max);

		if (clamped == value) {
			return;
		}

		value = clamped;
		repaint();
	}

	public int getMinimum() {
		return min;
	}

	public int getMaximum() {
		return max;
	}

	public void addChangeListener(ChangeListener listener) {
		changeListeners.add(listener);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int trackX = TRACK_PADDING;
		int trackW = getWidth() - TRACK_PADDING * 2;
		int trackY = (getHeight() - TRACK_HEIGHT) / 2;
		int arc = TRACK_HEIGHT;

		g2.setColor(GuiApp.theme.getSliderTrackBg());
		g2.fillRoundRect(trackX, trackY, trackW, TRACK_HEIGHT, arc, arc);

		int thumbCx = computeThumbCx(trackX, trackW);
		int fillW = thumbCx - trackX;

		if (fillW > 0) {
			g2.setColor(GuiApp.theme.getSliderTrackFill());
			g2.fillRoundRect(trackX, trackY, fillW, TRACK_HEIGHT, arc, arc);
		}

		int size = (int) (THUMB_SIZE_NORMAL + (THUMB_SIZE_HOVER - THUMB_SIZE_NORMAL) * hoverProgress);
		int r = size / 2;
		Color thumbColor = UiAnimator.lerp(GuiApp.theme.getSliderThumb(), GuiApp.theme.getSliderThumbHover(), hoverProgress);

		g2.setColor(thumbColor);
		g2.fillOval(thumbCx - r, getHeight() / 2 - r, size, size);

		g2.dispose();
	}

	private int computeThumbCx(int trackX, int trackW) {
		float ratio = (float) (value - min) / (max - min);
		return trackX + (int) (ratio * trackW);
	}

	private void updateValueFromX(int mouseX) {
		int trackX = TRACK_PADDING;
		int trackW = getWidth() - TRACK_PADDING * 2;
		float ratio = (float) (mouseX - trackX) / trackW;
		int newValue = min + Math.round(ratio * (max - min));
		setValue(Math.clamp(newValue, min, max));
	}

	private void updateThumbHover(int mx, int my) {
		int trackX = TRACK_PADDING;
		int trackW = getWidth() - TRACK_PADDING * 2;
		int thumbCx = computeThumbCx(trackX, trackW);
		int thumbCy = getHeight() / 2;
		int r = THUMB_SIZE_NORMAL / 2 + 4;

		boolean over = Math.abs(mx - thumbCx) <= r && Math.abs(my - thumbCy) <= r;

		if (over == thumbHovered) {
			return;
		}

		thumbHovered = over;
		animateHover(over);
	}

	private void animateHover(boolean toHovered) {
		if (hoverTimer != null) {
			hoverTimer.stop();
		}

		float from = hoverProgress;
		float to = toHovered ? 1f : 0f;

		hoverTimer = UiAnimator.animateFloat(from, to, 150, progress -> {
			hoverProgress = progress;
			repaint();
		}, null);
	}

	private void fireChangeEvent() {
		ChangeEvent event = new ChangeEvent(this);

		for (ChangeListener listener : changeListeners) {
			listener.stateChanged(event);
		}
	}
}
