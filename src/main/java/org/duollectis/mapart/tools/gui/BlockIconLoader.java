package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.converter.BlockData;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Загружает иконки блоков из ZIP-ресурса versions/{version}.zip.
 * Структура архива: block-icons/minecraft_oak_log_axis_y.png
 * Ключ в кэше — имя файла без расширения (например "minecraft_oak_log_axis_y").
 * Для поиска иконки блока используется поле {@link BlockData#getIcon()},
 * которое содержит точное имя файла из JSON палитры.
 * Все иконки загружаются при создании объекта и кэшируются в памяти.
 */
public class BlockIconLoader {

	private static final int ICON_SIZE = 32;
	private static final String ICONS_PREFIX = "block-icons/";

	/** Ключ — имя файла без расширения (например "minecraft_oak_log_axis_y") */
	private final Map<String, ImageIcon> icons;

	private BlockIconLoader(Map<String, ImageIcon> icons) {
		this.icons = icons;
	}

	/**
	 * Загружает все иконки из versions/{version}.zip в classpath.
	 * Если ресурс не найден или пуст — возвращает пустой Optional.
	 *
	 * @param version версия Minecraft (например "1.21.11")
	 */
	public static Optional<BlockIconLoader> create(String version) {
		String resourcePath = "versions/" + version + ".zip";

		try (InputStream raw = BlockIconLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (raw == null) {
				return Optional.empty();
			}

			Map<String, ImageIcon> loaded = loadAllIcons(raw);

			return loaded.isEmpty()
				? Optional.empty()
				: Optional.of(new BlockIconLoader(loaded));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * Возвращает иконку для блока, используя поле {@link BlockData#getIcon()} как ключ.
	 * Если поле icon не задано — fallback на ID блока (двоеточие → подчёркивание).
	 *
	 * @param block блок из палитры
	 */
	public Optional<ImageIcon> getIcon(BlockData block) {
		String iconKey = block.getIcon() != null
			? block.getIcon()
			: block.getId().replace(':', '_');

		return Optional.ofNullable(icons.get(iconKey));
	}

	private static Map<String, ImageIcon> loadAllIcons(InputStream raw) throws IOException {
		Map<String, ImageIcon> result = new HashMap<>();

		try (ZipInputStream zip = new ZipInputStream(raw)) {
			ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();

				if (!name.startsWith(ICONS_PREFIX) || !name.endsWith(".png")) {
					zip.closeEntry();
					continue;
				}

				// "block-icons/minecraft_oak_log_axis_y.png" → "minecraft_oak_log_axis_y"
				String key = name.substring(ICONS_PREFIX.length(), name.length() - ".png".length());
				ImageIcon icon = readIcon(zip);

				if (icon != null) {
					result.put(key, icon);
				}

				zip.closeEntry();
			}
		}

		return result;
	}

	private static ImageIcon readIcon(ZipInputStream zip) {
		try {
			BufferedImage raw = ImageIO.read(zip);

			if (raw == null) {
				return null;
			}

			// Берём только первый кадр (анимированные текстуры — вертикальные полосы)
			int size = Math.min(raw.getWidth(), raw.getHeight());
			BufferedImage frame = raw.getSubimage(0, 0, size, size);

			Image scaled = frame.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_FAST);
			return new ImageIcon(scaled);
		} catch (IOException e) {
			return null;
		}
	}
}
