package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Random;

/**
 * Обобщённый механизм взвешенного выбора элемента из списка.
 * Поддерживает два режима: случайный (с учётом весов) и последовательный
 * (чередование пропорционально весам). Веса не обязаны суммироваться в 100 —
 * они нормализуются автоматически.
 *
 * @param <T> тип выбираемого элемента
 */
public final class WeightedSelector<T> {

	private static final Random RANDOM = new Random();

	@Getter
	private final List<Entry<T>> entries;

	@Getter
	private final Mode mode;

	private final int[] thresholds;
	private final int totalWeight;

	public WeightedSelector(List<Entry<T>> entries, Mode mode) {
		if (entries == null || entries.isEmpty()) {
			throw new IllegalArgumentException("Список элементов не может быть пустым");
		}

		this.entries = List.copyOf(entries);
		this.mode = mode;
		this.totalWeight = entries.stream().mapToInt(Entry::weight).sum();
		this.thresholds = buildThresholds(entries);
	}

	/**
	 * Создаёт селектор с одним элементом (обратная совместимость).
	 *
	 * @param value единственный элемент
	 */
	public static <T> WeightedSelector<T> single(T value) {
		return new WeightedSelector<>(List.of(new Entry<>(value, 100)), Mode.SEQUENTIAL);
	}

	/** Создаёт независимую копию с теми же entries и режимом, но сброшенными счётчиками. */
	public WeightedSelector<T> copy() {
		return new WeightedSelector<>(entries, mode);
	}

	/**
	 * Выбирает элемент для позиции {@code index}.
	 * Режим {@link Mode#RANDOM} — случайно с учётом весов.
	 * Режим {@link Mode#SEQUENTIAL} — последовательно пропорционально весам.
	 *
	 * @param index монотонный счётчик позиции (используется только для SEQUENTIAL)
	 */
	/**
	 * Выбирает элемент для позиции {@code index}.
	 * Режим {@link Mode#RANDOM} — случайно с учётом весов.
	 * Режим {@link Mode#SEQUENTIAL} — детерминированно по позиции: {@code index % totalWeight}
	 * попадает в пороговый диапазон нужного элемента. Это корректно работает при любом
	 * масштабе весов (в том числе при масштабировании на 1000 из процентов).
	 *
	 * @param index монотонный счётчик позиции
	 */
	public T pick(int index) {
		if (entries.size() == 1) {
			return entries.getFirst().value();
		}

		return mode == Mode.RANDOM
			? pickRandom()
			: pickSequential(index);
	}

	private T pickRandom() {
		int roll = RANDOM.nextInt(totalWeight);

		for (int i = 0; i < thresholds.length; i++) {
			if (roll < thresholds[i]) {
				return entries.get(i).value();
			}
		}

		return entries.getLast().value();
	}

	/**
	 * Детерминированный выбор по позиции: {@code index % totalWeight} определяет
	 * в какой весовой диапазон попадает позиция. Не зависит от масштаба весов.
	 */
	private T pickSequential(int index) {
		int position = index % totalWeight;

		for (int i = 0; i < thresholds.length; i++) {
			if (position < thresholds[i]) {
				return entries.get(i).value();
			}
		}

		return entries.getLast().value();
	}

	private static <T> int[] buildThresholds(List<Entry<T>> entries) {
		int[] result = new int[entries.size()];
		int cumulative = 0;

		for (int i = 0; i < entries.size(); i++) {
			cumulative += entries.get(i).weight();
			result[i] = cumulative;
		}

		return result;
	}

	@Getter
	@RequiredArgsConstructor
	public enum Mode {
		RANDOM("support.mode_random"),
		SEQUENTIAL("support.mode_sequential");

		private final String langKey;
	}

	/**
	 * Элемент с весом.
	 *
	 * @param value  значение
	 * @param weight вес (> 0)
	 */
	public record Entry<T>(T value, int weight) {

		public Entry {
			if (weight <= 0) {
				throw new IllegalArgumentException("Вес элемента должен быть > 0");
			}
		}
	}
}
