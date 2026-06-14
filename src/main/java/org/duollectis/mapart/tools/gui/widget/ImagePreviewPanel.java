package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.converter.CropSettings;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public class ImagePreviewPanel extends JPanel {

	public static final double MAX_BLUR_RADIUS = 5.0;

	private static final int MIN_SIZE = 300;
	private static final int TITLE_HEIGHT = 24;
	private static final int ARC = 10;
	private static final int RESIZE_HIT_ZONE = 8;
	private static final double ZOOM_STEP = 0.1;
	private static final double SCALE_MIN = 0.05;
	private static final double SCALE_MAX = 10.0;
	private static final int ARROW_STEP = 1;
	private static final int ARROW_STEP_FAST = 10;
	private static final int SNAP_THRESHOLD = 5;

	private static Color bg() {return GuiApp.theme.getBgCard();}

	private static Color borderColor() {return GuiApp.theme.getBorder();}

	private static Color titleColor() {return GuiApp.theme.getTextDim();}

	private static Color placeholderBg() {return GuiApp.theme.getPreviewPlaceholderBg();}

	private static Color placeholderText() {return GuiApp.theme.getTextDim();}

	private String title;
	private BufferedImage contentCache;
	private double blurRadius;
	private float gridAlpha;
	private Timer gridAnimTimer;
	private boolean showGridBackground;
	private int mapsX = 1;
	private int mapsY = 1;
	private float gridStrokeWidth = 1f;
	private Color gridBackgroundColor = Color.BLACK;

	// Слои
	private final List<ImageLayer> layers = new ArrayList<>();
	private int activeLayerIndex = -1;
	private Runnable onLayersChanged;
	private IntConsumer onActiveLayerChanged;

	// Последний известный размер сетки — для пересчёта масштаба при ресайзе панели
	private int lastGridW;
	private int lastGridH;

	// Режим перетаскивания (только для sourcePreview)
	private boolean draggable;
	private boolean snapEnabled = true;
	private BiConsumer<Integer, Integer> onOffsetChanged;

	private boolean pixelPerfect;

	// Drag-состояние (перемещение картинки)
	private int dragStartMouseX;
	private int dragStartMouseY;
	private double dragStartOffsetX;
	private double dragStartOffsetY;

	// Resize-состояние
	private boolean resizing;
	private int resizeCursorType;
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
		setupResizeListener();
	}

	// ── Public API ─────────────────────────────────────────────────────────────

	/**
	 * Добавляет новый слой поверх существующих и делает его активным.
	 * Масштаб нового слоя вычисляется как fit в сетку.
	 */
	public void addLayer(BufferedImage image, String name) {
		ImageLayer layer = new ImageLayer(image, name);
		layers.add(layer);
		activeLayerIndex = layers.size() - 1;

		int[] grid = computeGridBounds();
		lastGridW = grid[2];
		lastGridH = grid[3];

		fitLayerToGrid(layer, grid[2], grid[3]);
		notifyLayersChanged();
		repaint();
	}

	/**
	 * Добавляет слой и позиционирует его в ячейку сетки (col, row) из сетки totalCols × totalRows.
	 * Используется при импорте схем с именами вида map_X_Y — каждая схема занимает одну ячейку.
	 * Координаты нормализованы, поэтому корректно работают при любом размере панели.
	 *
	 * @param col       0-based колонка (X-1 из имени файла)
	 * @param row       0-based строка (Y-1 из имени файла)
	 * @param totalCols общее количество колонок в сетке
	 * @param totalRows общее количество строк в сетке
	 */
	public void addLayerAtGridCell(BufferedImage image, String name, int col, int row, int totalCols, int totalRows) {
		double cellW = 1.0 / totalCols;
		double cellH = 1.0 / totalRows;
		double normOffsetX = (col + 0.5 - totalCols / 2.0) * cellW;
		double normOffsetY = (row + 0.5 - totalRows / 2.0) * cellH;
		addLayerNormalized(image, name, true, cellW, cellH, normOffsetX, normOffsetY);
	}

	/**
	 * Добавляет слой с нормализованным трансформом — используется при восстановлении сессии.
	 * Нормализованные координаты не зависят от размера окна:
	 * normW = scaleX * imageW / gridW, normOffsetX = offsetX / gridW.
	 * Трансформ применяется через invokeLater, когда панель уже имеет реальный размер.
	 *
	 * @param image       изображение слоя
	 * @param name        имя слоя
	 * @param visible     видимость слоя
	 * @param normW       ширина изображения как доля ширины сетки (знак = зеркало по X)
	 * @param normH       высота изображения как доля высоты сетки (знак = зеркало по Y)
	 * @param normOffsetX смещение по X как доля ширины сетки
	 * @param normOffsetY смещение по Y как доля высоты сетки
	 */
	public void addLayerNormalized(
		BufferedImage image,
		String name,
		boolean visible,
		double normW,
		double normH,
		double normOffsetX,
		double normOffsetY
	) {
		ImageLayer layer = new ImageLayer(image, name);
		layer.setVisible(visible);
		layers.add(layer);
		activeLayerIndex = layers.size() - 1;

		notifyLayersChanged();
		repaint();

		// Денормализуем трансформ после того как панель получит реальный размер
		SwingUtilities.invokeLater(() -> applyNormalizedTransform(layer, normW, normH, normOffsetX, normOffsetY));
	}

	private void applyNormalizedTransform(
		ImageLayer layer,
		double normW,
		double normH,
		double normOffsetX,
		double normOffsetY
	) {
		int[] grid = computeGridBounds();
		int gridW = grid[2];
		int gridH = grid[3];

		if (gridW == 0 || gridH == 0) {
			return;
		}

		double signX = normW < 0 ? -1.0 : 1.0;
		double signY = normH < 0 ? -1.0 : 1.0;
		layer.scaleX = signX * Math.abs(normW) * gridW / layer.getImage().getWidth();
		layer.scaleY = signY * Math.abs(normH) * gridH / layer.getImage().getHeight();
		layer.offsetX = normOffsetX * gridW;
		layer.offsetY = normOffsetY * gridH;

		lastGridW = gridW;
		lastGridH = gridH;

		repaint();
	}

	/**
	 * Склеивает указанные слои в один, сохраняя их визуальное положение.
	 * Bounding box вычисляется в grid-пикселях; результирующий слой вставляется
	 * на позицию самого нижнего из склеиваемых слоёв.
	 *
	 * @param indices список realIndex склеиваемых слоёв (минимум 2)
	 */
	public void mergeLayers(List<Integer> indices) {
		if (indices.size() < 2) {
			return;
		}

		int[] grid = computeGridBounds();
		int gridW = grid[2];
		int gridH = grid[3];

		if (gridW == 0 || gridH == 0) {
			return;
		}

		List<Integer> sorted = indices.stream().sorted().toList();

		double minLeft = Double.MAX_VALUE;
		double minTop = Double.MAX_VALUE;
		double maxRight = -Double.MAX_VALUE;
		double maxBottom = -Double.MAX_VALUE;

		for (int idx : sorted) {
			ImageLayer layer = layers.get(idx);
			double halfW = layer.getImage().getWidth() * Math.abs(layer.scaleX) / 2.0;
			double halfH = layer.getImage().getHeight() * Math.abs(layer.scaleY) / 2.0;
			minLeft = Math.min(minLeft, layer.offsetX - halfW);
			minTop = Math.min(minTop, layer.offsetY - halfH);
			maxRight = Math.max(maxRight, layer.offsetX + halfW);
			maxBottom = Math.max(maxBottom, layer.offsetY + halfH);
		}

		int bboxW = Math.max(1, (int) Math.ceil(maxRight - minLeft));
		int bboxH = Math.max(1, (int) Math.ceil(maxBottom - minTop));

		BufferedImage merged = new BufferedImage(bboxW, bboxH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = merged.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		for (int idx : sorted) {
			ImageLayer layer = layers.get(idx);
			int imgW = (int) Math.round(layer.getImage().getWidth() * Math.abs(layer.scaleX));
			int imgH = (int) Math.round(layer.getImage().getHeight() * Math.abs(layer.scaleY));
			int drawX = (int) Math.round(layer.offsetX - minLeft - imgW / 2.0);
			int drawY = (int) Math.round(layer.offsetY - minTop - imgH / 2.0);
			g2.drawImage(layer.getImage(), drawX, drawY, imgW, imgH, null);
		}

		g2.dispose();

		String mergedName = layers.get(sorted.get(0)).getName();
		int insertIndex = sorted.get(0);

		// Удаляем по убыванию, чтобы не сбивать индексы
		for (int i = sorted.size() - 1; i >= 0; i--) {
			layers.remove((int) sorted.get(i));
		}

		ImageLayer mergedLayer = new ImageLayer(merged, mergedName);
		mergedLayer.scaleX = 1.0;
		mergedLayer.scaleY = 1.0;
		mergedLayer.offsetX = (minLeft + maxRight) / 2.0;
		mergedLayer.offsetY = (minTop + maxBottom) / 2.0;

		layers.add(Math.min(insertIndex, layers.size()), mergedLayer);
		activeLayerIndex = Math.min(insertIndex, layers.size() - 1);

		notifyLayersChanged();
		repaint();
	}

	public void removeLayer(int index) {
		if (index < 0 || index >= layers.size()) {
			return;
		}

		layers.remove(index);
		activeLayerIndex = layers.isEmpty() ? -1 : Math.min(activeLayerIndex, layers.size() - 1);
		notifyLayersChanged();
		repaint();
	}

	/** Перемещает слой с позиции {@code from} на позицию {@code to}, сохраняя активный слой. */
	public void moveLayer(int from, int to) {
		if (from < 0 || to < 0 || from >= layers.size() || to >= layers.size() || from == to) {
			return;
		}

		ImageLayer moved = layers.remove(from);
		layers.add(to, moved);

		// Пересчитываем activeLayerIndex после перестановки
		if (activeLayerIndex == from) {
			activeLayerIndex = to;
		} else if (from < to && activeLayerIndex > from && activeLayerIndex <= to) {
			activeLayerIndex--;
		} else if (from > to && activeLayerIndex >= to && activeLayerIndex < from) {
			activeLayerIndex++;
		}

		notifyLayersChanged();
		repaint();
	}


	public void setActiveLayerIndex(int index) {
		if (index < 0 || index >= layers.size()) {
			return;
		}

		activeLayerIndex = index;
		repaint();

		if (onActiveLayerChanged != null) {
			onActiveLayerChanged.accept(activeLayerIndex);
		}
	}

	public int getActiveLayerIndex() {
		return activeLayerIndex;
	}

	public void setActiveLayerSourcePath(String path) {
		ImageLayer active = activeLayer();

		if (active != null) {
			active.setSourcePath(path);
		}
	}

	public List<ImageLayer> getLayers() {
		return List.copyOf(layers);
	}

	/** Возвращает [gridX, gridY, gridW, gridH] текущей сетки в экранных координатах. */
	public int[] getGridBounds() {
		return computeGridBounds();
	}

	public void setOnLayersChanged(Runnable callback) {
		onLayersChanged = callback;
	}

	public void setOnActiveLayerChanged(IntConsumer callback) {
		onActiveLayerChanged = callback;
	}

	/**
	 * Заменяет изображение активного слоя без сброса трансформа (масштаб/смещение).
	 * Используется при применении adjustments к оригиналу слоя.
	 */
	public void updateActiveLayerImage(BufferedImage adjusted) {
		ImageLayer active = activeLayer();

		if (active == null || adjusted == null) {
			return;
		}

		active.setImage(adjusted);
		contentCache = null;
		repaint();
	}

	/** Устаревший метод — заменяет все слои одним. Сохранён для совместимости с resultPreview. */
	public void setImage(BufferedImage image) {
		layers.clear();

		if (image == null) {
			activeLayerIndex = -1;
			repaint();
			return;
		}

		ImageLayer layer = new ImageLayer(image, "Layer 1");
		layers.add(layer);
		activeLayerIndex = 0;

		int[] grid = computeGridBounds();
		lastGridW = grid[2];
		lastGridH = grid[3];

		fitLayerToGrid(layer, grid[2], grid[3]);
		repaint();
	}

	public void setBlurRadius(double radius) {
		blurRadius = radius;
		repaint();
	}

	public void clear() {
		layers.clear();
		activeLayerIndex = -1;
		notifyLayersChanged();
		repaint();
	}

	/** Возвращает изображение активного слоя (для совместимости). */
	public BufferedImage getImage() {
		ImageLayer active = activeLayer();
		return active == null ? null : active.getImage();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String newTitle) {
		title = newTitle;
		repaint();
	}

	public void setShowGrid(boolean show) {
		if (gridAnimTimer != null) {
			gridAnimTimer.stop();
		}

		float target = show ? 1f : 0f;

		gridAnimTimer = UiAnimator.animateFloat(gridAlpha, target, 220, alpha -> {
			gridAlpha = alpha;
			repaint();
		}, null);
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

	public void setShowGridBackground(boolean show) {
		showGridBackground = show;
		repaint();
	}

	public void setPixelPerfect(boolean enabled) {
		pixelPerfect = enabled;
		repaint();
	}

	public void setSnapEnabled(boolean enabled) {
		snapEnabled = enabled;
	}

	public boolean isSnapEnabled() {
		return snapEnabled;
	}

	public void setInteractive(Runnable onChange) {
		draggable = true;
		onOffsetChanged = (dx, dy) -> onChange.run();
	}

	/**
	 * Сбрасывает активный слой к fit-виду относительно сетки.
	 */
	public void resetDisplayOffset() {
		ImageLayer active = activeLayer();

		if (active == null) {
			return;
		}

		active.offsetX = 0;
		active.offsetY = 0;

		if (getWidth() == 0 || getHeight() == 0) {
			active.scaleX = 1.0;
			active.scaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		double scaleX = (double) grid[2] / active.getImage().getWidth();
		double scaleY = (double) grid[3] / active.getImage().getHeight();
		active.scaleX = Math.min(scaleX, scaleY);
		active.scaleY = active.scaleX;

		lastGridW = grid[2];
		lastGridH = grid[3];

		repaint();
	}

	public void resetDisplayOffsetCover() {
		ImageLayer active = activeLayer();

		if (active == null) {
			return;
		}

		active.offsetX = 0;
		active.offsetY = 0;

		if (getWidth() == 0 || getHeight() == 0) {
			active.scaleX = 1.0;
			active.scaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		double scaleX = (double) grid[2] / active.getImage().getWidth();
		double scaleY = (double) grid[3] / active.getImage().getHeight();
		active.scaleX = Math.max(scaleX, scaleY);
		active.scaleY = active.scaleX;

		lastGridW = grid[2];
		lastGridH = grid[3];

		repaint();
	}

	/**
	 * Масштабирует активный слой под размер одной ячейки сетки (1×1 карта)
	 * и привязывает его к ближайшей ячейке по текущей позиции центра изображения.
	 */
	public void resetDisplayOffsetOneMap() {
		ImageLayer active = activeLayer();

		if (active == null) {
			return;
		}

		if (getWidth() == 0 || getHeight() == 0) {
			active.scaleX = 1.0;
			active.scaleY = 1.0;
			active.offsetX = 0;
			active.offsetY = 0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		double cellW = (double) grid[2] / mapsX;
		double cellH = (double) grid[3] / mapsY;
		double scale = Math.min(cellW / active.getImage().getWidth(), cellH / active.getImage().getHeight());
		active.scaleX = scale;
		active.scaleY = scale;

		// Текущий центр изображения в пространстве сетки (0,0 = левый верхний угол сетки)
		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;
		double imgCenterX = gridCenterX + active.offsetX;
		double imgCenterY = gridCenterY + active.offsetY;

		double relX = imgCenterX - grid[0];
		double relY = imgCenterY - grid[1];

		// Ближайшая ячейка
		int col = (int) Math.round(relX / cellW - 0.5);
		int row = (int) Math.round(relY / cellH - 0.5);
		col = Math.clamp(col, 0, mapsX - 1);
		row = Math.clamp(row, 0, mapsY - 1);

		// Центр ближайшей ячейки в пикселях панели
		double snapCenterX = grid[0] + col * cellW + cellW / 2.0;
		double snapCenterY = grid[1] + row * cellH + cellH / 2.0;

		active.offsetX = snapCenterX - gridCenterX;
		active.offsetY = snapCenterY - gridCenterY;

		lastGridW = grid[2];
		lastGridH = grid[3];

		repaint();
	}

	public void resetDisplayOffsetStretch() {
		ImageLayer active = activeLayer();

		if (active == null) {
			return;
		}

		active.offsetX = 0;
		active.offsetY = 0;

		if (getWidth() == 0 || getHeight() == 0) {
			active.scaleX = 1.0;
			active.scaleY = 1.0;
			repaint();
			return;
		}

		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);

		active.scaleX = (double) grid[2] / active.getImage().getWidth();
		active.scaleY = (double) grid[3] / active.getImage().getHeight();

		lastGridW = grid[2];
		lastGridH = grid[3];

		repaint();
	}

	public void copyDisplayStateFrom(ImagePreviewPanel other) {
		ImageLayer active = activeLayer();
		ImageLayer otherActive = other.activeLayer();

		if (active == null || otherActive == null) {
			return;
		}

		int[] srcGrid = other.computeGridBounds();
		int[] dstGrid = computeGridBounds();

		double normW = (otherActive.scaleX * otherActive.getImage().getWidth()) / srcGrid[2];
		double normH = (otherActive.scaleY * otherActive.getImage().getHeight()) / srcGrid[3];
		double normOffsetX = otherActive.offsetX / srcGrid[2];
		double normOffsetY = otherActive.offsetY / srcGrid[3];

		double dstScreenW = normW * dstGrid[2];
		double dstScreenH = normH * dstGrid[3];

		active.scaleX = dstScreenW / active.getImage().getWidth();
		active.scaleY = dstScreenH / active.getImage().getHeight();
		active.offsetX = normOffsetX * dstGrid[2];
		active.offsetY = normOffsetY * dstGrid[3];

		lastGridW = dstGrid[2];
		lastGridH = dstGrid[3];

		repaint();
	}

	/**
	 * Компонует все видимые слои в один {@link BufferedImage} размером сетки.
	 * Используется перед конвертацией.
	 *
	 * @param targetW ширина целевого изображения в пикселях
	 * @param targetH высота целевого изображения в пикселях
	 */
	public BufferedImage compositeImage(int targetW, int targetH) {
		BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = result.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setColor(gridBackgroundColor);
		g2.fillRect(0, 0, targetW, targetH);

		int[] grid = computeGridBounds();

		for (ImageLayer layer : layers) {
			if (!layer.isVisible()) {
				continue;
			}

			drawLayerToComposite(g2, layer, grid, targetW, targetH);
		}

		g2.dispose();

		return result;
	}

	/**
	 * Строит {@link CropSettings} для активного слоя.
	 * Используется для передачи в дизер при конвертации одного слоя.
	 *
	 * @param targetW ширина целевого изображения в пикселях (mapWidth * 128)
	 * @param targetH высота целевого изображения в пикселях (mapHeight * 128)
	 */
	public CropSettings buildCropSettings(int targetW, int targetH) {
		ImageLayer active = activeLayer();

		if (active == null || getWidth() == 0 || getHeight() == 0) {
			return new CropSettings(0, 0, 1.0, 1.0, gridBackgroundColor);
		}

		int[] grid = computeGridBounds();
		int gridW = grid[2];
		int gridH = grid[3];

		double baseScreenScale = Math.min(
			(double) gridW / active.getImage().getWidth(),
			(double) gridH / active.getImage().getHeight()
		);
		double userScaleX = active.scaleX / baseScreenScale;
		double userScaleY = active.scaleY / baseScreenScale;

		int offsetX = (int) Math.round(active.offsetX * ((double) targetW / gridW));
		int offsetY = (int) Math.round(active.offsetY * ((double) targetH / gridH));

		return CropSettings.fit(offsetX, offsetY, userScaleX, userScaleY, gridBackgroundColor);
	}

	// ── Отрисовка ──────────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int contentX = 4;
		int contentY = TITLE_HEIGHT;
		int contentW = w - 8;
		int contentH = h - TITLE_HEIGHT - 4;

		g2.setColor(bg());
		g2.fillRoundRect(0, 0, w, h, ARC, ARC);

		if (UpdatableRegistry.themeAnimating) {
			paintCachedContent(g2, contentX, contentY, contentW, contentH);
		}
		else {
			paintFreshContent(g2, contentX, contentY, contentW, contentH);
		}

		paintThemeOverlay(g2, contentX, contentY, contentW, contentH);

		g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
		g2.setColor(titleColor());
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(title, 10, fm.getAscent() + 5);

		ImageLayer active = activeLayer();

		if (active != null) {
			String sizeText = active.getImage().getWidth() + "×" + active.getImage().getHeight() + " px";
			int sizeX = w - fm.stringWidth(sizeText) - 36;
			g2.drawString(sizeText, sizeX, fm.getAscent() + 5);
		}

		Area cornerMask = new Area(new Rectangle(0, 0, w, h));
		cornerMask.subtract(new Area(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC)));
		g2.setColor(bg());
		g2.fill(cornerMask);

		g2.setColor(borderColor());
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

		g2.dispose();
	}

	/**
	 * Рисует поверх кеша элементы, зависящие от темы: placeholder и рамку активного слоя.
	 * Вызывается на каждом кадре — не кешируется, чтобы анимация темы работала корректно.
	 */
	private void paintThemeOverlay(Graphics2D g2, int x, int y, int w, int h) {
		if (layers.isEmpty()) {
			drawPlaceholder(g2, x, y, w, h);
			return;
		}

		if (draggable && activeLayer() != null) {
			int[] grid = computeGridBounds(x, y, w, h);
			drawActiveLayerBorder(g2, activeLayer(), grid[0], grid[1], grid[2], grid[3]);
		}
	}

	private void paintCachedContent(Graphics2D g2, int x, int y, int w, int h) {
		if (contentCache == null || contentCache.getWidth() != w || contentCache.getHeight() != h) {
			paintFreshContent(g2, x, y, w, h);
			return;
		}

		g2.drawImage(contentCache, x, y, null);
	}

	private void paintFreshContent(Graphics2D g2, int x, int y, int w, int h) {
		boolean hasActive = activeLayer() != null;
		boolean zoomed = hasActive
			&& Math.abs(activeLayer().scaleX) >= 1.0
			&& Math.abs(activeLayer().scaleY) >= 1.0;

		if (pixelPerfect && zoomed) {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		}
		else {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		}

		if (contentCache == null || contentCache.getWidth() != w || contentCache.getHeight() != h) {
			contentCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		}

		Graphics2D cacheG2 = contentCache.createGraphics();
		cacheG2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		cacheG2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION));
		cacheG2.setRenderingHint(RenderingHints.KEY_RENDERING, g2.getRenderingHint(RenderingHints.KEY_RENDERING));
		cacheG2.setComposite(AlphaComposite.Clear);
		cacheG2.fillRect(0, 0, w, h);
		cacheG2.setComposite(AlphaComposite.SrcOver);

		if (layers.isEmpty()) {
			cacheG2.setComposite(AlphaComposite.Clear);
			cacheG2.fillRect(0, 0, w, h);
			cacheG2.setComposite(AlphaComposite.SrcOver);
		}
		else {
			drawContent(cacheG2, 0, 0, w, h);
		}

		cacheG2.dispose();

		g2.drawImage(contentCache, x, y, null);
	}

	private void drawContent(Graphics2D g2, int x, int y, int w, int h) {
		int[] grid = computeGridBounds(x, y, w, h);
		int gridX = grid[0];
		int gridY = grid[1];
		int gridW = grid[2];
		int gridH = grid[3];

		if (draggable || showGridBackground) {
			g2.setColor(gridBackgroundColor);
			g2.fillRect(gridX, gridY, gridW, gridH);
		}

		for (ImageLayer layer : layers) {
			if (!layer.isVisible()) {
				continue;
			}

			drawLayerInGrid(g2, layer, gridX, gridY, gridW, gridH);
		}

		if (gridAlpha > 0f) {
			drawInvertedGridOverlay(g2, gridX, gridY, gridW, gridH);
		}

	}

	private void drawActiveLayerBorder(Graphics2D g2, ImageLayer layer, int gridX, int gridY, int gridW, int gridH) {
		int imgW = (int) Math.round(layer.getImage().getWidth() * Math.abs(layer.scaleX));
		int imgH = (int) Math.round(layer.getImage().getHeight() * Math.abs(layer.scaleY));
		int drawX = gridX + gridW / 2 - imgW / 2 + (int) Math.round(layer.offsetX);
		int drawY = gridY + gridH / 2 - imgH / 2 + (int) Math.round(layer.offsetY);

		g2.setColor(GuiApp.theme.getAccent());
		g2.setStroke(new BasicStroke(2f));
		g2.drawRect(drawX, drawY, imgW, imgH);
	}

	private void drawLayerInGrid(Graphics2D g2, ImageLayer layer, int gridX, int gridY, int gridW, int gridH) {
		BufferedImage source = resolveDisplayImage(layer);
		int imgW = (int) Math.round(layer.getImage().getWidth() * Math.abs(layer.scaleX));
		int imgH = (int) Math.round(layer.getImage().getHeight() * Math.abs(layer.scaleY));

		int gridCenterX = gridX + gridW / 2;
		int gridCenterY = gridY + gridH / 2;

		int drawX = gridCenterX - imgW / 2 + (int) Math.round(layer.offsetX);
		int drawY = gridCenterY - imgH / 2 + (int) Math.round(layer.offsetY);

		int visX1 = Math.max(drawX, gridX);
		int visY1 = Math.max(drawY, gridY);
		int visX2 = Math.min(drawX + imgW, gridX + gridW);
		int visY2 = Math.min(drawY + imgH, gridY + gridH);

		if (visX1 >= visX2 || visY1 >= visY2) {
			return;
		}

		double srcScaleX = (double) source.getWidth() / imgW;
		double srcScaleY = (double) source.getHeight() / imgH;

		int sx1 = (int) Math.round((visX1 - drawX) * srcScaleX);
		int sy1 = (int) Math.round((visY1 - drawY) * srcScaleY);
		int sx2 = (int) Math.round((visX2 - drawX) * srcScaleX);
		int sy2 = (int) Math.round((visY2 - drawY) * srcScaleY);

		if (layer.scaleX < 0) {
			int tmp = sx1;
			sx1 = source.getWidth() - sx2;
			sx2 = source.getWidth() - tmp;
		}

		if (layer.scaleY < 0) {
			int tmp = sy1;
			sy1 = source.getHeight() - sy2;
			sy2 = source.getHeight() - tmp;
		}

		g2.drawImage(source, visX1, visY1, visX2, visY2, sx1, sy1, sx2, sy2, null);
	}

	private void drawLayerToComposite(
		Graphics2D g2,
		ImageLayer layer,
		int[] grid,
		int targetW,
		int targetH
	) {
		int gridW = grid[2];
		int gridH = grid[3];

		double scaleToTarget = (double) targetW / gridW;

		int imgW = (int) Math.round(layer.getImage().getWidth() * Math.abs(layer.scaleX) * scaleToTarget);
		int imgH = (int) Math.round(layer.getImage().getHeight() * Math.abs(layer.scaleY) * scaleToTarget);

		int centerX = targetW / 2 + (int) Math.round(layer.offsetX * scaleToTarget);
		int centerY = targetH / 2 + (int) Math.round(layer.offsetY * scaleToTarget);

		int drawX = centerX - imgW / 2;
		int drawY = centerY - imgH / 2;

		g2.drawImage(layer.getImage(), drawX, drawY, imgW, imgH, null);
	}

	private BufferedImage resolveDisplayImage(ImageLayer layer) {
		if (blurRadius <= 0.0 || layer != activeLayer()) {
			return layer.getImage();
		}

		return applyGaussianBlur(layer.getImage(), blurRadius);
	}

	private void drawPlaceholder(Graphics2D g2, int x, int y, int w, int h) {
		g2.setColor(placeholderBg());
		g2.fillRoundRect(x, y, w, h, 6, 6);

		if (!draggable) {
			return;
		}

		g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
		g2.setColor(placeholderText());
		String placeholder = UpdatableRegistry.translate("preview.placeholder");
		FontMetrics fm = g2.getFontMetrics();
		int textX = x + (w - fm.stringWidth(placeholder)) / 2;
		int textY = y + h / 2 + fm.getAscent() / 2;
		g2.drawString(placeholder, textX, textY);
	}

	/**
	 * Накладывает инвертированную сетку поверх уже нарисованного контента.
	 */
	private void drawInvertedGridOverlay(Graphics2D g2, int gridX, int gridY, int gridW, int gridH) {
		BufferedImage base = new BufferedImage(gridW, gridH, BufferedImage.TYPE_INT_RGB);
		Graphics2D bg = base.createGraphics();
		bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION));
		bg.setRenderingHint(RenderingHints.KEY_RENDERING, g2.getRenderingHint(RenderingHints.KEY_RENDERING));

		if (draggable || showGridBackground) {
			bg.setColor(gridBackgroundColor);
			bg.fillRect(0, 0, gridW, gridH);
		}

		bg.translate(-gridX, -gridY);

		for (ImageLayer layer : layers) {
			if (!layer.isVisible()) {
				continue;
			}

			drawLayerInGrid(bg, layer, gridX, gridY, gridW, gridH);
		}

		bg.dispose();

		BufferedImage withGrid = new BufferedImage(gridW, gridH, BufferedImage.TYPE_INT_RGB);
		Graphics2D wg = withGrid.createGraphics();
		wg.drawImage(base, 0, 0, null);
		wg.setXORMode(Color.WHITE);
		wg.setColor(Color.BLACK);
		wg.setStroke(new BasicStroke(gridStrokeWidth));
		drawGridLines(wg, 0, 0, gridW, gridH, (double) gridW / mapsX, (double) gridH / mapsY);
		wg.dispose();

		int[] basePixels = base.getRGB(0, 0, gridW, gridH, null, 0, gridW);
		int[] gridPixels = withGrid.getRGB(0, 0, gridW, gridH, null, 0, gridW);
		int[] maskPixels = new int[basePixels.length];

		for (int i = 0; i < basePixels.length; i++) {
			int diff = basePixels[i] ^ gridPixels[i];
			maskPixels[i] = diff == 0 ? 0x00000000 : (gridPixels[i] | 0xFF000000);
		}

		BufferedImage gridMask = new BufferedImage(gridW, gridH, BufferedImage.TYPE_INT_ARGB);
		gridMask.setRGB(0, 0, gridW, gridH, maskPixels, 0, gridW);

		Composite saved = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, gridAlpha));
		g2.drawImage(gridMask, gridX, gridY, null);
		g2.setComposite(saved);
	}

	private void drawGridLines(Graphics2D g2, int drawX, int drawY, int drawWidth, int drawHeight, double cellW, double cellH) {
		for (int col = 1; col < mapsX; col++) {
			int lineX = drawX + (int) (col * cellW);
			g2.drawLine(lineX, drawY, lineX, drawY + drawHeight);
		}

		for (int row = 1; row < mapsY; row++) {
			int lineY = drawY + (int) (row * cellH);
			g2.drawLine(drawX, lineY, drawX + drawWidth, lineY);
		}

		g2.drawRect(drawX, drawY, drawWidth - 1, drawHeight - 1);
	}

	// ── Вспомогательные методы ─────────────────────────────────────────────────

	private ImageLayer activeLayer() {
		if (activeLayerIndex < 0 || activeLayerIndex >= layers.size()) {
			return null;
		}

		return layers.get(activeLayerIndex);
	}

	private void notifyLayersChanged() {
		if (onLayersChanged != null) {
			onLayersChanged.run();
		}
	}

	/**
	 * Ищет верхний видимый слой, содержащий точку (mx, my) в экранных координатах панели.
	 * Обход идёт от последнего слоя к первому (верхний по z-order — последний в списке).
	 * Возвращает индекс найденного слоя или -1, если ни один слой не содержит точку.
	 */
	private int findLayerAt(int mx, int my) {
		int[] grid = computeGridBounds();
		int gridX = grid[0];
		int gridY = grid[1];
		int gridW = grid[2];
		int gridH = grid[3];

		int gridCenterX = gridX + gridW / 2;
		int gridCenterY = gridY + gridH / 2;

		for (int i = layers.size() - 1; i >= 0; i--) {
			ImageLayer layer = layers.get(i);

			if (!layer.isVisible()) {
				continue;
			}

			int imgW = (int) Math.round(layer.getImage().getWidth() * Math.abs(layer.scaleX));
			int imgH = (int) Math.round(layer.getImage().getHeight() * Math.abs(layer.scaleY));
			int drawX = gridCenterX - imgW / 2 + (int) Math.round(layer.offsetX);
			int drawY = gridCenterY - imgH / 2 + (int) Math.round(layer.offsetY);

			if (mx >= drawX && mx <= drawX + imgW && my >= drawY && my <= drawY + imgH) {
				return i;
			}
		}

		return -1;
	}

	private boolean isInsideActiveLayer(int mx, int my) {
		double[] edges = computeActiveImageEdges();

		if (edges == null) {
			return false;
		}

		return mx >= edges[0] - RESIZE_HIT_ZONE
			&& mx <= edges[2] + RESIZE_HIT_ZONE
			&& my >= edges[1] - RESIZE_HIT_ZONE
			&& my <= edges[3] + RESIZE_HIT_ZONE;
	}

	private void fitLayerToGrid(ImageLayer layer, int gridW, int gridH) {
		if (gridW == 0 || gridH == 0) {
			return;
		}

		double scaleX = (double) gridW / layer.getImage().getWidth();
		double scaleY = (double) gridH / layer.getImage().getHeight();
		layer.scaleX = Math.min(scaleX, scaleY);
		layer.scaleY = layer.scaleX;
		layer.offsetX = 0;
		layer.offsetY = 0;
	}

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

	private int[] computeGridBounds() {
		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		return computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);
	}

	private double[] computeActiveImageEdges() {
		ImageLayer active = activeLayer();

		if (active == null) {
			return null;
		}

		int[] grid = computeGridBounds();
		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		double imgW = active.getImage().getWidth() * Math.abs(active.scaleX);
		double imgH = active.getImage().getHeight() * Math.abs(active.scaleY);
		double centerX = gridCenterX + active.offsetX;
		double centerY = gridCenterY + active.offsetY;

		return new double[]{
			centerX - imgW / 2,
			centerY - imgH / 2,
			centerX + imgW / 2,
			centerY + imgH / 2
		};
	}

	private int getResizeCursorType(int mx, int my) {
		double[] edges = computeActiveImageEdges();

		if (edges == null) {
			return Cursor.DEFAULT_CURSOR;
		}

		double left = edges[0];
		double top = edges[1];
		double right = edges[2];
		double bottom = edges[3];

		boolean nearLeft = Math.abs(mx - left) <= RESIZE_HIT_ZONE;
		boolean nearRight = Math.abs(mx - right) <= RESIZE_HIT_ZONE;
		boolean nearTop = Math.abs(my - top) <= RESIZE_HIT_ZONE;
		boolean nearBottom = Math.abs(my - bottom) <= RESIZE_HIT_ZONE;

		boolean insideX = mx >= left - RESIZE_HIT_ZONE && mx <= right + RESIZE_HIT_ZONE;
		boolean insideY = my >= top - RESIZE_HIT_ZONE && my <= bottom + RESIZE_HIT_ZONE;

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

		if (nearLeft && insideY) {
			return Cursor.W_RESIZE_CURSOR;
		}

		if (nearRight && insideY) {
			return Cursor.E_RESIZE_CURSOR;
		}

		if (nearTop && insideX) {
			return Cursor.N_RESIZE_CURSOR;
		}

		if (nearBottom && insideX) {
			return Cursor.S_RESIZE_CURSOR;
		}

		return Cursor.DEFAULT_CURSOR;
	}

	private void setupMouseListeners() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!draggable) {
					return;
				}

				requestFocusInWindow();

				// Рамка активного слоя имеет абсолютный приоритет:
				// если клик внутри bounding box активного слоя — не переключаем слой
				if (activeLayer() != null && isInsideActiveLayer(e.getX(), e.getY())) {
					int cursor = getResizeCursorType(e.getX(), e.getY());

					if (cursor != Cursor.DEFAULT_CURSOR) {
						resizing = true;
						resizeCursorType = cursor;
						double[] edges = computeActiveImageEdges();
						resizeStartLeft = edges[0];
						resizeStartRight = edges[2];
						resizeStartTop = edges[1];
						resizeStartBottom = edges[3];
					}
					else {
						dragStartMouseX = e.getX();
						dragStartMouseY = e.getY();
						dragStartOffsetX = activeLayer().offsetX;
						dragStartOffsetY = activeLayer().offsetY;
					}

					return;
				}

				int clicked = findLayerAt(e.getX(), e.getY());

				if (clicked >= 0 && clicked != activeLayerIndex) {
					activeLayerIndex = clicked;
					repaint();

					if (onActiveLayerChanged != null) {
						onActiveLayerChanged.accept(activeLayerIndex);
					}
				}

				if (activeLayer() == null) {
					return;
				}

				dragStartMouseX = e.getX();
				dragStartMouseY = e.getY();
				dragStartOffsetX = activeLayer().offsetX;
				dragStartOffsetY = activeLayer().offsetY;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				resizing = false;
				resizeCursorType = Cursor.DEFAULT_CURSOR;
				updateCursor(e.getX(), e.getY());
			}
		});

		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (!draggable || activeLayer() == null) {
					return;
				}

				if (resizing) {
					handleResize(e.getX(), e.getY());
				}
				else {
					handleDrag(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (draggable) {
					updateCursor(e.getX(), e.getY());
				}
			}
		});

		addMouseWheelListener(e -> {
			if (draggable && activeLayer() != null) {
				handleZoom(e.getWheelRotation(), e.getX(), e.getY());
			}
		});
	}

	private void setupKeyBindings() {
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), "left-fast");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK), "right-fast");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), "up-fast");
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), "down-fast");

		bindArrow("left", -ARROW_STEP, 0);
		bindArrow("right", ARROW_STEP, 0);
		bindArrow("up", 0, -ARROW_STEP);
		bindArrow("down", 0, ARROW_STEP);
		bindArrow("left-fast", -ARROW_STEP_FAST, 0);
		bindArrow("right-fast", ARROW_STEP_FAST, 0);
		bindArrow("up-fast", 0, -ARROW_STEP_FAST);
		bindArrow("down-fast", 0, ARROW_STEP_FAST);
	}

	private void setupResizeListener() {
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				rescaleOnPanelResize();
			}
		});
	}

	private void rescaleOnPanelResize() {
		int contentW = getWidth() - 8;
		int contentH = getHeight() - TITLE_HEIGHT - 4;
		int[] grid = computeGridBounds(4, TITLE_HEIGHT, contentW, contentH);
		int newGridW = grid[2];
		int newGridH = grid[3];

		if (lastGridW == 0 || lastGridH == 0) {
			lastGridW = newGridW;
			lastGridH = newGridH;
			return;
		}

		double ratioW = (double) newGridW / lastGridW;
		double ratioH = (double) newGridH / lastGridH;

		for (ImageLayer layer : layers) {
			layer.scaleX *= ratioW;
			layer.scaleY *= ratioH;
			layer.offsetX *= ratioW;
			layer.offsetY *= ratioH;
		}

		lastGridW = newGridW;
		lastGridH = newGridH;

		repaint();
	}

	private void bindArrow(String key, int dx, int dy) {
		getActionMap().put(key, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ImageLayer active = activeLayer();

				if (active == null) {
					return;
				}

				active.offsetX += dx;
				active.offsetY += dy;

				if (onOffsetChanged != null) {
					onOffsetChanged.accept(dx, dy);
				}

				repaint();
			}
		});
	}

	private void updateCursor(int mx, int my) {
		int cursor = activeLayer() != null
			? getResizeCursorType(mx, my)
			: Cursor.DEFAULT_CURSOR;

		setCursor(Cursor.getPredefinedCursor(cursor));
	}

	private void handleDrag(int mx, int my) {
		ImageLayer active = activeLayer();
		int[] grid = computeGridBounds();

		double rawOffsetX = dragStartOffsetX + (mx - dragStartMouseX);
		double rawOffsetY = dragStartOffsetY + (my - dragStartMouseY);

		if (snapEnabled) {
			double[] snapped = applyEdgeSnap(active, grid, rawOffsetX, rawOffsetY);
			active.offsetX = snapped[0];
			active.offsetY = snapped[1];
		}
		else {
			active.offsetX = rawOffsetX;
			active.offsetY = rawOffsetY;
		}

		if (onOffsetChanged != null) {
			onOffsetChanged.accept((int) active.offsetX, (int) active.offsetY);
		}

		repaint();
	}

	private double[] applyEdgeSnap(ImageLayer layer, int[] grid, double rawOffsetX, double rawOffsetY) {
		double imgW = layer.getImage().getWidth() * Math.abs(layer.scaleX);
		double imgH = layer.getImage().getHeight() * Math.abs(layer.scaleY);

		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		double imgLeft = gridCenterX + rawOffsetX - imgW / 2;
		double imgRight = gridCenterX + rawOffsetX + imgW / 2;
		double imgTop = gridCenterY + rawOffsetY - imgH / 2;
		double imgBottom = gridCenterY + rawOffsetY + imgH / 2;

		double gridLeft = grid[0];
		double gridRight = grid[0] + grid[2];
		double gridTop = grid[1];
		double gridBottom = grid[1] + grid[3];

		List<Double> snapLinesX = collectSnapLinesX(grid);
		List<Double> snapLinesY = collectSnapLinesY(grid);

		double snappedX = rawOffsetX;
		double snappedY = rawOffsetY;

		Double snapLeft = snapToNearest(imgLeft, snapLinesX);
		Double snapRight = snapToNearest(imgRight, snapLinesX);
		Double snapTop = snapToNearest(imgTop, snapLinesY);
		Double snapBottom = snapToNearest(imgBottom, snapLinesY);

		if (snapLeft != null && (snapRight == null || Math.abs(imgLeft - snapLeft) <= Math.abs(imgRight - snapRight))) {
			snappedX = snapLeft - gridCenterX + imgW / 2;
		}
		else if (snapRight != null) {
			snappedX = snapRight - gridCenterX - imgW / 2;
		}

		if (snapTop != null && (snapBottom == null || Math.abs(imgTop - snapTop) <= Math.abs(imgBottom - snapBottom))) {
			snappedY = snapTop - gridCenterY + imgH / 2;
		}
		else if (snapBottom != null) {
			snappedY = snapBottom - gridCenterY - imgH / 2;
		}

		// Снэп к центру
		if (Math.abs(rawOffsetX) <= SNAP_THRESHOLD) {
			snappedX = 0;
		}

		if (Math.abs(rawOffsetY) <= SNAP_THRESHOLD) {
			snappedY = 0;
		}

		return new double[]{snappedX, snappedY};
	}

	private List<Double> collectSnapLinesX(int[] grid) {
		List<Double> lines = new ArrayList<>();
		double cellW = (double) grid[2] / mapsX;

		for (int i = 0; i <= mapsX; i++) {
			lines.add(grid[0] + i * cellW);
		}

		return lines;
	}

	private List<Double> collectSnapLinesY(int[] grid) {
		List<Double> lines = new ArrayList<>();
		double cellH = (double) grid[3] / mapsY;

		for (int i = 0; i <= mapsY; i++) {
			lines.add(grid[1] + i * cellH);
		}

		return lines;
	}

	private Double snapToNearest(double value, List<Double> lines) {
		for (double line : lines) {
			if (Math.abs(value - line) <= SNAP_THRESHOLD) {
				return line;
			}
		}

		return null;
	}

	private void handleResize(int mx, int my) {
		ImageLayer active = activeLayer();
		int[] grid = computeGridBounds();

		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		boolean resizeLeft = resizeCursorType == Cursor.W_RESIZE_CURSOR
			|| resizeCursorType == Cursor.NW_RESIZE_CURSOR
			|| resizeCursorType == Cursor.SW_RESIZE_CURSOR;
		boolean resizeRight = resizeCursorType == Cursor.E_RESIZE_CURSOR
			|| resizeCursorType == Cursor.NE_RESIZE_CURSOR
			|| resizeCursorType == Cursor.SE_RESIZE_CURSOR;
		boolean resizeTop = resizeCursorType == Cursor.N_RESIZE_CURSOR
			|| resizeCursorType == Cursor.NW_RESIZE_CURSOR
			|| resizeCursorType == Cursor.NE_RESIZE_CURSOR;
		boolean resizeBottom = resizeCursorType == Cursor.S_RESIZE_CURSOR
			|| resizeCursorType == Cursor.SW_RESIZE_CURSOR
			|| resizeCursorType == Cursor.SE_RESIZE_CURSOR;

		double newLeft = resizeLeft ? mx : resizeStartLeft;
		double newRight = resizeRight ? mx : resizeStartRight;
		double newTop = resizeTop ? my : resizeStartTop;
		double newBottom = resizeBottom ? my : resizeStartBottom;

		if (snapEnabled) {
			List<Double> snapLinesX = collectSnapLinesX(grid);
			List<Double> snapLinesY = collectSnapLinesY(grid);

			if (resizeLeft) {
				Double snapped = snapToNearest(newLeft, snapLinesX);
				if (snapped != null) {
					newLeft = snapped;
				}
			}

			if (resizeRight) {
				Double snapped = snapToNearest(newRight, snapLinesX);
				if (snapped != null) {
					newRight = snapped;
				}
			}

			if (resizeTop) {
				Double snapped = snapToNearest(newTop, snapLinesY);
				if (snapped != null) {
					newTop = snapped;
				}
			}

			if (resizeBottom) {
				Double snapped = snapToNearest(newBottom, snapLinesY);
				if (snapped != null) {
					newBottom = snapped;
				}
			}
		}

		// Знак scale инвертируется при пересечении краёв (зеркалирование).
		// Размер всегда берётся как абсолютная разница, центр — как среднее реальных краёв.
		double rawW = newRight - newLeft;
		double rawH = newBottom - newTop;

		double signX = rawW < 0 ? -Math.signum(active.scaleX) : Math.signum(active.scaleX);
		double signY = rawH < 0 ? -Math.signum(active.scaleY) : Math.signum(active.scaleY);

		if (signX == 0) {
			signX = rawW < 0 ? -1 : 1;
		}

		if (signY == 0) {
			signY = rawH < 0 ? -1 : 1;
		}

		double absScaleX = Math.abs(rawW) / active.getImage().getWidth();
		double absScaleY = Math.abs(rawH) / active.getImage().getHeight();

		active.scaleX = signX * Math.max(SCALE_MIN, Math.min(SCALE_MAX, absScaleX));
		active.scaleY = signY * Math.max(SCALE_MIN, Math.min(SCALE_MAX, absScaleY));

		double newCenterX = (Math.min(newLeft, newRight) + Math.max(newLeft, newRight)) / 2;
		double newCenterY = (Math.min(newTop, newBottom) + Math.max(newTop, newBottom)) / 2;
		active.offsetX = newCenterX - gridCenterX;
		active.offsetY = newCenterY - gridCenterY;

		if (onOffsetChanged != null) {
			onOffsetChanged.accept((int) active.offsetX, (int) active.offsetY);
		}

		repaint();
	}

	private void handleZoom(int wheelRotation, int mx, int my) {
		ImageLayer active = activeLayer();
		int[] grid = computeGridBounds();

		double factor = wheelRotation > 0 ? 1.0 - ZOOM_STEP : 1.0 + ZOOM_STEP;
		double newScaleX = clampSigned(active.scaleX * factor, SCALE_MIN, SCALE_MAX);
		double newScaleY = clampSigned(active.scaleY * factor, SCALE_MIN, SCALE_MAX);

		double gridCenterX = grid[0] + grid[2] / 2.0;
		double gridCenterY = grid[1] + grid[3] / 2.0;

		double imgCenterX = gridCenterX + active.offsetX;
		double imgCenterY = gridCenterY + active.offsetY;

		double pivotX = mx - imgCenterX;
		double pivotY = my - imgCenterY;

		double scaleRatioX = newScaleX / active.scaleX;
		double scaleRatioY = newScaleY / active.scaleY;

		active.scaleX = newScaleX;
		active.scaleY = newScaleY;
		active.offsetX += pivotX * (1 - scaleRatioX);
		active.offsetY += pivotY * (1 - scaleRatioY);

		if (onOffsetChanged != null) {
			onOffsetChanged.accept((int) active.offsetX, (int) active.offsetY);
		}

		repaint();
	}

	private double clampSigned(double value, double min, double max) {
		double sign = value < 0 ? -1 : 1;
		return sign * Math.max(min, Math.min(max, Math.abs(value)));
	}

	@Override
	public Insets getInsets() {
		return new Insets(TITLE_HEIGHT + 4, 4, 4, 4);
	}

	private BufferedImage applyGaussianBlur(BufferedImage src, double radius) {
		float[] kernel = buildGaussianKernel((int) Math.ceil(radius));
		int size = kernel.length;
		BufferedImage tmp = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		ConvolveOp hBlur = new ConvolveOp(new Kernel(size, 1, kernel), ConvolveOp.EDGE_NO_OP, null);
		ConvolveOp vBlur = new ConvolveOp(new Kernel(1, size, kernel), ConvolveOp.EDGE_NO_OP, null);
		hBlur.filter(src, tmp);
		return vBlur.filter(tmp, null);
	}

	private float[] buildGaussianKernel(int radius) {
		int size = radius * 2 + 1;
		float[] kernel = new float[size];
		double sigma = radius / 3.0;
		double sum = 0;

		for (int i = 0; i < size; i++) {
			double x = i - radius;
			kernel[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
			sum += kernel[i];
		}

		for (int i = 0; i < size; i++) {
			kernel[i] /= (float) sum;
		}

		return kernel;
	}
}
