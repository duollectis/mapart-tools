package org.duollectis.mapart.tools.converter;

import lombok.Getter;

import java.util.List;

@Getter
public class PaletteEntry {

	private final List<BlockData> blocks;
	private final int rgb;
	private final Brightness brightness;
	private final WeightedSelector<BlockData> blockSelector;

	/**
	 * Идентификатор цвета карты Minecraft: {@code baseColorId * 4 + brightnessOffset}.
	 * Используется при экспорте в формат карты (.dat).
	 * Значение -1 означает, что запись создана без привязки к палитре карты.
	 */
	private final int mapColorId;

	public PaletteEntry(List<BlockData> blocks, int rgb, Brightness brightness) {
		this.blocks = List.copyOf(blocks);
		this.rgb = rgb;
		this.brightness = brightness;
		this.blockSelector = WeightedSelector.single(blocks.getFirst());
		this.mapColorId = -1;
	}

	public PaletteEntry(
		List<BlockData> blocks,
		int rgb,
		Brightness brightness,
		WeightedSelector<BlockData> blockSelector
	) {
		this.blocks = List.copyOf(blocks);
		this.rgb = rgb;
		this.brightness = brightness;
		this.blockSelector = blockSelector;
		this.mapColorId = -1;
	}

	public PaletteEntry(
		List<BlockData> blocks,
		int rgb,
		Brightness brightness,
		WeightedSelector<BlockData> blockSelector,
		int mapColorId
	) {
		this.blocks = List.copyOf(blocks);
		this.rgb = rgb;
		this.brightness = brightness;
		this.blockSelector = blockSelector;
		this.mapColorId = mapColorId;
	}

	/**
	 * Выбирает блок для позиции {@code index} согласно настроенному селектору.
	 * По умолчанию (без явного селектора) всегда возвращает первый блок.
	 *
	 * @param index монотонный счётчик позиции
	 */
	public BlockData pickBlock(int index) {
		return blockSelector.pick(index);
	}
}
