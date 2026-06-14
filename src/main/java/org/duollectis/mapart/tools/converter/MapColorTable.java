package org.duollectis.mapart.tools.converter;

import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Таблица соответствия базовых RGB-цветов карты Minecraft их идентификаторам.
 *
 * <p>Данные взяты из {@code net.minecraft.block.MapColor} версии 1.21.x.
 * Ключ — базовый RGB-цвет (как в JSON-палитре), значение — {@code baseColorId} (1–61).
 * Формула байта цвета карты: {@code (baseColorId << 2) | brightness.ordinal()},
 * где {@code Brightness.ordinal()}: LOW=0, NORMAL=1, HIGH=2.
 */
@UtilityClass
public class MapColorTable {

	/**
	 * Неизменяемая таблица: RGB-цвет → baseColorId карты Minecraft.
	 * Соответствует {@code MapColor.COLORS[id].color} для id 1–61.
	 */
	public static final Map<Integer, Integer> RGB_TO_BASE_ID = Map.ofEntries(
		Map.entry(8368696,   1),
		Map.entry(16247203,  2),
		Map.entry(13092807,  3),
		Map.entry(16711680,  4),
		Map.entry(10526975,  5),
		Map.entry(10987431,  6),
		Map.entry(31744,     7),
		Map.entry(16777215,  8),
		Map.entry(10791096,  9),
		Map.entry(9923917,   10),
		Map.entry(7368816,   11),
		Map.entry(4210943,   12),
		Map.entry(9402184,   13),
		Map.entry(16776437,  14),
		Map.entry(14188339,  15),
		Map.entry(11685080,  16),
		Map.entry(6724056,   17),
		Map.entry(15066419,  18),
		Map.entry(8375321,   19),
		Map.entry(15892389,  20),
		Map.entry(5000268,   21),
		Map.entry(10066329,  22),
		Map.entry(5013401,   23),
		Map.entry(8339378,   24),
		Map.entry(3361970,   25),
		Map.entry(6704179,   26),
		Map.entry(6717235,   27),
		Map.entry(10040115,  28),
		Map.entry(1644825,   29),
		Map.entry(16445005,  30),
		Map.entry(6085589,   31),
		Map.entry(4882687,   32),
		Map.entry(55610,     33),
		Map.entry(8476209,   34),
		Map.entry(7340544,   35),
		Map.entry(13742497,  36),
		Map.entry(10441252,  37),
		Map.entry(9787244,   38),
		Map.entry(7367818,   39),
		Map.entry(12223780,  40),
		Map.entry(6780213,   41),
		Map.entry(10505550,  42),
		Map.entry(3746083,   43),
		Map.entry(8874850,   44),
		Map.entry(5725276,   45),
		Map.entry(8014168,   46),
		Map.entry(4996700,   47),
		Map.entry(4993571,   48),
		Map.entry(5001770,   49),
		Map.entry(9321518,   50),
		Map.entry(2430480,   51),
		Map.entry(12398641,  52),
		Map.entry(9715553,   53),
		Map.entry(6035741,   54),
		Map.entry(1474182,   55),
		Map.entry(3837580,   56),
		Map.entry(5647422,   57),
		Map.entry(1356933,   58),
		Map.entry(6579300,   59),
		Map.entry(14200723,  60),
		Map.entry(8365974,   61)
	);

	/**
	 * Возвращает baseColorId для заданного RGB-цвета палитры.
	 * Если цвет не найден в таблице — возвращает -1 (неизвестный цвет).
	 *
	 * @param rgb базовый RGB-цвет из JSON-палитры
	 * @return baseColorId (1–61) или -1
	 */
	public int resolveBaseId(int rgb) {
		return RGB_TO_BASE_ID.getOrDefault(rgb, -1);
	}
}
