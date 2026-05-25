package org.duollectis.mapart.tools.gui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Загружает текстуры блоков из ZIP-архива textures-{version}.zip.
 * Маппинг: "minecraft:oak_log" → запись "oak_log.png" внутри архива.
 * При отсутствии точного совпадения ищет вариант с суффиксом _top,
 * затем любое частичное совпадение по имени блока.
 * Результаты кэшируются в памяти на время жизни объекта.
 */
public class BlockTextureLoader {

	private static final int ICON_SIZE = 32;
	private static final String ARCHIVE_PREFIX = "textures-";
	private static final String ARCHIVE_SUFFIX = ".zip";

	/**
	 * Суффиксы блоков, которые отрезаются при поиске текстуры материала.
	 * Порядок важен — более длинные суффиксы должны идти раньше коротких.
	 */
	private static final List<String> BLOCK_SUFFIXES = List.of(
		"_fence_gate",
		"_fence",
		"_wall",
		"_stairs",
		"_slab",
		"_button",
		"_pressure_plate",
		"_door",
		"_trapdoor",
		"_sign",
		"_hanging_sign",
		"_gate"
	);

	/**
	 * Точные замены имён блоков на имена текстур.
	 * Применяются до любого поиска по архиву.
	 */
	private static final Map<String, String> EXACT_ALIASES = Map.ofEntries(
		Map.entry("smooth_quartz", "quartz_block_top"),
		Map.entry("smooth_quartz_slab", "quartz_block_top"),
		Map.entry("smooth_quartz_stairs", "quartz_block_top"),
		Map.entry("smooth_sandstone", "sandstone_top"),
		Map.entry("smooth_sandstone_slab", "sandstone_top"),
		Map.entry("smooth_sandstone_stairs", "sandstone_top"),
		Map.entry("smooth_red_sandstone", "red_sandstone_top"),
		Map.entry("smooth_red_sandstone_slab", "red_sandstone_top"),
		Map.entry("smooth_red_sandstone_stairs", "red_sandstone_top"),
		// smooth_stone_slab имеет уникальную боковую текстуру, не совпадающую с smooth_stone
		Map.entry("smooth_stone_slab", "smooth_stone_slab_side"),
		// petrified_oak_slab — особый случай, есть отдельный файл
		Map.entry("petrified_oak_slab", "petrified_oak_slab"),
		Map.entry("crimson_hyphae", "crimson_stem"),
		Map.entry("warped_hyphae", "warped_stem"),
		Map.entry("stripped_crimson_hyphae", "stripped_crimson_stem"),
		Map.entry("stripped_warped_hyphae", "stripped_warped_stem"),
		Map.entry("magma_block", "magma"),
		Map.entry("chest", "barrel_top"),
		Map.entry("trapped_chest", "barrel_top"),
		Map.entry("ender_chest", "obsidian"),
		Map.entry("copper_chest", "copper_chest_top"),
		Map.entry("exposed_copper_chest", "exposed_copper_chest_top"),
		Map.entry("oxidized_copper_chest", "oxidized_copper_chest_top"),
		Map.entry("weathered_copper_chest", "weathered_copper_chest_top"),
		Map.entry("waxed_copper_chest", "copper_chest_top"),
		Map.entry("waxed_exposed_copper_chest", "exposed_copper_chest_top"),
		Map.entry("waxed_oxidized_copper_chest", "oxidized_copper_chest_top"),
		Map.entry("waxed_weathered_copper_chest", "weathered_copper_chest_top"),
		Map.entry("lava_cauldron", "cauldron_top"),
		Map.entry("water_cauldron", "cauldron_top"),
		Map.entry("powder_snow_cauldron", "cauldron_top"),
		Map.entry("dried_kelp_block", "dried_kelp_block_side"),
		Map.entry("pale_moss_carpet", "pale_moss_block"),
		Map.entry("heavy_weighted_pressure_plate", "iron_block"),
		Map.entry("light_weighted_pressure_plate", "gold_block"),
		Map.entry("bubble_column", "water_still"),
		Map.entry("end_gateway", "end_stone"),
		Map.entry("moving_piston", "piston_side"),
		Map.entry("sticky_piston", "piston_head"),
		Map.entry("decorated_pot", "decorated_pot_side")
	);

	private final Map<String, ImageIcon> cache = new HashMap<>();

	/** Все имена записей в архиве (без расширения .png), для нечёткого поиска */
	private final String[] availableNames;

	/** Открытый ZIP-архив с текстурами */
	private final ZipFile archive;

	private BlockTextureLoader(ZipFile archive, String[] availableNames) {
		this.archive = archive;
		this.availableNames = availableNames;
	}

	/**
	 * Ищет архив textures-{version}.zip в текущей директории и создаёт загрузчик.
	 * Если архив не найден — возвращает пустой Optional.
	 *
	 * @param version версия Minecraft (например "1.21.11")
	 * @return Optional с загрузчиком, если архив найден; пустой Optional иначе
	 */
	public static Optional<BlockTextureLoader> create(String version) {
		File archiveFile = new File(ARCHIVE_PREFIX + version + ARCHIVE_SUFFIX);

		if (archiveFile.isFile() == false) {
			return Optional.empty();
		}

		try {
			ZipFile zip = new ZipFile(archiveFile);

			String[] names = zip.stream()
				.map(ZipEntry::getName)
				.filter(name -> name.endsWith(".png"))
				.map(name -> name.substring(0, name.length() - ".png".length()))
				.sorted()
				.toArray(String[]::new);

			if (names.length == 0) {
				zip.close();
				return Optional.empty();
			}

			return Optional.of(new BlockTextureLoader(zip, names));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * Загружает иконку текстуры для блока по его id без учёта свойств.
	 *
	 * @param blockId id блока в формате "minecraft:stone" или "stone"
	 */
	public Optional<ImageIcon> loadIcon(String blockId) {
		return loadIcon(blockId, Collections.emptyMap());
	}

	/**
	 * Загружает иконку текстуры с учётом blockstate-свойств блока.
	 * Ключевое свойство — axis: при axis=y показывается вид сверху (_top),
	 * при axis=x/z — боковая текстура (_side или базовая).
	 *
	 * @param blockId    id блока в формате "minecraft:oak_log"
	 * @param properties blockstate-свойства из JSON палитры (axis, facing и т.д.)
	 */
	public Optional<ImageIcon> loadIcon(String blockId, Map<String, String> properties) {
		String cacheKey = blockId + properties;

		if (cache.containsKey(cacheKey)) {
			return Optional.ofNullable(cache.get(cacheKey));
		}

		Optional<ImageIcon> icon = resolveIcon(blockId, properties);
		cache.put(cacheKey, icon.orElse(null));

		return icon;
	}

	private Optional<ImageIcon> resolveIcon(String blockId, Map<String, String> properties) {
		String name = stripNamespace(blockId);
		String textureName = applyAliases(name);
		String axis = properties == null ? "y" : properties.getOrDefault("axis", "y");

		boolean isWoodBlock = name.endsWith("_wood") || name.endsWith("_hyphae");
		boolean isSlab = name.endsWith("_slab");
		boolean isStairs = name.endsWith("_stairs");

		Optional<ImageIcon> base = (isWoodBlock || "x".equals(axis) || "z".equals(axis))
			? resolveSideFirst(textureName)
			: resolveTopFirst(textureName);

		if (isSlab) {
			return base.map(BlockTextureLoader::applySlabOverlay);
		}

		if (isStairs) {
			return base.map(BlockTextureLoader::applyStairsOverlay);
		}

		return base;
	}

	/**
	 * Обрезает верхнюю половину иконки — показывает только нижние полблока (slab).
	 */
	private static ImageIcon applySlabOverlay(ImageIcon source) {
		int size = ICON_SIZE;
		int half = size / 2;
		BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();

		g.setColor(new Color(8, 8, 8));
		g.fillRect(0, 0, size, size);

		g.drawImage(source.getImage(), 0, half, size, size, 0, half, size, size, null);

		g.dispose();

		return new ImageIcon(result);
	}

	/**
	 * Обрезает верхний правый квадрант иконки — показывает ступенчатую форму (stairs).
	 */
	private static ImageIcon applyStairsOverlay(ImageIcon source) {
		int size = ICON_SIZE;
		int half = size / 2;
		BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();

		g.setColor(new Color(8, 8, 8));
		g.fillRect(0, 0, size, size);

		g.drawImage(source.getImage(), 0, 0, half, size, 0, 0, half, size, null);
		g.drawImage(source.getImage(), half, half, size, size, half, half, size, size, null);

		g.dispose();

		return new ImageIcon(result);
	}

	private Optional<ImageIcon> resolveTopFirst(String textureName) {
		Optional<ImageIcon> withTop = loadEntry(textureName + "_top");

		if (withTop.isPresent()) {
			return withTop;
		}

		Optional<ImageIcon> exact = loadEntry(textureName);

		if (exact.isPresent()) {
			return exact;
		}

		return findFuzzy(textureName);
	}

	private Optional<ImageIcon> resolveSideFirst(String textureName) {
		Optional<ImageIcon> withSide = loadEntry(textureName + "_side");

		if (withSide.isPresent()) {
			return withSide;
		}

		Optional<ImageIcon> exact = loadEntry(textureName);

		if (exact.isPresent()) {
			return exact;
		}

		return findFuzzy(textureName);
	}

	/**
	 * Применяет таблицу алиасов и правила трансформации имён блоков.
	 * Порядок: точные алиасы → суффиксы _wall_sign/_wood → префиксы waxed_/infested_.
	 */
	private static String applyAliases(String name) {
		if (EXACT_ALIASES.containsKey(name)) {
			return EXACT_ALIASES.get(name);
		}

		if (name.endsWith("_wall_hanging_sign")) {
			return name.replace("_wall_hanging_sign", "_hanging_sign");
		}

		if (name.endsWith("_wall_sign")) {
			return name.replace("_wall_sign", "_sign");
		}

		if (name.endsWith("_wall_banner") || name.endsWith("_banner")) {
			return "white_wool";
		}

		if (name.endsWith("_bed")) {
			return name.substring(0, name.length() - "_bed".length()) + "_wool";
		}

		if (name.endsWith("_carpet")) {
			return name.equals("moss_carpet")
				? "moss_block"
				: name.substring(0, name.length() - "_carpet".length()) + "_wool";
		}

		if (name.contains("_wall_") && name.endsWith("_fan")) {
			return name.replace("_wall_", "_");
		}

		if (name.equals("snow_block")) {
			return "snow";
		}

		if (name.endsWith("_wood")) {
			return name.substring(0, name.length() - "_wood".length()) + "_log";
		}

		if (name.startsWith("waxed_")) {
			return name.substring("waxed_".length());
		}

		if (name.startsWith("infested_")) {
			return name.substring("infested_".length());
		}

		return name;
	}

	/**
	 * Нечёткий поиск — перебирает все доступные имена в поисках частичного совпадения.
	 */
	private Optional<ImageIcon> findFuzzy(String textureName) {
		for (String name : availableNames) {
			if (name.startsWith(textureName)) {
				return loadEntry(name);
			}
		}

		return findByMaterialPrefix(textureName);
	}

	/**
	 * Поиск по материалу блока — отрезает известные суффиксы типа _fence, _wall, _slab
	 * и ищет текстуру базового материала (например, birch_fence → birch_planks).
	 */
	private Optional<ImageIcon> findByMaterialPrefix(String textureName) {
		for (String suffix : BLOCK_SUFFIXES) {
			if (textureName.endsWith(suffix) == false) {
				continue;
			}

			String materialPrefix = textureName.substring(0, textureName.length() - suffix.length());

			for (String name : availableNames) {
				if (name.startsWith(materialPrefix) && name.contains("plank")) {
					return loadEntry(name);
				}
			}

			for (String name : availableNames) {
				if (name.startsWith(materialPrefix) && name.contains("log")) {
					return loadEntry(name);
				}
			}

			for (String name : availableNames) {
				if (name.startsWith(materialPrefix)) {
					return loadEntry(name);
				}
			}
		}

		return Optional.empty();
	}

	/** Загружает запись из ZIP по имени без расширения (например "oak_log"). */
	private Optional<ImageIcon> loadEntry(String entryName) {
		ZipEntry entry = archive.getEntry(entryName + ".png");

		if (entry == null) {
			return Optional.empty();
		}

		try (InputStream stream = archive.getInputStream(entry)) {
			BufferedImage raw = ImageIO.read(stream);

			if (raw == null) {
				return Optional.empty();
			}

			// Берём только первый кадр (анимированные текстуры — вертикальные полосы)
			int size = Math.min(raw.getWidth(), raw.getHeight());
			BufferedImage frame = raw.getSubimage(0, 0, size, size);

			// Конвертируем в ARGB чтобы корректно обработать прозрачность
			// (палитровые PNG типа TYPE_BYTE_INDEXED теряют альфа при масштабировании)
			BufferedImage argb = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = argb.createGraphics();
			g2.drawImage(frame, 0, 0, null);
			g2.dispose();

			Image scaled = argb.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_FAST);
			return Optional.of(new ImageIcon(scaled));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static String stripNamespace(String blockId) {
		int colonIdx = blockId.indexOf(':');

		return colonIdx >= 0
			? blockId.substring(colonIdx + 1)
			: blockId;
	}
}
