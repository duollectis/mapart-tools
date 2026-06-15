package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Горизонтальная панель миниатюр слоёв с инерционным скроллом и drag-to-reorder.
 * <p>
 * Взаимодействие:
 * <ul>
 *   <li>Клик — выделяет слой как активный, сбрасывает мультиселект</li>
 *   <li>Ctrl+Клик — добавляет/убирает слой из мультиселекта</li>
 *   <li>Shift+Клик — выделяет диапазон от последнего кликнутого до текущего</li>
 *   <li>Del — удаляет все выделенные слои</li>
 *   <li>Глазик (левый верхний угол) — переключает видимость слоя с анимацией ч/б</li>
 *   <li>Крестик (правый верхний угол) — удаляет слой</li>
 *   <li>Drag по горизонтали — меняет порядок слоёв с анимацией сдвига</li>
 * </ul>
 */
public class LayerListPanel extends JPanel {

	private static final int CARD_SIZE = 56;
	private static final int CARD_GAP = 6;
	private static final int PADDING = 4;
	private static final int ARC = 10;
	private static final int ICON_BTN_SIZE = 18;
	private static final int ICON_PAD = 2;
	private static final int DRAG_THRESHOLD = 6;
	private static final float GHOST_SCALE_TARGET = 0.78f;
	private static final int CARD_STRIDE = CARD_SIZE + CARD_GAP;
	private static final int ANIM_DURATION_MS = 180;
	private static final int GHOST_ANIM_DURATION_MS = 140;
	private static final int VISIBILITY_ANIM_DURATION_MS = 200;

	private static final ColorConvertOp GRAYSCALE_OP = new ColorConvertOp(
		ColorSpace.getInstance(ColorSpace.CS_GRAY), null
	);

	private static Color bgDeep() { return GuiApp.theme.getBgDeep(); }
	private static Color border() { return GuiApp.theme.getBorder(); }
	private static Color accent() { return GuiApp.theme.getAccent(); }
	private static Color warn() { return GuiApp.theme.getWarn(); }

	private final ImagePreviewPanel preview;
	private final MainWindow mainWindow;

	private final JPanel itemsContainer;
	private final InertialScrollPane scrollPane;
	private final JButton mergeButton;

	// Мультиселект: хранит realIndex всех выделенных слоёв
	private final Set<Integer> selectedIndices = new LinkedHashSet<>();
	// Якорь для Shift+Click диапазона
	private int lastClickedRealIndex = -1;

	// Drag-состояние (координаты в системе LayerListPanel)
	private boolean dragging;
	private int dragSourceIndex = -1;
	private int dragTargetIndex = -1;
	private int dragMouseX;
	private int dragMouseY;
	private int dragStartScreenX;

	// Анимация масштаба ghost-карточки при захвате/отпускании
	private float ghostScale = GHOST_SCALE_TARGET;
	private Timer ghostScaleTimer;
	private Timer ghostSnapXTimer;
	private Timer ghostSnapYTimer;

	private IntConsumer onActiveChanged;
	private Consumer<Integer> onLayerRemoved;
	private Runnable onLayersReordered;

	public LayerListPanel(ImagePreviewPanel preview, MainWindow mainWindow) {
		this.preview = preview;
		this.mainWindow = mainWindow;

		setLayout(new BorderLayout(0, 2));
		setOpaque(false);

		itemsContainer = new JPanel(null) {
			@Override
			protected void paintChildren(Graphics g) {
				super.paintChildren(g);
				paintRemovingCards(g);
			}
		};
		itemsContainer.setOpaque(false);

		scrollPane = new InertialScrollPane(itemsContainer, true);

		mergeButton = new RippleButton("", mainWindow);
		UpdatableRegistry.registerLang("btn.merge_layers", mergeButton::setText);
		mergeButton.addActionListener(e -> mergeSelectedLayers());
		mergeButton.setVisible(false);

		add(mergeButton, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);

		preview.setOnLayersChanged(this::refresh);
		preview.setOnActiveLayerChanged(idx -> refreshActiveState());
	}

	public void setOnActiveChanged(IntConsumer callback) {
		onActiveChanged = callback;
	}

	public void setOnLayerRemoved(Consumer<Integer> callback) {
		onLayerRemoved = callback;
	}

	public void setOnLayersReordered(Runnable callback) {
		onLayersReordered = callback;
	}

	/** Перестраивает карточки из текущего состояния превью. */
	public void refresh() {
		List<ImageLayer> layers = preview.getLayers();
		int count = layers.size();

		// Очищаем невалидные индексы из мультиселекта
		selectedIndices.removeIf(idx -> idx < 0 || idx >= count);
		syncMergeButton();

		itemsContainer.removeAll();

		for (int i = 0; i < count; i++) {
			// UI-порядок обратный: последний слой — первый в списке
			int realIndex = count - 1 - i;
			ImageLayer layer = layers.get(realIndex);
			boolean active = realIndex == preview.getActiveLayerIndex();
			boolean selected = selectedIndices.contains(realIndex);

			CardPanel card = new CardPanel(layer, realIndex, active, selected);
			int x = PADDING + i * CARD_STRIDE;
			card.setBounds(x, PADDING, CARD_SIZE, CARD_SIZE);
			card.animX = x;
			itemsContainer.add(card);
		}

		updateContainerPreferredSize(count);
		itemsContainer.revalidate();
		itemsContainer.repaint();
	}

	/**
	 * Ghost-оверлей рисуется поверх всего содержимого панели во время drag.
	 */
	@Override
	protected void paintChildren(Graphics g) {
		super.paintChildren(g);

		if (!dragging) {
			return;
		}

		ImageLayer ghostLayer = null;

		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card && card.isDragPlaceholder()) {
				ghostLayer = card.getLayer();
				break;
			}
		}

		if (ghostLayer == null) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.clipRect(0, 0, getWidth(), getHeight());

		drawGhost(g2, ghostLayer);

		g2.dispose();
	}

	private void drawGhost(Graphics2D g2, ImageLayer layer) {
		int currentSize = Math.round(CARD_SIZE * ghostScale);
		int ghostX = dragMouseX - currentSize / 2;
		int ghostY = dragMouseY - currentSize / 2;

		g2.setColor(new Color(0, 0, 0, 60));
		g2.fillRoundRect(ghostX + 3, ghostY + 6, currentSize, currentSize, ARC, ARC);

		int inner = ghostX + 1;
		int innerY = ghostY + 1;
		int innerSize = currentSize - 2;

		g2.setColor(bgDeep());
		g2.fillRoundRect(inner, innerY, innerSize, innerSize, ARC, ARC);

		BufferedImage img = layer.getImage();

		if (img != null) {
			Shape clip = new RoundRectangle2D.Float(inner, innerY, innerSize, innerSize, ARC, ARC);
			Shape prevClip = g2.getClip();
			g2.clip(clip);
			g2.drawImage(img, inner, innerY, innerSize, innerSize, null);
			g2.setClip(prevClip);
		}

		g2.setColor(border());
		g2.setStroke(new BasicStroke(1.5f));
		g2.drawRoundRect(inner, innerY, innerSize, innerSize, ARC, ARC);
	}

	private void startDrag(int uiIndex, int screenX, int panelX, int panelY) {
		dragSourceIndex = uiIndex;
		dragTargetIndex = uiIndex;
		dragStartScreenX = screenX;
		dragMouseX = panelX;
		dragMouseY = panelY;
		dragging = false;
	}

	private void animateGhostScale(float from, float to, Runnable onDone) {
		if (ghostScaleTimer != null) {
			ghostScaleTimer.stop();
		}

		ghostScaleTimer = UiAnimator.animateFloat(from, to, GHOST_ANIM_DURATION_MS, scale -> {
			ghostScale = scale;
			repaint();
		}, onDone);
	}

	private void updateDrag(int screenX, int panelX, int panelY) {
		dragMouseX = panelX;
		dragMouseY = panelY;

		if (!dragging && Math.abs(screenX - dragStartScreenX) > DRAG_THRESHOLD) {
			dragging = true;
			ghostScale = 1.0f;
			animateGhostScale(1.0f, GHOST_SCALE_TARGET, null);
			updateCardPlaceholders();
		}

		if (dragging) {
			int newTarget = uiIndexAtPanelX(panelX);

			if (newTarget != dragTargetIndex) {
				dragTargetIndex = newTarget;
				reorderCardsAnimated();
			}

			repaint();
		}
	}

	private void endDrag() {
		if (!dragging) {
			dragSourceIndex = -1;
			dragTargetIndex = -1;
			return;
		}

		dragSourceIndex = -1;
		dragTargetIndex = -1;

		Point snapTarget = findPlaceholderCenter();

		if (snapTarget == null) {
			dragging = false;
			commitDrag();
			updateCardPlaceholders();
			repaint();
			return;
		}

		int fromX = dragMouseX;
		int fromY = dragMouseY;
		int toX = snapTarget.x;
		int toY = snapTarget.y;

		if (ghostSnapXTimer != null) {
			ghostSnapXTimer.stop();
		}

		if (ghostSnapYTimer != null) {
			ghostSnapYTimer.stop();
		}

		ghostSnapXTimer = UiAnimator.animateFloat(fromX, toX, GHOST_ANIM_DURATION_MS, x -> {
			dragMouseX = Math.round(x);
			repaint();
		}, null);

		ghostSnapYTimer = UiAnimator.animateFloat(fromY, toY, GHOST_ANIM_DURATION_MS, y -> {
			dragMouseY = Math.round(y);
			repaint();
		}, null);

		animateGhostScale(ghostScale, 1.0f, () -> {
			dragging = false;
			commitDrag();
			updateCardPlaceholders();
			repaint();
		});
	}

	/**
	 * Возвращает центр placeholder-карточки в координатах LayerListPanel.
	 */
	private Point findPlaceholderCenter() {
		Component[] components = itemsContainer.getComponents();
		int uiIndex = 0;

		for (Component comp : components) {
			if (comp instanceof CardPanel card) {
				if (card.isDragPlaceholder()) {
					int targetX = PADDING + uiIndex * CARD_STRIDE + CARD_SIZE / 2;
					int scrollX = scrollPane.getHorizontalScrollBar().getValue();
					int panelX = targetX - scrollX + scrollPane.getX();
					int panelY = scrollPane.getY() + PADDING + CARD_SIZE / 2;
					return new Point(panelX, panelY);
				}

				uiIndex++;
			}
		}

		return null;
	}

	private void updateCardPlaceholders() {
		Component[] components = itemsContainer.getComponents();
		int cardIndex = 0;

		for (Component comp : components) {
			if (comp instanceof CardPanel card) {
				card.setDragPlaceholder(dragging && cardIndex == dragSourceIndex);
				cardIndex++;
			}
		}
	}

	/**
	 * Переставляет карточки в itemsContainer при изменении dragTargetIndex
	 * и запускает анимацию плавного сдвига для каждой карточки.
	 */
	private void reorderCardsAnimated() {
		List<ImageLayer> layers = preview.getLayers();
		int count = layers.size();

		if (dragSourceIndex < 0 || dragTargetIndex < 0) {
			return;
		}

		List<CardPanel> cards = collectCards();

		if (cards.size() != count) {
			return;
		}

		for (CardPanel card : cards) {
			card.animX = card.getX();
		}

		CardPanel moved = cards.remove(dragSourceIndex);
		cards.add(Math.clamp(dragTargetIndex, 0, cards.size()), moved);

		itemsContainer.removeAll();

		for (int i = 0; i < cards.size(); i++) {
			CardPanel card = cards.get(i);
			int targetX = PADDING + i * CARD_STRIDE;

			itemsContainer.add(card);
			card.setBounds(card.animX, PADDING, CARD_SIZE, CARD_SIZE);
			animateCardToX(card, card.animX, targetX);
		}

		dragSourceIndex = dragTargetIndex;

		itemsContainer.revalidate();
	}

	private void animateCardToX(CardPanel card, int fromX, int toX) {
		if (card.moveTimer != null) {
			card.moveTimer.stop();
		}

		if (fromX == toX) {
			card.animX = toX;
			return;
		}

		card.moveTimer = UiAnimator.animateFloat(fromX, toX, ANIM_DURATION_MS, x -> {
			card.animX = Math.round(x);
			card.setBounds(card.animX, PADDING, CARD_SIZE, CARD_SIZE);
			itemsContainer.repaint();
		}, null);
	}

	private List<CardPanel> collectCards() {
		List<CardPanel> cards = new ArrayList<>();

		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card) {
				cards.add(card);
			}
		}

		return cards;
	}

	private void updateContainerPreferredSize(int count) {
		int totalW = count > 0
				? PADDING * 2 + count * CARD_SIZE + (count - 1) * CARD_GAP
				: PADDING * 2;
		int totalH = CARD_SIZE + PADDING * 2;
		itemsContainer.setPreferredSize(new Dimension(totalW, totalH));
	}

	private int uiIndexAtPanelX(int panelX) {
		int count = preview.getLayers().size();
		int scrollX = scrollPane.getHorizontalScrollBar().getValue();
		int relX = panelX + scrollX - PADDING;
		int index = relX / CARD_STRIDE;
		return Math.clamp(index, 0, count - 1);
	}

	private void commitDrag() {
		List<ImageLayer> layers = preview.getLayers();
		int count = layers.size();

		Component[] components = itemsContainer.getComponents();
		int uiIndex = 0;
		int originalRealIndex = -1;

		for (Component comp : components) {
			if (comp instanceof CardPanel card) {
				if (card.isDragPlaceholder()) {
					originalRealIndex = card.getRealIndex();
					break;
				}

				uiIndex++;
			}
		}

		if (originalRealIndex < 0) {
			refresh();
			return;
		}

		int newRealIndex = count - 1 - uiIndex;

		if (originalRealIndex == newRealIndex) {
			refresh();
			return;
		}

		preview.moveLayer(originalRealIndex, newRealIndex);

		if (onLayersReordered != null) {
			onLayersReordered.run();
		}

		refresh();
	}

	private void selectLayer(int realIndex, boolean ctrlDown, boolean shiftDown) {
		List<ImageLayer> layers = preview.getLayers();
		int count = layers.size();

		if (shiftDown && lastClickedRealIndex >= 0) {
			// Shift+Click: выделяем диапазон от якоря до текущего
			int from = Math.min(lastClickedRealIndex, realIndex);
			int to = Math.max(lastClickedRealIndex, realIndex);
			selectedIndices.clear();

			for (int i = from; i <= to; i++) {
				if (i < count) {
					selectedIndices.add(i);
				}
			}

		} else if (ctrlDown) {
			// Ctrl+Click: тоглим конкретный слой в мультиселекте
			if (selectedIndices.contains(realIndex)) {
				selectedIndices.remove(realIndex);
			} else {
				selectedIndices.add(realIndex);
			}

			lastClickedRealIndex = realIndex;
		} else {
			// Обычный клик: сбрасываем мультиселект, выделяем один
			selectedIndices.clear();
			selectedIndices.add(realIndex);
			lastClickedRealIndex = realIndex;
		}

		preview.setActiveLayerIndex(realIndex);
		refreshActiveState();
		syncMergeButton();

		if (onActiveChanged != null) {
			onActiveChanged.accept(realIndex);
		}
	}

	private void toggleVisibility(ImageLayer layer, int realIndex) {
		layer.setVisible(!layer.isVisible());
		preview.repaint();

		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card && card.getLayer() == layer) {
				card.syncEyeIcon(layer.isVisible());
				card.animateVisibility(!layer.isVisible());
				break;
			}
		}
	}

	private void animateVisibility(ImageLayer layer) {
		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card && card.getLayer() == layer) {
				card.animateVisibility(!layer.isVisible());
				break;
			}
		}
	}

	private void syncMergeButton() {
		mergeButton.setVisible(selectedIndices.size() >= 2);
	}

	public void mergeSelectedLayers() {
		if (selectedIndices.size() < 2) {
			return;
		}

		List<Integer> indices = new ArrayList<>(selectedIndices);
		selectedIndices.clear();
		lastClickedRealIndex = -1;
		syncMergeButton();

		preview.mergeLayers(indices);
	}

	/**
	 * Удаляет все выделенные слои по убыванию индекса, чтобы не сбивать позиции.
	 * Если мультиселект пуст — удаляет активный слой.
	 */
	private void removeSelectedLayers() {
		Set<Integer> toRemove = selectedIndices.isEmpty()
			? Set.of(preview.getActiveLayerIndex())
			: new LinkedHashSet<>(selectedIndices);

		if (toRemove.isEmpty()) {
			return;
		}

		// Сортируем по убыванию, чтобы удаление не сбивало индексы
		List<Integer> sorted = toRemove.stream()
			.sorted((a, b) -> b - a)
			.toList();

		selectedIndices.clear();
		lastClickedRealIndex = -1;

		for (int realIndex : sorted) {
			removeLayer(realIndex);
		}
	}

	private void removeLayer(int realIndex) {
		CardPanel target = findCardByRealIndex(realIndex);

		if (target == null) {
			doRemoveLayer(realIndex);
			return;
		}

		if (realIndex == preview.getActiveLayerIndex()) {
			List<ImageLayer> layers = preview.getLayers();
			int count = layers.size();
			int nextIndex = realIndex < count - 1 ? realIndex + 1 : realIndex - 1;

			if (nextIndex >= 0 && nextIndex < count) {
				target.setActive(false);
				preview.setActiveLayerIndex(nextIndex);
				refreshActiveState();

				if (onActiveChanged != null) {
					onActiveChanged.accept(nextIndex);
				}
			}
		}

		animateRemovalShift(target);

		target.animateRemoval(() -> {
			itemsContainer.remove(target);
			itemsContainer.revalidate();
			itemsContainer.repaint();
			doRemoveLayer(realIndex);
		});
	}

	private void doRemoveLayer(int realIndex) {
		preview.removeLayer(realIndex);

		if (onLayerRemoved != null) {
			onLayerRemoved.accept(realIndex);
		}

		refresh();
	}

	private CardPanel findCardByRealIndex(int realIndex) {
		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card && card.getRealIndex() == realIndex) {
				return card;
			}
		}

		return null;
	}

	/**
	 * Анимирует сдвиг соседних карточек к новым позициям, как будто удаляемая уже исчезла.
	 */
	private void animateRemovalShift(CardPanel removed) {
		List<CardPanel> cards = collectCards();
		cards.remove(removed);

		for (int i = 0; i < cards.size(); i++) {
			CardPanel card = cards.get(i);
			int targetX = PADDING + i * CARD_STRIDE;
			animateCardToX(card, card.getX(), targetX);
		}

		updateContainerPreferredSize(cards.size());
		itemsContainer.revalidate();
		itemsContainer.repaint();
	}

	/**
	 * Рисует снимки удаляемых карточек поверх остальных.
	 */
	private void paintRemovingCards(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card && card.removeSnapshot != null) {
				float p = card.removeProgress;
				float alpha = 1f - p;
				float scale = 1f - p * 0.4f;

				float cx = card.removeStartX + CARD_SIZE / 2f;
				float cy = card.removeStartY + CARD_SIZE / 2f;

				float offsetX = -p * CARD_SIZE * 0.6f;
				float offsetY = -p * CARD_SIZE * 0.6f;

				AffineTransform transform = new AffineTransform();
				transform.translate(cx + offsetX, cy + offsetY);
				transform.scale(scale, scale);
				transform.translate(-CARD_SIZE / 2f, -CARD_SIZE / 2f);

				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
				g2.drawImage(card.removeSnapshot, transform, null);
			}
		}

		g2.dispose();
	}

	/** Обновляет состояние активности и выделения без полного пересоздания карточек. */
	private void refreshActiveState() {
		List<ImageLayer> layers = preview.getLayers();
		int count = layers.size();
		int activeIndex = preview.getActiveLayerIndex();
		int cardIndex = 0;

		for (Component comp : itemsContainer.getComponents()) {
			if (comp instanceof CardPanel card) {
				int realIndex = count - 1 - cardIndex;
				card.setActive(realIndex == activeIndex);
				card.setSelected(selectedIndices.contains(realIndex));
				card.syncEyeIcon(layers.get(realIndex).isVisible());
				cardIndex++;
			}
		}

		itemsContainer.repaint();
	}

	/** Устанавливает Del-бинд на scrollPane для удаления выделенных слоёв. */
	public void deleteSelectedLayers() {
		removeSelectedLayers();
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(0, CARD_SIZE + PADDING * 2);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, CARD_SIZE + PADDING * 2);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Внутренний класс карточки слоя
	// ─────────────────────────────────────────────────────────────────────────

	private final class CardPanel extends JPanel {

		private final ImageLayer layer;
		private final int realIndex;
		private boolean active;
		private boolean selected;
		private boolean dragPlaceholder;

		private final RippleButton eyeBtn;
		private final RippleButton crossBtn;

		int animX;
		Timer moveTimer;

		// Анимация видимости: 0.0 = цветное, 1.0 = ч/б + затемнение
		private float visibilityAlpha;
		private Timer visibilityTimer;

		// Анимация удаления
		BufferedImage removeSnapshot;
		float removeProgress;
		int removeStartX;
		int removeStartY;
		private Timer removeTimer;

		// Анимация выделения активного слоя: 0.0 = border, 1.0 = accent
		private float activeAlpha;
		private Timer activeTimer;

		// Анимация выделения в мультиселекте: 0.0 = border, 1.0 = warn
		private float selectedAlpha;
		private Timer selectedTimer;

		private BufferedImage grayscaleCache;
		private BufferedImage grayscaleCacheSource;

		CardPanel(ImageLayer layer, int realIndex, boolean active, boolean selected) {
			this.layer = layer;
			this.realIndex = realIndex;
			this.active = active;
			this.selected = selected;
			this.visibilityAlpha = layer.isVisible() ? 0.0f : 1.0f;
			this.activeAlpha = active ? 1.0f : 0.0f;
			this.selectedAlpha = selected ? 1.0f : 0.0f;

			setLayout(null);
			setOpaque(false);
			setPreferredSize(new Dimension(CARD_SIZE, CARD_SIZE));
			setMinimumSize(new Dimension(CARD_SIZE, CARD_SIZE));
			setMaximumSize(new Dimension(CARD_SIZE, CARD_SIZE));
			UiAnimator.applyHandCursor(this);

			eyeBtn = new RippleButton(layer.isVisible() ? AppIcon.EYE : AppIcon.EYE_OFF, mainWindow);
			eyeBtn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
			eyeBtn.setBounds(ICON_PAD, ICON_PAD, ICON_BTN_SIZE, ICON_BTN_SIZE);
			eyeBtn.addActionListener(e -> toggleVisibility(layer, realIndex));

			crossBtn = new RippleButton(AppIcon.CROSS, mainWindow);
			crossBtn.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
			crossBtn.setBounds(CARD_SIZE - ICON_BTN_SIZE - ICON_PAD, ICON_PAD, ICON_BTN_SIZE, ICON_BTN_SIZE);
			crossBtn.addActionListener(e -> removeLayer(realIndex));

			add(eyeBtn);
			add(crossBtn);

			setToolTipText(layer.getName());

			MouseAdapter drag = buildDragAdapter();
			addMouseListener(drag);
			addMouseMotionListener(drag);
		}

		ImageLayer getLayer() {
			return layer;
		}

		int getRealIndex() {
			return realIndex;
		}

		boolean isDragPlaceholder() {
			return dragPlaceholder;
		}

		void setActive(boolean active) {
			if (this.active == active) {
				return;
			}

			this.active = active;
			animateActive(active);
		}

		void setSelected(boolean selected) {
			if (this.selected == selected) {
				return;
			}

			this.selected = selected;
			animateSelected(selected);
		}

		void setDragPlaceholder(boolean placeholder) {
			dragPlaceholder = placeholder;
			eyeBtn.setVisible(!placeholder);
			crossBtn.setVisible(!placeholder);
			repaint();
		}

		void animateActive(boolean toActive) {
			if (activeTimer != null) {
				activeTimer.stop();
			}

			float target = toActive ? 1.0f : 0.0f;

			activeTimer = UiAnimator.animateFloat(activeAlpha, target, ANIM_DURATION_MS, alpha -> {
				activeAlpha = alpha;
				repaint();
			}, null);
		}

		void animateSelected(boolean toSelected) {
			if (selectedTimer != null) {
				selectedTimer.stop();
			}

			float target = toSelected ? 1.0f : 0.0f;

			selectedTimer = UiAnimator.animateFloat(selectedAlpha, target, ANIM_DURATION_MS, alpha -> {
				selectedAlpha = alpha;
				repaint();
			}, null);
		}

		void syncEyeIcon(boolean visible) {
			eyeBtn.setCurrentIcon(visible ? AppIcon.EYE : AppIcon.EYE_OFF);
		}

		/**
		 * Делает снимок карточки, скрывает её и анимирует снимок:
		 * улетает влево-вверх, уменьшается и становится прозрачным.
		 */
		void animateRemoval(Runnable onDone) {
			if (removeTimer != null) {
				removeTimer.stop();
			}

			eyeBtn.setEnabled(false);
			crossBtn.setEnabled(false);

			removeSnapshot = new BufferedImage(CARD_SIZE, CARD_SIZE, BufferedImage.TYPE_INT_ARGB);
			printAll(removeSnapshot.getGraphics());
			removeStartX = getX();
			removeStartY = getY();
			removeProgress = 0f;

			setVisible(false);

			removeTimer = UiAnimator.animateFloat(0f, 1f, ANIM_DURATION_MS, progress -> {
				removeProgress = progress;
				itemsContainer.repaint();
			}, onDone);
		}

		void animateVisibility(boolean hidden) {
			if (visibilityTimer != null) {
				visibilityTimer.stop();
			}

			float target = hidden ? 1.0f : 0.0f;

			visibilityTimer = UiAnimator.animateFloat(visibilityAlpha, target, VISIBILITY_ANIM_DURATION_MS, alpha -> {
				visibilityAlpha = alpha;
				repaint();
			}, null);
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (dragPlaceholder) {
				paintPlaceholder(g);
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			int inner = 1;
			int innerSize = CARD_SIZE - inner * 2;

			g2.setColor(bgDeep());
			g2.fillRoundRect(inner, inner, innerSize, innerSize, ARC, ARC);

			BufferedImage img = layer.getImage();

			if (img != null) {
				Shape clip = new RoundRectangle2D.Float(inner, inner, innerSize, innerSize, ARC, ARC);
				Shape prevClip = g2.getClip();
				g2.clip(clip);

				g2.drawImage(img, inner, inner, innerSize, innerSize, null);

				if (visibilityAlpha > 0.0f) {
					BufferedImage gray = getGrayscale(img);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibilityAlpha));
					g2.drawImage(gray, inner, inner, innerSize, innerSize, null);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibilityAlpha * 0.55f));
					g2.setColor(Color.BLACK);
					g2.fillRoundRect(inner, inner, innerSize, innerSize, ARC, ARC);
					g2.setComposite(AlphaComposite.SrcOver);
				}

				g2.setClip(prevClip);
			}

			// Приоритет рамки: активный (accent) > выбранный (warn) > обычный (border)
			Color borderColor;
			float strokeWidth;

			if (activeAlpha > 0.0f) {
				borderColor = UiAnimator.lerp(border(), accent(), activeAlpha);
				strokeWidth = 1f + activeAlpha;
			} else {
				borderColor = UiAnimator.lerp(border(), warn(), selectedAlpha);
				strokeWidth = 1f + selectedAlpha * 0.5f;
			}

			g2.setColor(borderColor);
			g2.setStroke(new BasicStroke(strokeWidth));
			g2.drawRoundRect(inner, inner, innerSize, innerSize, ARC, ARC);

			g2.dispose();
		}

		private BufferedImage getGrayscale(BufferedImage src) {
			if (grayscaleCache == null || grayscaleCacheSource != src) {
				grayscaleCache = GRAYSCALE_OP.filter(src, null);
				grayscaleCacheSource = src;
			}

			return grayscaleCache;
		}

		private void paintPlaceholder(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Color b = border();
			g2.setColor(new Color(b.getRed(), b.getGreen(), b.getBlue(), 80));
			float[] dash = { 4f, 4f };
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
			g2.drawRoundRect(1, 1, CARD_SIZE - 2, CARD_SIZE - 2, ARC, ARC);

			g2.dispose();
		}

		private MouseAdapter buildDragAdapter() {
			return new MouseAdapter() {
				private int pressScreenX;
				private boolean dragStarted;

				@Override
				public void mousePressed(MouseEvent e) {
					pressScreenX = e.getXOnScreen();
					dragStarted = false;

					List<ImageLayer> layers = preview.getLayers();
					int count = layers.size();
					int uiIndex = count - 1 - realIndex;
					startDrag(uiIndex, pressScreenX, toLayerPanelX(e), toLayerPanelY(e));
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (!dragStarted && Math.abs(e.getXOnScreen() - pressScreenX) > DRAG_THRESHOLD) {
						dragStarted = true;
						setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					}

					updateDrag(e.getXOnScreen(), toLayerPanelX(e), toLayerPanelY(e));
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					UiAnimator.applyHandCursor(CardPanel.this);
					boolean wasDragging = dragging;
					endDrag();

					if (!wasDragging) {
						boolean ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
						boolean shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
						selectLayer(realIndex, ctrl, shift);
					}
				}

				private int toLayerPanelX(MouseEvent e) {
					Point p = SwingUtilities.convertPoint(CardPanel.this, e.getPoint(), LayerListPanel.this);
					return p.x;
				}

				private int toLayerPanelY(MouseEvent e) {
					Point p = SwingUtilities.convertPoint(CardPanel.this, e.getPoint(), LayerListPanel.this);
					return p.y;
				}
			};
		}
	}
}
