package org.duollectis.mapart.tools.converter;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.nativee.NativeHolder;
import org.duollectis.mapart.tools.utils.Schematic;
import org.duollectis.mapart.tools.utils.FileUtils;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.RGBUtils;
import org.duollectis.mapart.tools.utils.image.ImageUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class ImageConverter {

	private static final int MAP_SIZE = 128;

	private final List<PaletteEntry> palette = new ArrayList<>();

	private final Ditherer ditherer;

	public ImageConverter() {
		ditherer = new Ditherer(NativeHolder.getLib());
	}

	public void run(String paletteData, File imageFile, File outPath, File blocksPath, int width, int height) {


		List<String> blocks = new ArrayList<>();

		try (FileInputStream stream = new FileInputStream(blocksPath)) {
			String list = new String(stream.readAllBytes());
			for (String s : list.split("\n")) {
				s = s.strip();
				if (!s.isBlank()) {
					blocks.add(s);
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

		loadPalette(paletteData, blocks);

		try {
			BufferedImage image = ImageIO.read(imageFile);
			image = ImageUtils.resizeImage(image, MAP_SIZE * width, MAP_SIZE * height);

			ditherer.setErrorDiffusionRate(0.8);
			ditherer.setPalette(palette);
			ditherer.setAlgorithm(Ditherer.Algorithm.FLOYD_STEINBERG);
			ditherer.processImage(image);

			int[][] dithered = ditherer.getDithered();

			BlockLeveler leveler = new BlockLeveler();
			leveler.setPalette(palette);

			int maxHeght = 0;

			Schematic schematic = new Schematic(MAP_SIZE * height * width, maxHeght, MAP_SIZE + 1);

			int count = 0;

			for (int mx = 0; mx < width; mx++) {
				for (int my = 0; my < height; my++) {
					int[][] map = new int[MAP_SIZE][MAP_SIZE];
					for (int x = 0; x < MAP_SIZE; x++) {
						for (int y = 0; y < MAP_SIZE; y++) {
							map[y][x] = dithered[my * MAP_SIZE + y][mx * MAP_SIZE + x];
						}
					}

					leveler.setImage(map);
					leveler.process(true);

					if (maxHeght < leveler.getProcessedHeight()) {
						maxHeght = leveler.getProcessedHeight();
						schematic.setHeight(maxHeght);
					}

					LeveledEntry[][] leveled = leveler.getProcessed();

					for (int x = 0; x < leveled[0].length; x++) {
						for (int y = 0; y < leveled.length; y++) {

							LeveledEntry entry = leveled[y][x];
							schematic.setBlock(
									MAP_SIZE * count + x,
									entry.getLevel(),
									y,
									entry.getEntry().getBlocks().getFirst()
							);
						}
					}

					count++;

				}
			}


			schematic.save(new File(outPath, "map.nbt").toPath());

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		ditherer.close();
	}


	public void loadPalette(@NotNull String data, List<String> whitelist) {
		if (palette.isEmpty()) {
			Map<Integer, List<BlockData>> paletteMap = new HashMap<>(JsonHelper.GSON.fromJson(
					data,
					new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
			));

			for (int c : new HashSet<>(paletteMap.keySet())) {
				//				paletteMap.get(c).removeIf(b -> !whitelist.contains(b.getId()));

				if (paletteMap.get(c).isEmpty()) {
					paletteMap.remove(c);
				}
			}

			paletteMap.forEach((color, blocks) -> {
				for (Brightness brightness : Brightness.values()) {
					int sc = RGBUtils.scaleRGB(color, brightness.getModifier());
					palette.add(new PaletteEntry(blocks, sc, brightness));
				}
			});

			return;
		}

		throw new RuntimeException("Palette loaded already!");
	}
}
