package org.duollectis.mapart.tools.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class PaletteEntry {

	private final List<BlockData> blocks;
	private final int rgb;
	private final Brightness brightness;

}
