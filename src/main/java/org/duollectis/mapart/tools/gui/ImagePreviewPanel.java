package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.CropSettings;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

public class ImagePreviewPanel extends JPanel {

	private static final int MIN_SIZE = 300;
	private static final int TITLE_HEIGHT = 24;
	private static final int ARC = 10;
	private static final int RESIZE_HIT_ZONE = 8;
	private static final double ZOOM_STEP = 0.1;
	private static final double SCALE_MIN = 0.05;
	private static final double SCALE_MAX = 10.0;
	private static final int ARROW_STEP = 1;
	private static final int ARROW_STEP_FAST = 10;
	private static final Color BG = GuiApp.BG_CARD;
	private static final Color BORDER_COLOR = GuiApp.BORDER;
	private static final Color TITLE_COLOR = GuiApp.TEXT_DIM;
	private static final Color PLACEHOLDER_COLOR = new Color(18, 20, 30);
	private static final Color PLACEHOLDER_TEXT_COLOR = new Color(60, 68, 90);
	private static final Color GRID_COLOR = new Color(255, 255, 255);
	private static final String PLACEHOLDER_TEXT = "Перетащите изображение сюда";

	private final String title;
	private BufferedImage image;
	private boolean showGrid;
	private boolean showGridBackground;
	private int mapsX = 1;
	private int mapsY = 1;
	private float gridStrokeWidth = 1f;
	private Color gridBackgroundColor = Color.BLACK;

	// Режим перетаскивания (только для sourcePreview)
	private boolean draggable;
	private BiConsumer<Integer, Integer> onOffsetChanged;

	/**
	 * Пиксельный масштаб картинки: итоговый размер = image.getWidth() * imgScaleX.
	 * При imgScaleX=1.0 картинка отображается в натуральном размере (1:1).
	 * imgScaleX/Y могут отличаться при одностороннем resize.
	 */
	private double imgScaleX = 1.0;
	private double imgScaleY = 1.0;

	/**
	 * Смещение центра картинки от центра сетки в пикселях экрана.
	 */
	private double imgOffsetX;
	private double imgOffsetY;

	// Drag-состояние (перемещение картинки)
	private int dragStartMouseX;
	private int dragStartMouseY;
	private double dragStartOffsetX;
	private double dragStartOffsetY;

	// Resize-состояние
	private boolean resizing;
	private int resizeCursorType;
	// Начальные координаты сторон картинки в момент начала resize
	private double resizeStartLeft;
	private double resizeStartRight;
	private double resizeStartTop;
	private double resizeStartBottom;

	public ImagePreviewPanel(String title) {
		this.title = title;
		setOpaque(false);
		setFocusable(true);
		setMinimumSize(new Dimension(MIN_SIZE, MIN_SIZE));
		setPreferredSize(new Dimension(MIN_SIZE, MIN_SIZE));
		setupMouseListeners();
		setupKeyBindings();
	}

	public void setImage(BufferedImage image) {
		this.image = image;
		repaint();
	}

	public void clear() {
		image = null;
		repaint();
	}

	public BufferedImage getImage() {
		return image;
	}

	public void setShowGrid(boolean show) {
		showGrid = show;
		repaint();
	}

	public void setMapCount(int x, int y) {
		mapsX = x;
		mapsY = y;
		repaint();
	}

	public void setGridStrokeWidth(float width) {
		gridStrokeWidth = width;
		repaint();
	}

	public void setGridBackgroundColor(Color color) {
		gridBackgroundColor = color;
		repaint();
	}

	public Color getGridBackgroundColor() {
		return gridBackgroundColor;
	}

	/** Включает отрисовку фона сетки (чёрный прямоугольник под картинкой). */
	public void setShowGridBackground(boolean show) {
		showGridBackground = show;
		repaint();
	}

	/**
	 * Включает интерактивный режим: drag картинки, resize за края, zoom колёсиком.
	 * Колбэк вызывается при каждом изменении позиции/размера.
	 */
	public void setInteractive(Runnable onChange) {
		draggable = true;
		onOffsetChanged = (dx, dy) -> onChange.run();
	}

	/**
	 * Сбрасывает картинку к fit-виду относительно сетки.
	 * imgScale = min(gridW/imgW, gridH/imgH) — картинка вписывается в сетку с сохранением пропорций.
	 * Единая система координат: imgScale = 1.0 означает "картинка = сетка".
	 */
	public void resetDisplayOffset() {
		imgOffsetX = 0;
		imgOffsetY = 0;

		if (image == null || getWidth() == 0 || getHeight() == 0) {
			imgScaleX = 1.0;
			imgScaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		// Fit картинки в сетку с сохранением пропорций: imgScale = экранный px / исходный px
		double scaleX = (double) grid[2] / image.getWidth();
		double scaleY = (double) grid[3] / image.getHeight();
		imgScaleX = Math.min(scaleX, scaleY);
		imgScaleY = imgScaleX;

		repaint();
	}

	/**
	 * Cover-режим: масштабирует картинку так, чтобы она полностью заполнила сетку
	 * с сохранением пропорций. Края, выходящие за границы, обрезаются.
	 */
	public void resetDisplayOffsetCover() {
		imgOffsetX = 0;
		imgOffsetY = 0;

		if (image == null || getWidth() == 0 || getHeight() == 0) {
			imgScaleX = 1.0;
			imgScaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		double scaleX = (double) grid[2] / image.getWidth();
		double scaleY = (double) grid[3] / image.getHeight();
		imgScaleX = Math.max(scaleX, scaleY);
		imgScaleY = imgScaleX;

		repaint();
	}

	/**
	 * Растягивает картинку точно на всю сетку (stretch без сохранения пропорций).
	 * Используется для правой панели: дизеренное изображение имеет размер mapW*128 × mapH*128,
	 * что точно соответствует пропорциям сетки mapsX × mapsY, поэтому деформации нет.
	 */
	public void resetDisplayOffsetStretch() {
		imgOffsetX = 0;
		imgOffsetY = 0;

		if (image == null || getWidth() == 0 || getHeight() == 0) {
			imgScaleX = 1.0;
			imgScaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		imgScaleX = (double) grid[2] / image.getWidth();
		imgScaleY = (double) grid[3] / image.getHeight();

		repaint();
	}

	/**
	 * Синхронизирует визуальное состояние с другой панелью через нормализованные координаты.
	 * Дизеренное изображение отображается в том же прямоугольнике экрана что и исходник,
	 * поэтому imgScaleX/Y могут отличаться — это корректно, т.к. дизеренное уже содержит
	 * обработанный контент с учётом пропорций исходника.
	 */
	public void copyDisplayStateFrom(ImagePreviewPanel other) {
		if (image == null || other.image == null) {
			return;
		}

		int[] srcGrid = other.computeGridBounds();
		int[] dstGrid = computeGridBounds();

		// Нормализованный размер: доля ширины/высоты сетки источника
		double normW = (other.imgScaleX * other.image.getWidth()) / srcGrid[2];
		double normH = (other.imgScaleY * other.image.getHeight()) / srcGrid[3];

		// Нормализованное смещение: доля размера сетки источника
		double normOffsetX = other.imgOffsetX / srcGrid[2];
		double normOffsetY = other.imgOffsetY / srcGrid[3];

		// Экранный размер в dst через нормализованные доли
		double dstScreenW = normW * dstGrid[2];
		double dstScreenH = normH * dstGrid[3];

		imgScaleX = dstScreenW / image.getWidth();
		imgScaleY = dstScreenH / image.getHeight();
		imgOffsetX = normOffsetX * dstGrid[2];
		imgOffsetY = normOffsetY * dstGrid[3];

		repaint();
	}

	/**
	 * Конвертирует текущее визуальное состояние панели в {@link CropSettings}
	 * для передачи в дизер. Пересчитывает экранные координаты в пиксели целевого изображения.
	 *
	 * <p>Математика:
	 * <ul>
	 *   <li>{@code baseScreenScale = min(gridW/imgW, gridH/imgH)} — базовый fit-масштаб</li>
	 *   <li>{@code userScaleX = imgScaleX / baseScreenScale} — пользовательский зум по X</li>
	 *   <li>{@code userScaleY = imgScaleY / baseScreenScale} — пользовательский зум по Y</li>
	 *   <li>{@code offsetX_target = imgOffsetX * (targetW / gridW)} — смещение в пикселях целевого</li>
	 * </ul>
	 * Раздельные scaleX/scaleY необходимы для корректного воспроизведения деформаций,
	 * которые пользователь задал resize'ом (imgScaleX ≠ imgScaleY).
	 *
	 * @param targetW ширина целевого изображения в пикселях (mapWidth * 128)
	 * @param targetH высота целевого изображения в пикселях (mapHeight * 128)
	 */
	public CropSettings buildCropSettings(int targetW, int targetH) {
		if (image == null || getWidth() == 0 || getHeight() == 0) {
			return CropSettings.defaultFit();
		}

		int[] grid = computeGridBounds();
		int gridW = grid[2];
		int gridH = grid[3];

		double baseScreenScale = Math.min((double) gridW / image.getWidth(), (double) gridH / image.getHeight());
		double userScaleX = imgScaleX / baseScreenScale;
		double userScaleY = imgScaleY / baseScreenScale;

		int offsetX = (int) Math.round(imgOffsetX * ((double) targetW / gridW));
		int offsetY = (int) Math.round(imgOffsetY * ((double) targetH / gridH));

		return CropSettings.fit(offsetX, offsetY, userScaleX, userScaleY);
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

		if (image != null) {
			String sizeText = image.getWidth() + "×" + image.getHeight() + " px";
			int sizeX = w - fm.stringWidth(sizeText) - 36;
			g2.drawString(sizeText, sizeX, fm.getAscent() + 5);
		}

		int contentX = 4;
		int contentY = TITLE_HEIGHT;
		int contentW = w - 8;
		int contentH = h - TITLE_HEIGHT - 4;

		if (image == null) {
			drawPlaceholder(g2, contentX, contentY, contentW, contentH);
		} else {
			drawContent(g2, contentX, contentY, contentW, contentH);
		}

		g2.dispose();
	}

	private void drawContent(Graphics2D g2, int x, int y, int w, int h) {
		int[] grid = computeGridBounds(x, y, w, h);
		int gridX = grid[0];
		int gridY = grid[1];
		int gridW = grid[2];
		int gridH = grid[3];

		// Клип строго по сетке: картинка и фон не выходят за её границы
		g2.setClip(gridX, gridY, gridW, gridH);

		if (draggable || showGridBackground) {
			g2.setColor(gridBackgroundColor);
			g2.fillRect(gridX, gridY, gridW, gridH);
		}

		// imgScaleX/Y — пиксельный масштаб: итоговый размер = исходный px * scale
		int imgW = (int) Math.round(image.getWidth() * imgScaleX);
		int imgH = (int) Math.round(image.getHeight() * imgScaleY);

		// Центр сетки + смещение
		int gridCenterX = gridX + gridW / 2;
		int gridCenterY = gridY + gridH / 2;

		int drawX = gridCenterX - imgW / 2 + (int) Math.round(imgOffsetX);
		int drawY = gridCenterY - imgH / 2 + (int) Math.round(imgOffsetY);

		g2.drawImage(image, drawX, drawY, imgW, imgH, null);
		g2.setClip(null);

		if (showGrid) {
			g2.setClip(gridX, gridY, gridW, gridH);
			drawGrid(g2, gridX, gridY, gridW, gridH);
			g2.setClip(null);
		}
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

	/**
	 * Рисует сетку в XOR-режиме: цвет линии = инверсия пикселя под ней.
	 * На тёмном фоне линии светлые, на светлом — тёмные, всегда контрастны.
	 */
	private void drawGrid(Graphics2D g2, int drawX, int drawY, int drawWidth, int drawHeight) {
		double cellW = (double) drawWidth / mapsX;
		double cellH = (double) drawHeight / mapsY;

		// XOR(WHITE) + рисование чёрным = инверсия dst: dst XOR 0xFFFFFF
		g2.setXORMode(Color.WHITE);
		g2.setColor(Color.BLACK);
		g2.setStroke(new BasicStroke(gridStrokeWidth));

		for (int col = 1; col < mapsX; col++) {
			int lineX = drawX + (int) (col * cellW);
			g2.drawLine(lineX, drawY, lineX, drawY + drawHeight);
		}

		for (int row = 1; row < mapsY; row++) {
			int lineY = drawY + (int) (row * cellH);
			g2.drawLine(drawX, lineY, drawX + drawWidth, lineY);
		}

		g2.drawRect(drawX, drawY, drawWidth - 1, drawHeight - 1);

		g2.setPaintMode();
	}

	/** Вычисляет фиксированные координаты сетки (fit-scale без imgScale). */
	private int[] computeGridBounds(int x, int y, int w, int h) {
		double fitScaleX = (double) w / mapsX;
		double fitScaleY = (double) h / mapsY;
		double fitScale = Math.min(fitScaleX, fitScaleY);

		int gridW = (int) Math.round(mapsX * fitScale);
		int gridH = (int) Math.round(mapsY * fitScale);
		int gridX = x + (w - gridW) / 2;
		int gridY = y + (h - gridH) / 2;

		return new int[]{gridX, gridY, gridW, gridH};
	}

	/** Вычисляет координаты сетки по текущим размерам панели. */
	private int[] computeGridBounds() {
		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		return computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);
	}

	/**
	 * Вычисляет текущие экранные координаты картинки (с imgScaleX/Y и imgOffset).
	 * Возвращает [left, top, right, bottom].
	 */
	private double[] computeImageEdges() {
		int[] grid = computeGridBounds();
		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		double imgW = image.getWidth() * imgScaleX;
		double imgH = image.getHeight() * imgScaleY;
		double centerX = gridCenterX + imgOffsetX;
		double centerY = gridCenterY + imgOffsetY;

		return new double[]{
			centerX - imgW / 2,
			centerY - imgH / 2,
			centerX + imgW / 2,
			centerY + imgH / 2
		};
	}

	/**
	 * Определяет, находится ли курсор в зоне resize (у края картинки).
	 * Возвращает тип курсора или -1 если не в зоне resize.
	 */
	private int getResizeCursorType(int mx, int my) {
		if (image == null) {
			return -1;
		}

		double[] edges = computeImageEdges();
		double left = edges[0];
		double top = edges[1];
		double right = edges[2];
		double bottom = edges[3];

		boolean nearLeft = mx >= left - RESIZE_HIT_ZONE && mx <= left + RESIZE_HIT_ZONE;
		boolean nearRight = mx >= right - RESIZE_HIT_ZONE && mx <= right + RESIZE_HIT_ZONE;
		boolean nearTop = my >= top - RESIZE_HIT_ZONE && my <= top + RESIZE_HIT_ZONE;
		boolean nearBottom = my >= bottom - RESIZE_HIT_ZONE && my <= bottom + RESIZE_HIT_ZONE;

		if (nearLeft && nearTop) {
			return Cursor.NW_RESIZE_CURSOR;
		}

		if (nearRight && nearTop) {
			return Cursor.NE_RESIZE_CURSOR;
		}

		if (nearLeft && nearBottom) {
			return Cursor.SW_RESIZE_CURSOR;
		}

		if (nearRight && nearBottom) {
			return Cursor.SE_RESIZE_CURSOR;
		}

		if (nearLeft) {
			return Cursor.W_RESIZE_CURSOR;
		}

		if (nearRight) {
			return Cursor.E_RESIZE_CURSOR;
		}

		if (nearTop) {
			return Cursor.N_RESIZE_CURSOR;
		}

		if (nearBottom) {
			return Cursor.S_RESIZE_CURSOR;
		}

		return -1;
	}

	private void setupMouseListeners() {
		MouseAdapter dragAdapter = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();

				if (draggable == false || image == null) {
					return;
				}

				int cursorType = getResizeCursorType(e.getX(), e.getY());

				if (cursorType != -1) {
					double[] edges = computeImageEdges();
					resizing = true;
					resizeCursorType = cursorType;
					resizeStartLeft = edges[0];
					resizeStartTop = edges[1];
					resizeStartRight = edges[2];
					resizeStartBottom = edges[3];
					return;
				}

				dragStartMouseX = e.getX();
				dragStartMouseY = e.getY();
				dragStartOffsetX = imgOffsetX;
				dragStartOffsetY = imgOffsetY;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				resizing = false;
				updateCursor(e.getX(), e.getY());
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (draggable == false || image == null) {
					return;
				}

				updateCursor(e.getX(), e.getY());
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (draggable == false || image == null) {
					return;
				}

				if (resizing) {
					handleResize(e);
				} else {
					handleDrag(e);
				}
			}
		};

		MouseWheelListener wheelListener = e -> {
			if (draggable == false || image == null) {
				return;
			}

			handleZoom(e.getX(), e.getY(), e.getWheelRotation() < 0 ? ZOOM_STEP : -ZOOM_STEP);
		};

		addMouseListener(dragAdapter);
		addMouseMotionListener(dragAdapter);
		addMouseWheelListener(wheelListener);
	}

	private void setupKeyBindings() {
		int condition = JComponent.WHEN_FOCUSED;

		bindArrow(condition, KeyEvent.VK_LEFT, false, -ARROW_STEP, 0);
		bindArrow(condition, KeyEvent.VK_RIGHT, false, ARROW_STEP, 0);
		bindArrow(condition, KeyEvent.VK_UP, false, 0, -ARROW_STEP);
		bindArrow(condition, KeyEvent.VK_DOWN, false, 0, ARROW_STEP);

		bindArrow(condition, KeyEvent.VK_LEFT, true, -ARROW_STEP_FAST, 0);
		bindArrow(condition, KeyEvent.VK_RIGHT, true, ARROW_STEP_FAST, 0);
		bindArrow(condition, KeyEvent.VK_UP, true, 0, -ARROW_STEP_FAST);
		bindArrow(condition, KeyEvent.VK_DOWN, true, 0, ARROW_STEP_FAST);
	}

	private void bindArrow(int condition, int keyCode, boolean shift, int dx, int dy) {
		int modifiers = shift ? KeyEvent.SHIFT_DOWN_MASK : 0;
		String key = (shift ? "shift-" : "") + keyCode + "-" + dx + "-" + dy;

		getInputMap(condition).put(KeyStroke.getKeyStroke(keyCode, modifiers), key);
		getActionMap().put(key, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (draggable == false || image == null) {
					return;
				}

				imgOffsetX += dx;
				imgOffsetY += dy;

				repaint();

				if (onOffsetChanged != null) {
					onOffsetChanged.accept((int) Math.round(imgOffsetX), (int) Math.round(imgOffsetY));
				}
			}
		});
	}

	private void updateCursor(int mx, int my) {
		int cursorType = getResizeCursorType(mx, my);

		if (cursorType != -1) {
			setCursor(Cursor.getPredefinedCursor(cursorType));
		} else {
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		}
	}

	private void handleDrag(MouseEvent e) {
		imgOffsetX = dragStartOffsetX + (e.getX() - dragStartMouseX);
		imgOffsetY = dragStartOffsetY + (e.getY() - dragStartMouseY);

		repaint();

		if (onOffsetChanged != null) {
			onOffsetChanged.accept((int) Math.round(imgOffsetX), (int) Math.round(imgOffsetY));
		}
	}

	/**
	 * Resize картинки за конкретную сторону/угол.
	 * Захваченная сторона следует за мышью, противоположная фиксирована.
	 * Без модификатора — свободная деформация (imgScaleX/Y независимы).
	 * С Shift или Ctrl — пропорциональный resize: соотношение сторон сохраняется.
	 */
	private void handleResize(MouseEvent e) {
		int[] grid = computeGridBounds();
		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		double mx = e.getX();
		double my = e.getY();

		double newLeft = resizeStartLeft;
		double newRight = resizeStartRight;
		double newTop = resizeStartTop;
		double newBottom = resizeStartBottom;

		switch (resizeCursorType) {
			case Cursor.E_RESIZE_CURSOR -> newRight = mx;
			case Cursor.W_RESIZE_CURSOR -> newLeft = mx;
			case Cursor.S_RESIZE_CURSOR -> newBottom = my;
			case Cursor.N_RESIZE_CURSOR -> newTop = my;
			case Cursor.SE_RESIZE_CURSOR -> { newRight = mx; newBottom = my; }
			case Cursor.SW_RESIZE_CURSOR -> { newLeft = mx; newBottom = my; }
			case Cursor.NE_RESIZE_CURSOR -> { newRight = mx; newTop = my; }
			case Cursor.NW_RESIZE_CURSOR -> { newLeft = mx; newTop = my; }
			default -> { return; }
		}

		double newW = newRight - newLeft;
		double newH = newBottom - newTop;

		if (newW < 10 || newH < 10) {
			return;
		}

		double newScaleX = Math.max(SCALE_MIN, Math.min(SCALE_MAX, newW / image.getWidth()));
		double newScaleY = Math.max(SCALE_MIN, Math.min(SCALE_MAX, newH / image.getHeight()));

		if (e.isShiftDown() || e.isControlDown()) {
			// Пропорциональный resize: выбираем ведущую ось по наибольшему изменению
			double deltaW = Math.abs(newW - (resizeStartRight - resizeStartLeft));
			double deltaH = Math.abs(newH - (resizeStartBottom - resizeStartTop));
			double uniformScale = deltaW >= deltaH ? newScaleX : newScaleY;

			uniformScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, uniformScale));

			// Пересчитываем границы с сохранением пропорций, фиксируя противоположную сторону
			double scaledW = image.getWidth() * uniformScale;
			double scaledH = image.getHeight() * uniformScale;

			switch (resizeCursorType) {
				case Cursor.E_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR -> {
					newRight = newLeft + scaledW;
					newBottom = newTop + scaledH;
				}
				case Cursor.W_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR -> {
					newLeft = newRight - scaledW;
					newBottom = newTop + scaledH;
				}
				case Cursor.S_RESIZE_CURSOR -> newRight = newLeft + scaledW;
				case Cursor.N_RESIZE_CURSOR -> newRight = newLeft + scaledW;
				default -> { }
			}

			newScaleX = uniformScale;
			newScaleY = uniformScale;
		}

		double newCenterX = (newLeft + newRight) / 2.0;
		double newCenterY = (newTop + newBottom) / 2.0;

		imgScaleX = newScaleX;
		imgScaleY = newScaleY;
		imgOffsetX = newCenterX - gridCenterX;
		imgOffsetY = newCenterY - gridCenterY;

		repaint();
	}

	/**
	 * Зум с привязкой к позиции курсора: точка под курсором остаётся на месте.
	 * Зум пропорциональный — оба масштаба меняются одинаково.
	 */
	private void handleZoom(int mx, int my, double delta) {
		double oldScaleX = imgScaleX;
		double oldScaleY = imgScaleY;

		double newScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, imgScaleX * (1.0 + delta)));
		imgScaleX = newScale;
		imgScaleY = newScale;

		if (imgScaleX == oldScaleX && imgScaleY == oldScaleY) {
			return;
		}

		int[] grid = computeGridBounds();
		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		// Позиция курсора относительно центра картинки до зума
		double cursorRelX = mx - (gridCenterX + imgOffsetX);
		double cursorRelY = my - (gridCenterY + imgOffsetY);

		// Корректируем смещение: точка под курсором остаётся на месте
		double factorX = imgScaleX / oldScaleX;
		double factorY = imgScaleY / oldScaleY;
		imgOffsetX += cursorRelX - cursorRelX * factorX;
		imgOffsetY += cursorRelY - cursorRelY * factorY;

		repaint();
	}

	@Override
	public Insets getInsets() {
		return new Insets(TITLE_HEIGHT + 4, 4, 4, 4);
	}
}
