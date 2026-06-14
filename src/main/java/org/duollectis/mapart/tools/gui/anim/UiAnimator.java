package org.duollectis.mapart.tools.gui.anim;

import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Утилита для декларативных Swing-анимаций через {@link Timer} на ~60fps.
 * Все методы возвращают запущенный таймер — вызывающий код может остановить его
 * через {@link Timer#stop()} при необходимости.
 *
 * <p>Кривая easeOutCubic применяется везде по умолчанию — она даёт ощущение
 * «живого» интерфейса: быстрый старт и плавное торможение, как в Telegram.
 */
@UtilityClass
public class UiAnimator {

	private static final int FRAME_MS = 16;

	/** Глобальный флаг анимаций. При {@code false} все переходы выполняются мгновенно. */
	public static boolean animationsEnabled = true;

	/**
	 * Анимирует float-значение от {@code from} до {@code to} за {@code durationMs} мс
	 * с кривой easeOutCubic. На каждом кадре вызывает {@code onValue}, в конце — {@code onDone}.
	 * Если {@link #animationsEnabled} равен {@code false}, значение применяется мгновенно.
	 *
	 * @param from       начальное значение
	 * @param to         конечное значение
	 * @param durationMs длительность в мс
	 * @param onValue    колбэк с текущим значением на каждом кадре
	 * @param onDone     колбэк по завершении (может быть null)
	 * @return запущенный таймер (уже остановленный, если анимации отключены)
	 */
	public static Timer animateFloat(float from, float to, int durationMs, Consumer<Float> onValue, Runnable onDone) {
		if (!animationsEnabled) {
			onValue.accept(to);

			if (onDone != null) {
				onDone.run();
			}

			Timer stub = new Timer(Integer.MAX_VALUE, null);
			return stub;
		}

		long startTime = System.currentTimeMillis();

		Timer timer = new Timer(FRAME_MS, e -> {
			double t = Math.min(1.0, (double) (System.currentTimeMillis() - startTime) / durationMs);
			double eased = easeOutCubicD(t);
			onValue.accept((float) (from + (to - from) * eased));

			if (t >= 1.0) {
				((Timer) e.getSource()).stop();

				if (onDone != null) {
					onDone.run();
				}
			}
		});

		timer.start();
		return timer;
	}

	/**
	 * Анимирует прогресс от 0 до 1 за {@code durationMs} мс, передавая в коллбэк
	 * <b>линейный</b> {@code t} [0..1]. Вызывающий код сам применяет нужную кривую
	 * к каждому анимируемому значению — это позволяет одним таймером управлять
	 * несколькими свойствами с разными кривыми без рывков.
	 *
	 * @param durationMs длительность в мс
	 * @param onProgress колбэк с линейным t [0..1] на каждом кадре
	 * @param onDone     колбэк по завершении (может быть null)
	 * @return запущенный таймер
	 */
	public static Timer animateProgress(int durationMs, Consumer<Float> onProgress, Runnable onDone) {
		if (!animationsEnabled) {
			onProgress.accept(1f);

			if (onDone != null) {
				onDone.run();
			}

			Timer stub = new Timer(Integer.MAX_VALUE, null);
			return stub;
		}

		long startTime = System.currentTimeMillis();

		Timer timer = new Timer(FRAME_MS, e -> {
			double t = Math.min(1.0, (double) (System.currentTimeMillis() - startTime) / durationMs);
			onProgress.accept((float) t);

			if (t >= 1.0) {
				((Timer) e.getSource()).stop();

				if (onDone != null) {
					onDone.run();
				}
			}
		});

		timer.start();
		return timer;
	}

	/**
	 * Плавный переход цвета компонента от {@code from} к {@code to} за {@code durationMs} мс
	 * с кривой easeOutCubic.
	 *
	 * @param target     компонент для перерисовки
	 * @param from       начальный цвет
	 * @param to         конечный цвет
	 * @param durationMs длительность перехода в мс
	 * @param applyColor функция применения цвета
	 * @return запущенный таймер
	 */
	public static Timer hoverTransition(JComponent target, Color from, Color to, int durationMs, Consumer<Color> applyColor) {
		return animateFloat(0f, 1f, durationMs, t -> {
			applyColor.accept(lerp(from, to, t));
			target.repaint();
		}, null);
	}

	/**
	 * Рисует ripple-эффект (круговая волна) из точки клика.
	 * Вызывай из {@code paintComponent} компонента, передавая сохранённый {@link RippleState}.
	 *
	 * @param g2    контекст рисования
	 * @param state состояние ripple (null — ничего не рисуется)
	 * @param w     ширина компонента
	 * @param h     высота компонента
	 */
	public static void paintRipple(Graphics2D g2, RippleState state, int w, int h) {
		if (state == null || state.alpha <= 0f) {
			return;
		}

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int maxRadius = (int) Math.sqrt(w * w + h * h);
		int radius = (int) (maxRadius * state.progress);

		Color rippleColor = new Color(
			255, 255, 255,
			(int) (state.alpha * 60)
		);

		g2.setColor(rippleColor);
		g2.fillOval(
			state.x - radius,
			state.y - radius,
			radius * 2,
			radius * 2
		);
	}

	/**
	 * Запускает ripple-анимацию из точки (x, y) и обновляет {@code state}.
	 * Возвращает новый {@link RippleState} — сохрани его в компоненте.
	 *
	 * @param x         X-координата клика
	 * @param y         Y-координата клика
	 * @param component компонент для перерисовки
	 * @return новый RippleState с запущенным таймером
	 */
	public static RippleState startRipple(int x, int y, JComponent component) {
		if (!animationsEnabled) {
			return null;
		}

		RippleState state = new RippleState(x, y);

		state.timer = animateFloat(0f, 1f, 500, progress -> {
			state.progress = progress;
			state.alpha = 1f - easeOutCubic(progress);
			component.repaint();
		}, () -> {
			state.alpha = 0f;
			component.repaint();
		});

		return state;
	}

	/**
	 * Синусоидальная пульсация яркости компонента пока активна.
	 * Вызывай {@link Timer#stop()} чтобы остановить.
	 *
	 * @param target    компонент для перерисовки
	 * @param baseColor базовый цвет
	 * @param amplitude амплитуда изменения яркости (0.0–1.0, рекомендуется 0.15–0.3)
	 * @param periodMs  период одного цикла пульсации в мс
	 * @param applyColor функция применения цвета
	 * @return запущенный таймер
	 */
	public static Timer pulse(JComponent target, Color baseColor, float amplitude, int periodMs, Consumer<Color> applyColor) {
		long startTime = System.currentTimeMillis();

		Timer timer = new Timer(FRAME_MS, e -> {
			long elapsed = System.currentTimeMillis() - startTime;
			double phase = (2.0 * Math.PI * elapsed) / periodMs;
			float brightness = 1.0f + amplitude * (float) Math.sin(phase);
			applyColor.accept(scaleBrightness(baseColor, brightness));
			target.repaint();
		});

		timer.start();
		return timer;
	}

	/**
	 * Линейная интерполяция между двумя цветами.
	 *
	 * @param a начальный цвет (t=0)
	 * @param b конечный цвет (t=1)
	 * @param t коэффициент [0.0, 1.0]
	 */
	public static Color lerp(Color a, Color b, float t) {
		int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		int alpha = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
		return new Color(
			Math.clamp(r, 0, 255),
			Math.clamp(g, 0, 255),
			Math.clamp(bl, 0, 255),
			Math.clamp(alpha, 0, 255)
		);
	}

	/** Кривая easeOutCubic: быстрый старт, плавное торможение. */
	public static float easeOutCubic(float t) {
		double f = 1.0 - t;
		return (float) (1.0 - f * f * f);
	}

	private static double easeOutCubicD(double t) {
		double f = 1.0 - t;
		return 1.0 - f * f * f;
	}

	private static Color scaleBrightness(Color color, float factor) {
		int r = Math.clamp((int) (color.getRed() * factor), 0, 255);
		int g = Math.clamp((int) (color.getGreen() * factor), 0, 255);
		int b = Math.clamp((int) (color.getBlue() * factor), 0, 255);
		return new Color(r, g, b, color.getAlpha());
	}

	/** Состояние ripple-анимации для одного компонента. */
	public static final class RippleState {

		final int x;
		final int y;
		float progress;
		float alpha;
		Timer timer;

		RippleState(int x, int y) {
			this.x = x;
			this.y = y;
			this.progress = 0f;
			this.alpha = 1f;
		}
	}
}
