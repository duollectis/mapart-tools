package org.duollectis.mapart.tools.converter;

import java.util.List;
import java.util.Set;

/**
 * Настройки блоков-опор для схематики.
 * Поддерживает пустой список — это означает что пользователь явно снял все блоки опоры.
 * Веса не обязаны суммироваться в 100 — они нормализуются при выборе блока.
 */
public final class SupportBlockSettings {

	private final List<Entry> entries;
	private final WeightedSelector<String> selector;
	private final WeightedSelector.Mode mode;

	public SupportBlockSettings(List<Entry> entries, WeightedSelector.Mode mode) {
		this.entries = entries == null ? List.of() : List.copyOf(entries);
		this.mode = mode;

		if (this.entries.isEmpty()) {
			this.selector = null;
			return;
		}

		List<WeightedSelector.Entry<String>> selectorEntries = this.entries.stream()
			.map(e -> new WeightedSelector.Entry<>(e.blockId(), e.weight()))
			.toList();

		this.selector = new WeightedSelector<>(selectorEntries, mode);
	}

	/**
	 * Создаёт настройки с одним блоком опоры (обратная совместимость).
	 *
	 * @param blockId идентификатор блока
	 */
	public static SupportBlockSettings single(String blockId) {
		return new SupportBlockSettings(List.of(new Entry(blockId, 100)), WeightedSelector.Mode.SEQUENTIAL);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * Возвращает ID блока опоры для позиции {@code index} в схематике.
	 * Возвращает {@code null} если список блоков пуст.
	 *
	 * @param index линейный индекс позиции (монотонный счётчик)
	 */
	public String pickBlock(int index) {
		return selector != null ? selector.pick(index) : null;
	}

	/**
	 * Возвращает новый экземпляр с записями, отфильтрованными по множеству допустимых ID.
	 * Если после фильтрации не осталось ни одной записи — возвращает пустой экземпляр.
	 *
	 * @param validIds множество допустимых blockId
	 * @return отфильтрованный экземпляр (может быть пустым)
	 */
	public SupportBlockSettings filtered(Set<String> validIds) {
		List<Entry> kept = entries.stream()
			.filter(e -> validIds.contains(e.blockId()))
			.toList();

		return new SupportBlockSettings(kept, mode);
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public WeightedSelector.Mode getMode() {
		return mode;
	}

	/**
	 * Один блок опоры с его весом (процентом заполнения).
	 *
	 * @param blockId идентификатор блока (например "minecraft:stone")
	 * @param weight  вес блока (> 0)
	 */
	public record Entry(String blockId, int weight) {

		public Entry {
			if (weight <= 0) {
				throw new IllegalArgumentException("Вес блока опоры должен быть > 0");
			}
		}
	}
}
