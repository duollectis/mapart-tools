package org.duollectis.mapart.tools.converter;

import com.google.gson.reflect.TypeToken;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.converter.schematic.SchematicWriter;
import org.duollectis.mapart.tools.nativee.NativeHolder;
import org.duollectis.mapart.tools.utils.JsonHelper;
import org.duollectis.mapart.tools.utils.RGBUtils;
import org.duollectis.mapart.tools.utils.image.FitResult;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

public class ImageConverter {

	private static final int MAP_SIZE = 128;
	private static final String DEFAULT_SUPPORT_BLOCK_ID = "minecraft:stone";

	private final List<PaletteEntry> palette = new ArrayList<>();
	private final Ditherer ditherer;

	public ImageConverter() {
		ditherer = new Ditherer(NativeHolder.getLib());
	}

	/** Конструктор для unit-тестов: не инициализирует нативный дизерер. */
	ImageConverter(Ditherer ditherer) {
		this.ditherer = ditherer;
	}

	/**
	 * Выполняет только дизеринг изображения и возвращает {@link Ditherer} с результатом.
	 * Схематики не сохраняются — это позволяет сначала показать превью пользователю.
	 * Вызывающий код обязан закрыть возвращённый {@link Ditherer} после использования.
	 * <p>
	 * Прогресс публикуется через {@code onStage} на каждом значимом шаге:
	 * загрузка палитры (0–4%), подготовка изображения (5–9%), дизеринг (10–100%).
	 *
	 * @param paletteJson    JSON-строка с палитрой цветов версии Майнкрафта
	 * @param imageFile      исходное изображение
	 * @param blocksFile     файл со списком разрешённых блоков
	 * @param mapWidth       количество карт по горизонтали
	 * @param mapHeight      количество карт по вертикали
	 * @param algorithm      алгоритм дизеринга
	 * @param adjustments    коррекция изображения
	 * @param ditherSettings настройки алгоритма дизеринга
	 * @param cropSettings   настройки кропа/подгонки изображения
	 * @param onStage        колбэк прогресса — вызывается на каждом этапе с текстом и процентом
	 * @return {@link Ditherer} с заполненным результатом дизеринга и готовым превью
	 */
	/**
	 * Выполняет только дизеринг изображения и возвращает {@link Ditherer} с результатом.
	 * Схематики не сохраняются — это позволяет сначала показать превью пользователю.
	 * Вызывающий код обязан закрыть возвращённый {@link Ditherer} после использования.
	 * <p>
	 * Прогресс публикуется через {@code onStage} на каждом значимом шаге:
	 * загрузка палитры (0–4%), подготовка изображения (5–9%), дизеринг (10–100%).
	 * <p>
	 * Отмена реализована через {@code cancelCheck}: колбэк прогресса возвращает {@code 1}
	 * в нативный C++ код, который немедленно прерывает дизеринг. После этого
	 * бросается {@link CancellationException}, которую воркер конвертации перехватывает.
	 *
	 * @param cancelCheck поставщик флага отмены — вызывается на каждом шаге прогресса
	 */
	public Ditherer dither(
		String paletteJson,
		File imageFile,
		File blocksFile,
		int mapWidth,
		int mapHeight,
		DitherAlgorithm algorithm,
		ImageAdjustments adjustments,
		DitherSettings ditherSettings,
		CropSettings cropSettings,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		StaircaseMode staircaseMode,
		Consumer<ConversionStage> onStage,
		BooleanSupplier cancelCheck
	) {
		Set<String> allowedBlocks = loadAllowedBlocks(blocksFile);
		loadPalette(paletteJson, allowedBlocks, blockSelectors, staircaseMode, onStage);

		try {
			onStage.accept(new ConversionStage(ConversionStage.Phase.PREPARING_IMAGE, 5));
			BufferedImage rawImage = ImageIO.read(imageFile);

			onStage.accept(new ConversionStage(ConversionStage.Phase.PREPARING_IMAGE, 6));
			BufferedImage adjusted = ImageUtils.applyAdjustments(rawImage, adjustments);

			onStage.accept(new ConversionStage(ConversionStage.Phase.PREPARING_IMAGE, 8));
			FitResult fit = ImageUtils.prepareImage(adjusted, MAP_SIZE * mapWidth, MAP_SIZE * mapHeight, cropSettings);
			BufferedImage image = fit.image();

			onStage.accept(new ConversionStage(ConversionStage.Phase.DITHERING, 10));

			ditherer.setErrRateR(ditherSettings.errRateR());
			ditherer.setErrRateG(ditherSettings.errRateG());
			ditherer.setErrRateB(ditherSettings.errRateB());
			ditherer.setNoiseLevel(ditherSettings.noiseLevel());
			ditherer.setColorMetric(ditherSettings.colorMetric());
			ditherer.setPalette(palette);
			ditherer.setAlgorithm(algorithm);
			ditherer.setOnProgress(nativePercent -> {
				if (cancelCheck.getAsBoolean()) {
					return 1;
				}

				int mapped = 10 + nativePercent * 90 / 100;
				onStage.accept(new ConversionStage(ConversionStage.Phase.DITHERING, mapped));
				return 0;
			});
			ditherer.processImage(image);

			if (cancelCheck.getAsBoolean()) {
				ditherer.close();
				throw new CancellationException();
			}
		} catch (CancellationException e) {
			throw e;
		} catch (Exception e) {
			ditherer.close();
			throw new RuntimeException(e);
		}

		return ditherer;
	}

	/**
	 * Экспортирует схематики из уже материализованного результата дизеринга.
	 * Вызывается отдельно после {@link #dither} — когда пользователь подтвердил превью.
	 * Палитра передаётся явно для расчёта высот; конкретные блоки берутся из {@code resolved}.
	 *
	 * @param dithered        двумерный массив индексов палитры (для расчёта высот в BlockLeveler)
	 * @param resolved        материализованный массив блоков (из {@link Ditherer#getResolved()})
	 * @param palette         палитра, использованная при дизеринге (из {@link Ditherer#getPalette()})
	 * @param outDir          директория для сохранения файлов схематик
	 * @param mapWidth        количество карт по горизонтали
	 * @param mapHeight       количество карт по вертикали
	 * @param supportSettings настройки блоков-опор (список блоков с весами и режим распределения)
	 * @param format          формат экспорта: NBT (.nbt) или LITEMATIC (.litematic)
	 */
	public void exportSchematics(
		int[][] dithered,
		BlockData[][] resolved,
		List<PaletteEntry> palette,
		File outDir,
		int mapWidth,
		int mapHeight,
		SupportBlockSettings supportSettings,
		SchematicFormat format,
		StaircaseMode staircaseMode
	) {
		this.palette.addAll(palette);

		try {
			renderSchematics(dithered, resolved, outDir, mapWidth, mapHeight, supportSettings, format, staircaseMode);
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
		loadPalette(paletteJson, allowedBlocks, Map.of(), StaircaseMode.STAIRCASE);

		try {
			BufferedImage image = ImageIO.read(imageFile);
			image = ImageUtils.resizeImage(image, MAP_SIZE * mapWidth, MAP_SIZE * mapHeight);

			DitherSettings defaults = DitherSettings.defaults();
				ditherer.setErrRateR(defaults.errRateR());
				ditherer.setErrRateG(defaults.errRateG());
				ditherer.setErrRateB(defaults.errRateB());
				ditherer.setNoiseLevel(defaults.noiseLevel());
			ditherer.setPalette(palette);
			ditherer.setAlgorithm(DitherAlgorithm.FLOYD_STEINBERG);
			ditherer.processImage(image);

			renderSchematics(
				ditherer.getDithered(),
				ditherer.getResolved(),
				outDir,
				mapWidth,
				mapHeight,
				SupportBlockSettings.single(DEFAULT_SUPPORT_BLOCK_ID),
				SchematicFormat.NBT,
				StaircaseMode.STAIRCASE
			);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		ditherer.close();
	}

	/**
	 * Загружает палитру из JSON, фильтрует блоки по белому списку и применяет
	 * пользовательские селекторы весов для каждого базового blockId.
	 * Для каждого базового цвета генерирует три варианта яркости ({@link Brightness}).
	 *
	 * @param data           JSON-строка с маппингом цвет → список блоков
	 * @param whitelist      множество разрешённых идентификаторов блоков
	 * @param blockSelectors карта пользовательских селекторов: ключ — базовый blockId
	 * @throws RuntimeException если палитра уже была загружена ранее
	 */
	public void loadPalette(
		String data,
		Set<String> whitelist,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		StaircaseMode staircaseMode
	) {
		loadPalette(data, whitelist, blockSelectors, staircaseMode, stage -> {});
	}

	/**
	 * Загружает палитру с публикацией прогресса через {@code onStage}.
	 * Прогресс публикуется в диапазоне 0–4% по мере обработки каждого цвета палитры.
	 */
	public void loadPalette(
		String data,
		Set<String> whitelist,
		Map<String, WeightedSelector<BlockData>> blockSelectors,
		StaircaseMode staircaseMode,
		Consumer<ConversionStage> onStage
	) {
		if (!palette.isEmpty()) {
			throw new RuntimeException("Палитра уже загружена!");
		}

		Map<Integer, List<BlockData>> paletteMap = JsonHelper.GSON.fromJson(
			data,
			new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
		);

		onStage.accept(new ConversionStage(ConversionStage.Phase.LOADING_PALETTE, 1));

		// Итерируем по entrySet напрямую — без лишней копии keySet
		paletteMap.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(block ->
				!whitelist.contains(block.getId()) && !whitelist.contains(block.getUniqueKey())
			);
			return entry.getValue().isEmpty();
		});

		onStage.accept(new ConversionStage(ConversionStage.Phase.LOADING_PALETTE, 2));

		var allowedBrightnesses = staircaseMode.getAllowedBrightnesses();
		int total = paletteMap.size();
		int[] processed = {0};

		paletteMap.forEach((color, blocks) -> {
			String baseId = blocks.getFirst().getId();

			// Каждая разрешённая яркость получает независимую копию селектора — свои счётчики SEQUENTIAL
			for (Brightness brightness : allowedBrightnesses) {
				String brightnessKey = baseId + "#" + brightness.name();
				WeightedSelector<BlockData> selector = blockSelectors.containsKey(brightnessKey)
					? blockSelectors.get(brightnessKey)
					: blockSelectors.containsKey(baseId)
						? blockSelectors.get(baseId)
						: buildEqualSelector(blocks);
				int scaledColor = RGBUtils.scaleRGB(color, brightness.getModifier());
				int baseColorId = MapColorTable.resolveBaseId(color);
				int mapColorId = baseColorId >= 0 ? (baseColorId << 2) | brightness.ordinal() : -1;
				palette.add(new PaletteEntry(blocks, scaledColor, brightness, selector.copy(), mapColorId));
			}

			processed[0]++;
			int percent = 2 + processed[0] * 2 / total;
			onStage.accept(new ConversionStage(ConversionStage.Phase.LOADING_PALETTE, percent));
		});
	}

	public int getPaletteSize() {
		return palette.size();
	}

	private static WeightedSelector<BlockData> buildEqualSelector(List<BlockData> blocks) {
		List<WeightedSelector.Entry<BlockData>> entries = blocks.stream()
			.map(b -> new WeightedSelector.Entry<>(b, 100))
			.toList();

		return new WeightedSelector<>(entries, WeightedSelector.Mode.SEQUENTIAL);
	}

	private void renderSchematics(
		int[][] dithered,
		BlockData[][] resolved,
		File outDir,
		int mapWidth,
		int mapHeight,
		SupportBlockSettings supportSettings,
		SchematicFormat format,
		StaircaseMode staircaseMode
	) throws Exception {
		String primarySupportId = supportSettings.isEmpty()
			? DEFAULT_SUPPORT_BLOCK_ID
			: supportSettings.getEntries().getFirst().blockId();

		BlockLeveler leveler = new BlockLeveler();
		leveler.setPalette(palette);
		leveler.setSupportBlockId(primarySupportId);

		for (int mx = 0; mx < mapWidth; mx++) {
			for (int my = 0; my < mapHeight; my++) {
				int[][] mapSlice = extractMapSlice(dithered, mx, my);
				BlockData[][] resolvedSlice = extractResolvedSlice(resolved, mx, my);

				leveler.setImage(mapSlice);
				leveler.process(staircaseMode.isNormalize(), staircaseMode);

				int schematicHeight = leveler.getProcessedHeight();
				int offset = needsSupportOffset(leveler.getProcessed());
				String mapName = "map_%s_%s".formatted(mx + 1, my + 1);

				SchematicWriter writer = format.createWriter(
					MAP_SIZE,
					schematicHeight + offset,
					MAP_SIZE + 1,
					mapName
				);

				fillWriter(writer, leveler.getProcessed(), resolvedSlice, supportSettings, offset);

				File outFile = new File(outDir, mapName + format.getExtension());
				writer.save(outFile.toPath());
			}
		}
	}

	private int[][] extractMapSlice(int[][] dithered, int mx, int my) {
		int[][] slice = new int[MAP_SIZE][MAP_SIZE];

		for (int x = 0; x < MAP_SIZE; x++) {
			for (int y = 0; y < MAP_SIZE; y++) {
				int idx = dithered[my * MAP_SIZE + y][mx * MAP_SIZE + x];
				// Пустые пиксели вне clip_rect (sentinel -1) заменяем индексом 0
				// для корректного расчёта высот в BlockLeveler
				slice[y][x] = Math.max(idx, 0);
			}
		}

		return slice;
	}

	private BlockData[][] extractResolvedSlice(BlockData[][] resolved, int mx, int my) {
		BlockData[][] slice = new BlockData[MAP_SIZE][MAP_SIZE];

		for (int x = 0; x < MAP_SIZE; x++) {
			for (int y = 0; y < MAP_SIZE; y++) {
				slice[y][x] = resolved[my * MAP_SIZE + y][mx * MAP_SIZE + x];
			}
		}

		return slice;
	}

	private static int needsSupportOffset(LeveledEntry[][] leveled) {
		for (LeveledEntry[] row : leveled) {
			for (LeveledEntry entry : row) {
				// Проверяем первый блок как представитель цвета — достаточно для определения нужды в опоре,
				// так как все варианты одного цвета имеют одинаковый флаг needSupport
				if (entry.getEntry().getBlocks().getFirst().isNeedSupport() && entry.getLevel() == 0) {
					return 1;
				}
			}
		}

		return 0;
	}

	private void fillWriter(
		SchematicWriter writer,
		LeveledEntry[][] leveled,
		BlockData[][] resolvedSlice,
		SupportBlockSettings supportSettings,
		int offset
	) {
		AtomicInteger supportIndex = new AtomicInteger(0);

		for (int x = 0; x < leveled[0].length; x++) {
			// y=0 — технический ряд опоры/воздуха из BlockLeveler, блок берётся из leveled, не из resolvedSlice
			LeveledEntry topEntry = leveled[0][x];
			BlockData topBlock = topEntry.getEntry().getBlocks().getFirst();
			writer.setBlock(x, topEntry.getLevel() + offset, 0, topBlock);

			// y=1..128 — реальные блоки карты; resolvedSlice индексируется как [y-1][x]
			for (int y = 1; y < leveled.length; y++) {
				LeveledEntry entry = leveled[y][x];
				BlockData block = resolvedSlice[y - 1][x];
				int level = entry.getLevel() + offset;

				// Пустые пиксели вне clip_rect (null) заменяем блоком опоры по умолчанию
				BlockData effectiveBlock = block != null ? block : new BlockData(DEFAULT_SUPPORT_BLOCK_ID);
				writer.setBlock(x, level, y, effectiveBlock);

				if (effectiveBlock.isNeedSupport()) {
					String supportId = supportSettings.pickBlock(supportIndex.getAndIncrement());
					BlockData supportBlock = new BlockData(supportId);
					writer.setBlock(
						x,
						level - 1,
						y,
						supportBlock.isSlab() ? supportBlock.asTopSlab() : supportBlock
					);
				}
			}
		}
	}

	/**
	 * Рендерит превью карт-арта из результата импорта схематики.
	 * Яркость каждого пикселя определяется по разнице Y-уровней текущего блока и блока
	 * предыдущей строки (z-1): выше → HIGH (светлее), ниже → LOW (темнее), равно → NORMAL.
	 * Это воспроизводит реальное затенение карты Майнкрафта без повторного запуска BlockLeveler.
	 *
	 * @param importResult результат импорта схематики с верхним слоем блоков и Y-уровнями
	 * @param paletteJson  JSON-строка с палитрой цветов версии Майнкрафта
	 * @return изображение превью размером sizeX × sizeZ
	 */
	/**
	 * Перегрузка для пакетного импорта: принимает уже построенную карту цветов блоков,
	 * чтобы не парсить и не пересчитывать палитру для каждого файла отдельно.
	 */
	public static BufferedImage renderPreview(
		SchematicImportResult importResult,
		Map<String, Map<Brightness, Integer>> blockColorMap
	) {
		BlockData[][] blocks = importResult.blocks();
		int[][] topLevels = importResult.topLevels();
		int height = blocks.length;
		int width = height > 0 ? blocks[0].length : 0;
		int mapHeight = importResult.mapHeight();
		int tileRows = mapHeight > 0 ? height / mapHeight : height;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlockData block = blocks[y][x];

				if (block == null) {
					image.setRGB(x, y, Color.GRAY.getRGB());
					continue;
				}

				Brightness brightness = resolveBrightness(topLevels, y, x, tileRows);
				Map<Brightness, Integer> colorsByBrightness = blockColorMap.get(block.getId());

				int color = colorsByBrightness == null
					? Color.GRAY.getRGB()
					: colorsByBrightness.getOrDefault(brightness, colorsByBrightness.getOrDefault(Brightness.NORMAL, Color.GRAY.getRGB()));

				image.setRGB(x, y, color);
			}
		}

		return image;
	}

	public static BufferedImage renderPreview(SchematicImportResult importResult, String paletteJson) {
		Map<Integer, List<BlockData>> paletteMap = JsonHelper.GSON.fromJson(
			paletteJson,
			new TypeToken<Map<Integer, List<BlockData>>>() {}.getType()
		);

		Map<String, Map<Brightness, Integer>> blockColorMap = buildBlockColorMap(paletteMap);

		BlockData[][] blocks = importResult.blocks();
		int[][] topLevels = importResult.topLevels();
		int height = blocks.length;
		int width = height > 0 ? blocks[0].length : 0;
		int mapHeight = importResult.mapHeight();
		int tileRows = mapHeight > 0 ? height / mapHeight : height;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlockData block = blocks[y][x];

				if (block == null) {
					image.setRGB(x, y, Color.GRAY.getRGB());
					continue;
				}

				Brightness brightness = resolveBrightness(topLevels, y, x, tileRows);
				Map<Brightness, Integer> colorsByBrightness = blockColorMap.get(block.getId());

				int color = colorsByBrightness == null
					? Color.GRAY.getRGB()
					: colorsByBrightness.getOrDefault(brightness, colorsByBrightness.getOrDefault(Brightness.NORMAL, Color.GRAY.getRGB()));

				image.setRGB(x, y, color);
			}
		}

		return image;
	}

	/**
	 * Определяет яркость пикселя карты по разнице Y-уровней текущей и предыдущей строки.
	 * {@code topLevels} имеет размер [totalHeight+1][width]: индекс 0 — опорный ряд первого тайла,
	 * индексы 1..totalHeight — реальные строки карты.
	 * <p>
	 * На границах тайлов (строки кратные {@code tileRows}) возвращает NORMAL, так как
	 * Y-уровни соседних тайлов нормализованы независимо и несопоставимы.
	 */
	private static Brightness resolveBrightness(int[][] topLevels, int y, int x, int tileRows) {
		if (topLevels == null || y + 1 >= topLevels.length || x >= topLevels[y + 1].length) {
			return Brightness.NORMAL;
		}

		boolean isFirstRowOfTile = tileRows > 0 && y % tileRows == 0 && y > 0;
		boolean isLastRowOfTile = tileRows > 0 && (y + 1) % tileRows == 0;

		if (isFirstRowOfTile || isLastRowOfTile) {
			return Brightness.NORMAL;
		}

		int currentY = topLevels[y + 1][x];
		int prevY = topLevels[y][x];

		if (currentY > prevY) {
			return Brightness.HIGH;
		}

		return currentY < prevY ? Brightness.LOW : Brightness.NORMAL;
	}

	public static Map<String, Map<Brightness, Integer>> buildBlockColorMapPublic(Map<Integer, List<BlockData>> paletteMap) {
		return buildBlockColorMap(paletteMap);
	}

	private static Map<String, Map<Brightness, Integer>> buildBlockColorMap(Map<Integer, List<BlockData>> paletteMap) {
		Map<String, Map<Brightness, Integer>> colorMap = new HashMap<>();

		paletteMap.forEach((baseColor, blocks) -> {
			for (BlockData block : blocks) {
				colorMap.computeIfAbsent(block.getId(), k -> new HashMap<>()).putIfAbsent(
					Brightness.LOW,
					RGBUtils.scaleRGB(baseColor, Brightness.LOW.getModifier())
				);
				colorMap.get(block.getId()).putIfAbsent(
					Brightness.NORMAL,
					RGBUtils.scaleRGB(baseColor, Brightness.NORMAL.getModifier())
				);
				colorMap.get(block.getId()).putIfAbsent(
					Brightness.HIGH,
					RGBUtils.scaleRGB(baseColor, Brightness.HIGH.getModifier())
				);
			}
		});

		return colorMap;
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
