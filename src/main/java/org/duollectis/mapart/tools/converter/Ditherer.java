package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.duollectis.mapart.tools.nativee.NativeBridge;
import org.duollectis.mapart.tools.nativee.NativeMethod;
import org.duollectis.mapart.tools.nativee.NativeWrapper;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Ditherer implements AutoCloseable {

	private static final double DEFAULT_ERROR_DIFFUSION_RATE = 1.0;
	private static final double DEFAULT_NOISE_LEVEL = 0.0;

	private final ImageDithererWrapper nativeWrapper;

	@Setter
	private double errorDiffusionRate = DEFAULT_ERROR_DIFFUSION_RATE;

	@Setter
	private double noiseLevel = DEFAULT_NOISE_LEVEL;

	@Getter
	@Setter
	@NonNull
	private List<PaletteEntry> palette = List.of();

	@Setter
	@NonNull
	private Algorithm algorithm = Algorithm.FLOYD_STEINBERG;

	@Getter
	private int ditherTime;

	@Getter
	private int[][] dithered = new int[0][0];

	/**
	 * Материализованный результат: для каждого пикселя уже выбран конкретный блок
	 * согласно весовому распределению. Заполняется сразу после нативного дизеринга.
	 * Гарантирует что список блоков, превью и экспорт используют одни и те же блоки.
	 */
	@Getter
	private BlockData[][] resolved = new BlockData[0][0];

	public Ditherer(File lib) {
		nativeWrapper = NativeBridge.create(ImageDithererWrapper.class, lib);
	}

	public void processImage(BufferedImage image) {
		int height = image.getHeight();
		int width = image.getWidth();
		dithered = new int[height][width];
		resolved = new BlockData[height][width];

		if (palette.isEmpty()) {
			return;
		}

		byte[] pixels = extractBgrPixels(image);
		int[] paletteRgb = palette.stream().mapToInt(PaletteEntry::getRgb).toArray();

		long startTime = System.currentTimeMillis();

		nativeWrapper.dither(
			paletteRgb,
			paletteRgb.length,
			pixels,
			width,
			height,
			dithered,
			algorithm.getId(),
			errorDiffusionRate,
			noiseLevel
		);

		ditherTime = Math.toIntExact(System.currentTimeMillis() - startTime);

		materializeBlocks(height, width);
	}

	private void materializeBlocks(int height, int width) {
		Map<Integer, WeightedSelector<BlockData>> freshSelectors = buildFreshSelectors();
		Map<Integer, Integer> perEntryCounters = new LinkedHashMap<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int paletteIndex = dithered[y][x];
				int counter = perEntryCounters.merge(paletteIndex, 1, Integer::sum) - 1;
				resolved[y][x] = freshSelectors.get(paletteIndex).pick(counter);
			}
		}
	}

	/**
	 * Создаёт превью результата дизеринга — изображение, где каждый пиксель
	 * заменён цветом ближайшего элемента палитры.
	 */
	public BufferedImage createPreview() {
		if (dithered == null) {
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		}

		int width = dithered[0].length;
		int height = dithered.length;
		BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				preview.setRGB(x, y, palette.get(dithered[y][x]).getRgb());
			}
		}

		return preview;
	}

	/**
	 * Подсчитывает количество использований каждого блока по материализованному результату.
	 * Числа стабильны — не меняются при повторных вызовах.
	 * Результат отсортирован по убыванию количества.
	 *
	 * @return карта блок → количество, отсортированная по убыванию
	 */
	public Map<BlockData, Integer> getUsedBlockCounts() {
		Map<BlockData, Integer> counts = new LinkedHashMap<>();

		for (BlockData[] row : resolved) {
			for (BlockData block : row) {
				counts.merge(block, 1, Integer::sum);
			}
		}

		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.<BlockData, Integer>comparingByValue().reversed())
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(a, b) -> a,
				LinkedHashMap::new
			));
	}

	/**
	 * Считает количество пикселей, под которыми при экспорте будет подложен блок опоры.
	 * Читает из материализованного результата — стабильный счётчик.
	 *
	 * @return количество блоков опоры, необходимых для схематики
	 */
	public int getSupportBlockCount() {
		int count = 0;

		for (BlockData[] row : resolved) {
			for (BlockData block : row) {
				if (block.isNeedSupport()) {
					count++;
				}
			}
		}

		return count;
	}

	private Map<Integer, WeightedSelector<BlockData>> buildFreshSelectors() {
		Map<Integer, WeightedSelector<BlockData>> result = new LinkedHashMap<>();

		for (int i = 0; i < palette.size(); i++) {
			result.put(i, palette.get(i).getBlockSelector().copy());
		}

		return result;
	}

	@Override
	public void close() {
		nativeWrapper.close();
	}

	/**
	 * Конвертирует изображение в массив байт формата BGR,
	 * который ожидает нативная функция дизеринга.
	 */
	public static byte[] extractBgrPixels(BufferedImage image) {
		BufferedImage bgrImage = new BufferedImage(
			image.getWidth(),
			image.getHeight(),
			BufferedImage.TYPE_3BYTE_BGR
		);

		Graphics2D graphics = bgrImage.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();

		return ((DataBufferByte) bgrImage.getRaster().getDataBuffer()).getData();
	}

	@Getter
	@RequiredArgsConstructor
	public enum Algorithm {
		// Диффузия ошибки
		FLOYD_STEINBERG(0),
		STUCKI(1),
		JJN(2),
		BURKES(3),
		SIERRA3(4),
		SIERRA_LITE(5),
		ATKINSON(6),
		SIERRA2(7),
		FILTER_LITE(8),

		// Упорядоченный дизеринг Байера
		BAYER_2X2(9),
		BAYER_4X4(10),
		BAYER_8X8(11),

		// Без дизеринга
		NONE(12);

		private final int id;
	}

	private interface ImageDithererWrapper extends NativeWrapper {

		@NativeMethod
		void dither(
			int[] palette,
			int paletteSize,
			byte[] pixels,
			int width,
			int height,
			int[][] dithered,
			int algorithm,
			double errorDiffusionRate,
			double noiseLevel
		);
	}
}
