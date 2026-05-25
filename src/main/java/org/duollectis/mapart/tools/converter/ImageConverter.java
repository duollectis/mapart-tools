package org.duollectis.mapart.tools.converter;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.nativee.NativeHolder;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.RGBUtils;
import org.duollectis.mapart.tools.utils.Schematic;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageConverter {

	private static final int MAP_SIZE = 128;
	private static final double DITHER_ERROR_RATE = 0.8;
	private static final String DEFAULT_SUPPORT_BLOCK_ID = "minecraft:stone";

	private final List<PaletteEntry> palette = new ArrayList<>();
	private final Ditherer ditherer;

	public ImageConverter() {
		ditherer = new Ditherer(NativeHolder.getLib());
	}

	/**
	 * Выполняет только дизеринг изображения и возвращает {@link Ditherer} с результатом.
	 * Схематики не сохраняются — это позволяет сначала показать превью пользователю.
	 * Вызывающий код обязан закрыть возвращённый {@link Ditherer} после использования.
	 *
	 * @param paletteJson JSON-строка с палитрой цветов версии Майнкрафта
	 * @param imageFile   исходное изображение
	 * @param blocksFile  файл со списком разрешённых блоков
	 * @param mapWidth    количество карт по горизонтали
	 * @param mapHeight   количество карт по вертикали
	 * @param algorithm   алгоритм дизеринга
	 * @return {@link Ditherer} с заполненным результатом дизеринга и готовым превью
	 */
	public Ditherer dither(
		String paletteJson,
		File imageFile,
		File blocksFile,
		int mapWidth,
		int mapHeight,
		Ditherer.Algorithm algorithm,
		ImageAdjustments adjustments
	) {
		Set<String> allowedBlocks = loadAllowedBlocks(blocksFile);
		loadPalette(paletteJson, allowedBlocks);

		try {
			BufferedImage image = ImageIO.read(imageFile);
			image = ImageUtils.resizeImage(image, MAP_SIZE * mapWidth, MAP_SIZE * mapHeight);
			image = ImageUtils.applyAdjustments(image, adjustments);

			ditherer.setErrorDiffusionRate(DITHER_ERROR_RATE);
			ditherer.setPalette(palette);
			ditherer.setAlgorithm(algorithm);
			ditherer.processImage(image);
		} catch (Exception e) {
			ditherer.close();
			throw new RuntimeException(e);
		}

		return ditherer;
	}

	/**
	 * Экспортирует схематики из уже готового результата дизеринга.
	 * Вызывается отдельно после {@link #dither} — когда пользователь подтвердил превью.
	 * Палитра передаётся явно, так как этот метод может вызываться из нового экземпляра конвертера.
	 *
	 * @param dithered       двумерный массив индексов палитры (результат дизеринга)
	 * @param palette        палитра, использованная при дизеринге (из {@link Ditherer#getPalette()})
	 * @param outDir         директория для сохранения .nbt файлов
	 * @param mapWidth       количество карт по горизонтали
	 * @param mapHeight      количество карт по вертикали
	 * @param supportBlockId идентификатор блока-опоры для блоков с {@code needSupport = true}
	 */
	public void exportSchematics(
		int[][] dithered,
		List<PaletteEntry> palette,
		File outDir,
		int mapWidth,
		int mapHeight,
		String supportBlockId
	) {
		this.palette.addAll(palette);

		try {
			renderSchematics(dithered, outDir, mapWidth, mapHeight, supportBlockId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Конвертирует изображение в набор схематик (.nbt) для карт Майнкрафта.
	 * Каждая схематика соответствует одной карте размером 128×128 блоков.
	 *
	 * @param paletteJson JSON-строка с палитрой цветов версии Майнкрафта
	 * @param imageFile   исходное изображение для конвертации
	 * @param outDir      директория для сохранения схематик
	 * @param blocksFile  файл со списком разрешённых блоков
	 * @param mapWidth    количество карт по горизонтали
	 * @param mapHeight   количество карт по вертикали
	 */
	public void run(
		String paletteJson,
		File imageFile,
		File outDir,
		File blocksFile,
		int mapWidth,
		int mapHeight
	) {
		Set<String> allowedBlocks = loadAllowedBlocks(blocksFile);
		loadPalette(paletteJson, allowedBlocks);

		try {
			BufferedImage image = ImageIO.read(imageFile);
			image = ImageUtils.resizeImage(image, MAP_SIZE * mapWidth, MAP_SIZE * mapHeight);

			ditherer.setErrorDiffusionRate(DITHER_ERROR_RATE);
			ditherer.setPalette(palette);
			ditherer.setAlgorithm(Ditherer.Algorithm.FLOYD_STEINBERG);
			ditherer.processImage(image);

			renderSchematics(ditherer.getDithered(), outDir, mapWidth, mapHeight, DEFAULT_SUPPORT_BLOCK_ID);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		ditherer.close();
	}

	/**
	 * Загружает палитру из JSON и фильтрует блоки по белому списку.
	 * Для каждого базового цвета генерирует три варианта яркости ({@link Brightness}).
	 * Использует {@link Set} для O(1) проверки принадлежности к белому списку.
	 *
	 * @param data      JSON-строка с маппингом цвет → список блоков
	 * @param whitelist множество разрешённых идентификаторов блоков
	 * @throws RuntimeException если палитра уже была загружена ранее
	 */
	public void loadPalette(String data, Set<String> whitelist) {
		if (!palette.isEmpty()) {
			throw new RuntimeException("Палитра уже загружена!");
		}

		Map<Integer, List<BlockData>> paletteMap = JsonHelper.GSON.fromJson(
			data,
			new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
		);

		// Итерируем по entrySet напрямую — без лишней копии keySet
		paletteMap.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(block -> !whitelist.contains(block.getId()));
			return entry.getValue().isEmpty();
		});

		paletteMap.forEach((color, blocks) -> {
			for (Brightness brightness : Brightness.values()) {
				int scaledColor = RGBUtils.scaleRGB(color, brightness.getModifier());
				palette.add(new PaletteEntry(blocks, scaledColor, brightness));
			}
		});
	}

	private void renderSchematics(
		int[][] dithered,
		File outDir,
		int mapWidth,
		int mapHeight,
		String supportBlockId
	) throws Exception {
		BlockLeveler leveler = new BlockLeveler();
		leveler.setPalette(palette);
		leveler.setSupportBlockId(supportBlockId);

		int maxHeight = 0;

		for (int mx = 0; mx < mapWidth; mx++) {
			for (int my = 0; my < mapHeight; my++) {
				int[][] mapSlice = extractMapSlice(dithered, mx, my);

				leveler.setImage(mapSlice);
				leveler.process(true);

				if (maxHeight < leveler.getProcessedHeight()) {
					maxHeight = leveler.getProcessedHeight();
				}

				int offset = needsSupportOffset(leveler.getProcessed());
				Schematic schematic = new Schematic(MAP_SIZE, maxHeight + offset, MAP_SIZE + 1);
				fillSchematic(schematic, leveler.getProcessed(), supportBlockId, offset);

				File outFile = new File(outDir, "map_%s_%s.nbt".formatted(mx + 1, my + 1));
				schematic.save(outFile.toPath());
			}
		}
	}

	private int[][] extractMapSlice(int[][] dithered, int mx, int my) {
		int[][] slice = new int[MAP_SIZE][MAP_SIZE];

		for (int x = 0; x < MAP_SIZE; x++) {
			for (int y = 0; y < MAP_SIZE; y++) {
				slice[y][x] = dithered[my * MAP_SIZE + y][mx * MAP_SIZE + x];
			}
		}

		return slice;
	}

	private static int needsSupportOffset(LeveledEntry[][] leveled) {
		for (LeveledEntry[] row : leveled) {
			for (LeveledEntry entry : row) {
				if (entry.getEntry().getBlocks().getFirst().isNeedSupport() && entry.getLevel() == 0) {
					return 1;
				}
			}
		}

		return 0;
	}

	private void fillSchematic(Schematic schematic, LeveledEntry[][] leveled, String supportBlockId, int offset) {
		BlockData supportBlock = new BlockData(supportBlockId);

		for (int x = 0; x < leveled[0].length; x++) {
			for (int y = 0; y < leveled.length; y++) {
				LeveledEntry entry = leveled[y][x];
				BlockData block = entry.getEntry().getBlocks().getFirst();
				int level = entry.getLevel() + offset;

				schematic.setBlock(x, level, y, block);

				if (block.isNeedSupport()) {
					schematic.setBlock(x, level - 1, y, supportBlock);
				}
			}
		}
	}

	private static Set<String> loadAllowedBlocks(File blocksFile) {
		try (FileInputStream stream = new FileInputStream(blocksFile)) {
			String content = new String(stream.readAllBytes());
			Set<String> blocks = new HashSet<>();

			for (String line : content.split("\n")) {
				String trimmed = line.strip();

				if (trimmed.isBlank()) {
					continue;
				}

				blocks.add(trimmed);
			}

			return blocks;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
