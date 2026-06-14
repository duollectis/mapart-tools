package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Горизонтальный переключатель в стиле iOS-switch.
 * Ползунок анимированно скользит влево (выкл) или вправо (вкл).
 * Поддерживает произвольные метки слева и справа от трека.
 */
public class SliderToggle extends JPanel {

	private static final int TRACK_WIDTH = 40;
	private static final int TRACK_HEIGHT = 20;
	private static final int THUMB_PADDING = 2;
	private static final int THUMB_DIAMETER = TRACK_HEIGHT - THUMB_PADDING * 2;

	private boolean selected;
	private float animProgress;
	private float hoverProgress;
	private Timer animTimer;
	private Timer hoverTimer;

	private String labelOn = "";
	private String labelOff = "";

	private final List<Consumer<Boolean>> listeners = new ArrayList<>();

	public SliderToggle(boolean initialSelected) {
		this.selected = initialSelected;
		this.animProgress = initialSelected ? 1f : 0f;

		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setLayout(new BorderLayout(8, 0));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				animateHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				animateHover(false);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				toggle();
			}
		});
	}

	public SliderToggle() {
		this(false);
	}

	public void setLabelOn(String text) {
		labelOn = text;
		repaint();
	}

	public void setLabelOff(String text) {
		labelOff = text;
		repaint();
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean value) {
		if (selected == value) {
			return;
		}

		selected = value;
		animateTo(value ? 1f : 0f);
		fireListeners();
	}

	public void setSelectedSilently(boolean value) {
		selected = value;
		animProgress = value ? 1f : 0f;
		repaint();
	}

	public void addChangeListener(Consumer<Boolean> listener) {
		listeners.add(listener);
	}

	private void toggle() {
		selected = !selected;
		animateTo(selected ? 1f : 0f);
		fireListeners();
	}

	private void animateTo(float target) {
		if (animTimer != null) {
			animTimer.stop();
		}

		float from = animProgress;
		animTimer = UiAnimator.animateFloat(from, target, 180, progress -> {
			animProgress = progress;
			repaint();
		}, null);
	}

	private void animateHover(boolean entering) {
		if (hoverTimer != null) {
			hoverTimer.stop();
		}

		float from = hoverProgress;
		float to = entering ? 1f : 0f;

		hoverTimer = UiAnimator.animateFloat(from, to, 150, progress -> {
			hoverProgress = progress;
			repaint();
		}, null);
	}

	private void fireListeners() {
		for (Consumer<Boolean> listener : listeners) {
			listener.accept(selected);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int totalH = getHeight();
		FontMetrics fm = g2.getFontMetrics(getFont());
		int textY = (totalH + fm.getAscent() - fm.getDescent()) / 2;

		Color textColor = GuiApp.theme.getText();
		Color textDim = GuiApp.theme.getTextDim();

		if (labelOff != null && !labelOff.isEmpty()) {
			int offW = fm.stringWidth(labelOff);
			g2.setFont(getFont());
			g2.setColor(selected ? textDim : textColor);
			g2.drawString(labelOff, 0, textY);

			paintTrack(g2, offW + 8, totalH);
			paintRightLabel(g2, offW + 8 + TRACK_WIDTH + 8, textY, fm, textColor, textDim);
		} else {
			paintTrack(g2, 0, totalH);
			paintRightLabel(g2, TRACK_WIDTH + 8, textY, fm, textColor, textDim);
		}

		g2.dispose();
	}

	private void paintTrack(Graphics2D g2, int trackX, int totalH) {
		int trackY = (totalH - TRACK_HEIGHT) / 2;

		Color trackOffBase = UiAnimator.lerp(GuiApp.theme.getBgInput(), GuiApp.theme.getBtnHoverBg(), hoverProgress);
		Color trackOnBase = UiAnimator.lerp(GuiApp.theme.getAccent(), GuiApp.theme.getAccentBright(), hoverProgress);
		Color trackColor = UiAnimator.lerp(trackOffBase, trackOnBase, animProgress);

		g2.setColor(trackColor);
		g2.fillRoundRect(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

		g2.setColor(UiAnimator.lerp(GuiApp.theme.getBorder(), GuiApp.theme.getAccent(), animProgress));
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(trackX, trackY, TRACK_WIDTH - 1, TRACK_HEIGHT - 1, TRACK_HEIGHT, TRACK_HEIGHT);

		int thumbTravel = TRACK_WIDTH - THUMB_PADDING * 2 - THUMB_DIAMETER;
		int thumbX = trackX + THUMB_PADDING + Math.round(thumbTravel * animProgress);
		int thumbY = trackY + THUMB_PADDING;

		Color thumbColor = UiAnimator.lerp(GuiApp.theme.getScrollbarThumbHover(), Color.WHITE, animProgress);
		g2.setColor(thumbColor);
		g2.fillOval(thumbX, thumbY, THUMB_DIAMETER, THUMB_DIAMETER);
	}

	private void paintRightLabel(Graphics2D g2, int x, int textY, FontMetrics fm, Color textColor, Color textDim) {
		if (labelOn == null || labelOn.isEmpty()) {
			return;
		}

		g2.setFont(getFont());
		g2.setColor(selected ? textColor : textDim);
		g2.drawString(labelOn, x, textY);
	}

	@Override
	public Dimension getPreferredSize() {
		FontMetrics fm = getFontMetrics(getFont());
		int offW = (labelOff != null && !labelOff.isEmpty()) ? fm.stringWidth(labelOff) + 8 : 0;
		int onW = (labelOn != null && !labelOn.isEmpty()) ? fm.stringWidth(labelOn) + 8 : 0;
		int totalW = offW + TRACK_WIDTH + onW;
		return new Dimension(totalW, 28);
	}
}
