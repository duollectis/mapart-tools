package org.duollectis.mapart.tools.converter;

import lombok.Getter;

import java.util.List;

@Getter
public class PaletteEntry {

	private final List<BlockData> blocks;
	private final int rgb;
	private final Brightness brightness;
	private final WeightedSelector<BlockData> blockSelector;

	public PaletteEntry(List<BlockData> blocks, int rgb, Brightness brightness) {
		this.blocks = List.copyOf(blocks);
		this.rgb = rgb;
		this.brightness = brightness;
		this.blockSelector = WeightedSelector.single(blocks.getFirst());
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
