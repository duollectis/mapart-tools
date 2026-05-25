package org.duollectis.mapart.tools.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class LeveledEntry {

	private final PaletteEntry entry;
	private int level;

	public void addLevel(int delta) {
		level += delta;
	}
}
