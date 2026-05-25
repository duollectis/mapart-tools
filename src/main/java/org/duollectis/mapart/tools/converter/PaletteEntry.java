package org.duollectis.mapart.tools.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PaletteEntry {

	private final List<BlockData> blocks;
	private final int rgb;
	private final Brightness brightness;
}
