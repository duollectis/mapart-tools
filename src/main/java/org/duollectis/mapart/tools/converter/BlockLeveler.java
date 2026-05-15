package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class BlockLeveler {

	@Setter
	private int[][] image;

	@Setter
	private List<PaletteEntry> palette = List.of();

	@Getter
	private LeveledEntry[][] processed;

	@Getter
	private int processedHeight;

	public void process(boolean normalize) {

		int width = image[0].length;
		int height = image.length;

		processed = new LeveledEntry[height + 1][width];


		PaletteEntry stoneEntry = null;

		for (PaletteEntry _entry : palette) {
			for (BlockData data : _entry.getBlocks()) {
				if ("minecraft:stone".equals(data.getId())) {
					stoneEntry = _entry;
					break;
				}
			}
		}

		int minLevel = 9999;
		int maxLevel = 0;

		for (int x = 0; x < width; x++) {

			int level = 0;
			for (int y = height - 1; y >= 0; y--) {
				PaletteEntry entry = palette.get(image[y][x]);

				if (y < height - 1) {
					Brightness brightness = processed[y + 2][x].getEntry().getBrightness();

					switch (brightness) {
						case LOW -> level++;
						case HIGH -> level--;
					}
				}

				if (minLevel > level) {
					minLevel = level;
				}

				if (maxLevel < level) {
					maxLevel = level;
				}

				processed[y + 1][x] = new LeveledEntry(entry, level);
			}

			Brightness brightness = processed[1][x].getEntry().getBrightness();

			switch (brightness) {
				case LOW -> level++;
				case HIGH -> level--;
			}

			if (minLevel > level) {
				minLevel = level;
			}

			if (maxLevel < level) {
				maxLevel = level;
			}

			if (brightness == Brightness.HIGH) {
				processed[0][x] = new LeveledEntry(
						new PaletteEntry(List.of(new BlockData("minecraft:air")), 0, Brightness.HIGH),
						level
				);
			}
			else {
				processed[0][x] = new LeveledEntry(stoneEntry, level);
			}

			if (normalize) {
				for (int y = 0; y <= height; y++) {
					processed[y][x].addLevel(-minLevel);
				}
				maxLevel -= minLevel;
				minLevel = 0;
			}
		}

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				processed[y][x].addLevel(-minLevel);
			}
		}

		processedHeight = maxLevel - minLevel + 1;
	}
}
