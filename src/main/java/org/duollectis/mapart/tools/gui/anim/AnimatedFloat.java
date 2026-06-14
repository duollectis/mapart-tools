package org.duollectis.mapart.tools.gui.anim;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Инкапсулирует паттерн «остановить предыдущую анимацию → запустить новую»
 * для float-значения. Устраняет дублирование полей {@code Timer} и блоков
 * {@code if (timer != null) timer.stop()} во всех виджетах с hover/toggle-анимациями.
 *
 * <p>Хранит текущее значение, чтобы новая анимация всегда стартовала
 * с реального промежуточного состояния, а не с крайней точки.
 */
public final class AnimatedFloat {

	private float value;
	private Timer activeTimer;

	public AnimatedFloat(float initialValue) {
		value = initialValue;
	}

	/**
	 * Останавливает текущую анимацию и запускает новую от текущего значения до {@code to}.
	 *
	 * @param to         целевое значение
	 * @param durationMs длительность в мс
	 * @param onValue    колбэк с текущим значением на каждом кадре
	 */
	public void animateTo(float to, int durationMs, Consumer<Float> onValue) {
		animateTo(to, durationMs, onValue, null);
	}

	/**
	 * Останавливает текущую анимацию и запускает новую от текущего значения до {@code to}.
	 *
	 * @param to         целевое значение
	 * @param durationMs длительность в мс
	 * @param onValue    колбэк с текущим значением на каждом кадре
	 * @param onDone     колбэк по завершении (может быть null)
	 */
	public void animateTo(float to, int durationMs, Consumer<Float> onValue, Runnable onDone) {
		stop();

		activeTimer = UiAnimator.animateFloat(
			value, to, durationMs,
			v -> {
				value = v;
				onValue.accept(v);
			},
			onDone
		);
	}

	/** Мгновенно устанавливает значение без анимации. */
	public void set(float newValue) {
		stop();
		value = newValue;
	}

	public float get() {
		return value;
	}

	public void stop() {
		if (activeTimer == null) {
			return;
		}

		activeTimer.stop();
		activeTimer = null;
	}
}
