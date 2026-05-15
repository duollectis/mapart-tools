package org.duollectis.mapart.tools.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class LeveledEntry {

	private final PaletteEntry entry;
	private int level;

	public void addLevel(int level) {
		this.level += level;
	}
}
