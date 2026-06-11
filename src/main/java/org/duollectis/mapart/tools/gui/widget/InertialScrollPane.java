package org.duollectis.mapart.tools.gui.widget;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

/**
 * {@link JScrollPane} с инерционной прокруткой и передачей скролла родительскому
 * контейнеру при достижении края.
 *
 * <p>Логика передачи скролла родителю: когда список упёрся в край и инерция
 * полностью затухла, следующий импульс колеса в том же направлении передаётся
 * ближайшему родительскому {@link InertialScrollPane} через его собственную
 * инерционную систему — тот обрабатывает импульс самостоятельно.
 */
public class InertialScrollPane extends JScrollPane {

	private static final double FRICTION = 0.92;
	private static final double VELOCITY_THRESHOLD = 0.5;
	private static final int FRAME_MS = 16;
	private static final double PIXELS_PER_NOTCH = 10.0;

	private double scrollVelocity = 0.0;
	private boolean atEdge = false;
	private Timer inertiaTimer;

	public InertialScrollPane(Component view) {
		super(
			view,
			JScrollPane.VERTICAL_SCROLLBAR_NEVER,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
		);
		setOpaque(false);
		getViewport().setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder());
		installWheelListener();
	}

	private void installWheelListener() {
		addMouseWheelListener(this::onMouseWheel);
		getViewport().addMouseWheelListener(this::onMouseWheel);

		if (getViewport().getView() != null) {
			getViewport().getView().addMouseWheelListener(this::onMouseWheel);
		}
	}

	/**
	 * Регистрирует слушатель колеса на дочернем компоненте, добавленном после создания.
	 * Вызывать вручную не нужно — используй конструктор с {@code view}.
	 */
	public void attachWheelListenerTo(Component component) {
		component.addMouseWheelListener(this::onMouseWheel);
	}

	private void onMouseWheel(MouseWheelEvent e) {
		e.consume();

		var bar = getVerticalScrollBar();
		boolean hasNoScroll = bar.getMaximum() - bar.getVisibleAmount() <= bar.getMinimum();

		if (hasNoScroll) {
			dispatchToParentInertialPane(e.getPreciseWheelRotation() * PIXELS_PER_NOTCH);
			return;
		}

		applyImpulse(e.getPreciseWheelRotation() * PIXELS_PER_NOTCH);
	}

	void applyImpulse(double impulse) {
		if (atEdge && isMovingTowardEdge(impulse)) {
			dispatchToParentInertialPane(impulse);
			return;
		}

		scrollVelocity += impulse;
		startInertiaIfNeeded();
	}

	private boolean isMovingTowardEdge(double impulse) {
		var bar = getVerticalScrollBar();
		int maxValue = bar.getMaximum() - bar.getVisibleAmount();

		boolean atTop = bar.getValue() <= bar.getMinimum();
		boolean atBottom = bar.getValue() >= maxValue;

		return (atTop && impulse < 0) || (atBottom && impulse > 0);
	}

	private void startInertiaIfNeeded() {
		if (inertiaTimer != null && inertiaTimer.isRunning()) {
			return;
		}

		inertiaTimer = new Timer(FRAME_MS, tick -> applyInertia());
		inertiaTimer.start();
	}

	private void applyInertia() {
		if (Math.abs(scrollVelocity) < VELOCITY_THRESHOLD) {
			scrollVelocity = 0.0;
			inertiaTimer.stop();
			return;
		}

		var bar = getVerticalScrollBar();
		int oldValue = bar.getValue();
		int maxValue = bar.getMaximum() - bar.getVisibleAmount();
		int newValue = (int) Math.round(oldValue + scrollVelocity);
		int clamped = Math.clamp(newValue, bar.getMinimum(), maxValue);

		bar.setValue(clamped);

		atEdge = clamped == oldValue;

		if (atEdge) {
			scrollVelocity = 0.0;
			inertiaTimer.stop();
		} else {
			scrollVelocity *= FRICTION;
		}
	}

	private void dispatchToParentInertialPane(double impulse) {
		Container parent = getParent();

		while (parent != null) {
			if (parent instanceof InertialScrollPane parentPane) {
				parentPane.applyImpulse(impulse);
				return;
			}

			parent = parent.getParent();
		}
	}
}
