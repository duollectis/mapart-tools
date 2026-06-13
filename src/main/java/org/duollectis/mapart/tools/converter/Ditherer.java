package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.duollectis.mapart.tools.nativee.NativeBridge;
import org.duollectis.mapart.tools.nativee.NativeMethod;
import org.duollectis.mapart.tools.nativee.NativeWrapper;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

public class Ditherer implements AutoCloseable {

	private static final double DEFAULT_ERR_RATE_CHANNEL = 1.0;
	private static final double DEFAULT_NOISE_LEVEL = 0.0;

	private final ImageDithererWrapper nativeWrapper;
	private final Arena callbackArena = Arena.ofShared();

	@Setter
	private double errRateR = DEFAULT_ERR_RATE_CHANNEL;

	@Setter
	private double errRateG = DEFAULT_ERR_RATE_CHANNEL;

	@Setter
	private double errRateB = DEFAULT_ERR_RATE_CHANNEL;

	@Setter
	private double noiseLevel = DEFAULT_NOISE_LEVEL;

	@Setter
	@NonNull
	private ColorMetric colorMetric = ColorMetric.LAB;

	@Getter
	@Setter
	@NonNull
	private List<PaletteEntry> palette = List.of();

	@Setter
	@NonNull
	private DitherAlgorithm algorithm = DitherAlgorithm.FLOYD_STEINBERG;

	@Setter
	private IntUnaryOperator onProgress;

	private int clipX;
	private int clipY;
	private int clipW;
	private int clipH;

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

	/**
	 * Создаёт upcall-stub для передачи Java-колбэка прогресса в нативный C++ код.
	 * Колбэк принимает текущий прогресс (0–100) и возвращает 0 (продолжать) или 1 (отмена).
	 * Stub живёт в {@code callbackArena} и освобождается при закрытии дизерера.
	 * Если {@code onProgress} не задан — возвращает нулевой указатель (NULL callback).
	 */
	private MemorySegment buildProgressStub() {
		if (onProgress == null) {
			return MemorySegment.NULL;
		}

		try {
			FunctionDescriptor descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
			var handle = MethodHandles.lookup().findVirtual(
				IntUnaryOperator.class,
				"applyAsInt",
				MethodType.methodType(int.class, int.class)
			).bindTo(onProgress);

			return Linker.nativeLinker().upcallStub(handle, descriptor, callbackArena);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException("Не удалось создать upcall-stub для прогресса", e);
		}
	}

	public void setClipRect(int x, int y, int w, int h) {
		clipX = x;
		clipY = y;
		clipW = w;
		clipH = h;
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
			errRateR,
			errRateG,
			errRateB,
			noiseLevel,
			colorMetric.getId(),
			buildProgressStub(),
			clipX,
			clipY,
			clipW,
			clipH
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

				if (paletteIndex < 0) {
					resolved[y][x] = null;
					continue;
				}

				int counter = perEntryCounters.merge(paletteIndex, 1, Integer::sum) - 1;
				resolved[y][x] = freshSelectors.get(paletteIndex).pick(counter);
			}
		}
	}

	/**
	 * Создаёт превью результата дизеринга — изображение, где каждый пиксель
	 * заменён цветом ближайшего элемента палитры.
	 * Пиксели вне clip_rect (sentinel -1) отображаются чёрным цветом.
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
				int paletteIndex = dithered[y][x];
				int rgb = paletteIndex < 0 ? 0x000000 : palette.get(paletteIndex).getRgb();
				preview.setRGB(x, y, rgb);
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
				if (block == null) {
					continue;
				}

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
				if (block != null && block.isNeedSupport()) {
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
		callbackArena.close();
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
			double errRateR,
			double errRateG,
			double errRateB,
			double noiseLevel,
			int colorMetric,
			MemorySegment onProgress,
			int clipX,
			int clipY,
			int clipW,
			int clipH
		);
	}
}
