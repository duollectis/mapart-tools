package org.duollectis.mapart.tools.gui;

import com.google.gson.*;
import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.converter.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сохраняет и восстанавливает настройки GUI между сессиями через JSON-файл
 * {@code settings.json} в текущей рабочей директории приложения.
 */
@UtilityClass
public class AppPreferences {

	private static final Path SETTINGS_FILE = Path.of("./settings.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String KEY_VERSION = "version";
	private static final String KEY_MAP_WIDTH = "map_width";
	private static final String KEY_MAP_HEIGHT = "map_height";
	private static final String KEY_ALGORITHM = "algorithm";
	private static final String KEY_IMAGE_PATH = "image_path";
	private static final String KEY_BLOCKS_PATH = "blocks_path";
	private static final String KEY_OUT_PATH = "out_path";
	private static final String KEY_SUPPORT_BLOCK = "support_block";
	private static final String KEY_SUPPORT_SETTINGS = "support_settings";
	private static final String KEY_LOCALE = "locale";
	private static final String KEY_BRIGHTNESS = "brightness";
	private static final String KEY_CONTRAST = "contrast";
	private static final String KEY_SATURATION = "saturation";
	private static final String KEY_GAMMA = "gamma";
	private static final String KEY_HUE = "hue";
	private static final String KEY_AUTO_CONVERT = "auto_convert";
	private static final String KEY_ERR_RATE_R = "err_rate_r";
	private static final String KEY_ERR_RATE_G = "err_rate_g";
	private static final String KEY_ERR_RATE_B = "err_rate_b";
	private static final String KEY_ERR_RATE_STRENGTH = "err_rate_strength";
	private static final String KEY_NOISE_LEVEL = "noise_level";
	private static final String KEY_ERR_RATE_LINKED = "err_rate_linked";
	private static final String KEY_COLOR_METRIC = "color_metric";
	private static final String KEY_BLOCK_SELECTORS = "block_selectors";
	private static final String KEY_SCHEMATIC_FORMAT = "schematic_format";
	private static final String KEY_THEME = "theme";
	private static final String KEY_KEYBINDS = "keybinds";
	private static final String KEY_DISCORD_RPC = "discord_rpc";
	private static final String KEY_ANIMATIONS = "animations";
	private static final String KEY_LAYERS = "layers";
	private static final String KEY_ACTIVE_LAYER = "active_layer";
	private static final String KEY_IMPORT_ADD_TO_BLOCKS = "import_add_to_blocks";

	public void saveVersion(String version) {
		putString(KEY_VERSION, version);
	}

	public String loadVersion(String defaultValue) {
		return getString(KEY_VERSION, defaultValue);
	}

	public void saveMapWidth(int width) {
		putInt(KEY_MAP_WIDTH, width);
	}

	public int loadMapWidth(int defaultValue) {
		return getInt(KEY_MAP_WIDTH, defaultValue);
	}

	public void saveMapHeight(int height) {
		putInt(KEY_MAP_HEIGHT, height);
	}

	public int loadMapHeight(int defaultValue) {
		return getInt(KEY_MAP_HEIGHT, defaultValue);
	}

	public void saveAlgorithm(String algorithm) {
		putString(KEY_ALGORITHM, algorithm);
	}

	public String loadAlgorithm(String defaultValue) {
		return getString(KEY_ALGORITHM, defaultValue);
	}

	public void saveImagePath(String path) {
		putString(KEY_IMAGE_PATH, path);
	}

	public String loadImagePath() {
		return getString(KEY_IMAGE_PATH, "");
	}

	public void saveBlocksPath(String path) {
		putString(KEY_BLOCKS_PATH, path);
	}

	public String loadBlocksPath() {
		return getString(KEY_BLOCKS_PATH, "");
	}

	public void saveOutPath(String path) {
		putString(KEY_OUT_PATH, path);
	}

	public String loadOutPath(String defaultValue) {
		return getString(KEY_OUT_PATH, defaultValue);
	}

	public void saveSupportBlock(String blockId) {
		putString(KEY_SUPPORT_BLOCK, blockId);
	}

	public String loadSupportBlock(String defaultValue) {
		return getString(KEY_SUPPORT_BLOCK, defaultValue);
	}

	/**
	 * Сохраняет настройки блоков-опор в JSON.
	 * Пустой список сохраняется явно — это позволяет отличить "пользователь снял все блоки"
	 * от "настройки ещё не сохранялись".
	 */
	public void saveSupportSettings(SupportBlockSettings settings) {
		JsonObject root = readRoot();

		if (settings == null) {
			root.remove(KEY_SUPPORT_SETTINGS);
			writeRoot(root);
			return;
		}

		JsonObject obj = new JsonObject();
		obj.addProperty("mode", settings.getMode().name());

		JsonArray arr = new JsonArray();

		for (SupportBlockSettings.Entry entry : settings.getEntries()) {
			JsonObject item = new JsonObject();
			item.addProperty("blockId", entry.blockId());
			item.addProperty("weight", entry.weight());
			arr.add(item);
		}

		obj.add("entries", arr);
		root.add(KEY_SUPPORT_SETTINGS, obj);
		writeRoot(root);
	}

	/**
	 * Загружает настройки блоков-опор из JSON.
	 * Возвращает пустой {@link SupportBlockSettings} если данных нет — это позволяет
	 * отличить "пользователь снял все блоки" от "настройки ещё не сохранялись".
	 * Возвращает {@code null} только если ключ {@code support_settings} отсутствует полностью.
	 *
	 * @param defaultBlockId ID блока-опоры по умолчанию (используется только для обратной совместимости)
	 * @return сохранённые настройки, пустой экземпляр или {@code null} если данных нет
	 */
	public SupportBlockSettings loadSupportSettings(String defaultBlockId) {
		JsonObject root = readRoot();

		if (root.has(KEY_SUPPORT_SETTINGS)) {
			try {
				JsonObject obj = root.getAsJsonObject(KEY_SUPPORT_SETTINGS);
				WeightedSelector.Mode mode = WeightedSelector.Mode.valueOf(
					obj.get("mode").getAsString()
				);

				JsonArray arr = obj.getAsJsonArray("entries");
				List<SupportBlockSettings.Entry> entries = new ArrayList<>();

				for (int i = 0; i < arr.size(); i++) {
					JsonObject item = arr.get(i).getAsJsonObject();
					entries.add(new SupportBlockSettings.Entry(
						normalizeBlockId(item.get("blockId").getAsString()),
						item.get("weight").getAsInt()
					));
				}

				return new SupportBlockSettings(entries, mode);
			} catch (Exception ignored) {
				// повреждённые данные — возвращаем null
			}
		}

		// Обратная совместимость: читаем старый ключ support_block только если он явно сохранён
		if (root.has(KEY_SUPPORT_BLOCK)) {
			String legacyId = normalizeBlockId(getString(KEY_SUPPORT_BLOCK, defaultBlockId));
			return SupportBlockSettings.single(legacyId);
		}

		return null;
	}

	private static String normalizeBlockId(String blockId) {
		return blockId.contains(":") ? blockId : "minecraft:" + blockId;
	}

	public void saveLocale(String locale) {
		putString(KEY_LOCALE, locale);
	}

	public String loadLocale(String defaultValue) {
		return getString(KEY_LOCALE, defaultValue);
	}

	public void saveBrightness(int value) {
		putInt(KEY_BRIGHTNESS, value);
	}

	public int loadBrightness(int defaultValue) {
		return getInt(KEY_BRIGHTNESS, defaultValue);
	}

	public void saveContrast(int value) {
		putInt(KEY_CONTRAST, value);
	}

	public int loadContrast(int defaultValue) {
		return getInt(KEY_CONTRAST, defaultValue);
	}

	public void saveSaturation(int value) {
		putInt(KEY_SATURATION, value);
	}

	public int loadSaturation(int defaultValue) {
		return getInt(KEY_SATURATION, defaultValue);
	}

	public void saveGamma(int value) {
		putInt(KEY_GAMMA, value);
	}

	public int loadGamma(int defaultValue) {
		return getInt(KEY_GAMMA, defaultValue);
	}

	public void saveHue(int value) {
		putInt(KEY_HUE, value);
	}

	public int loadHue(int defaultValue) {
		return getInt(KEY_HUE, defaultValue);
	}

	public void saveAutoConvert(boolean value) {
		JsonObject root = readRoot();
		root.addProperty(KEY_AUTO_CONVERT, value);
		writeRoot(root);
	}

	public boolean loadAutoConvert(boolean defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_AUTO_CONVERT) ? root.get(KEY_AUTO_CONVERT).getAsBoolean() : defaultValue;
	}

	/**
	 * Сохраняет пользовательские селекторы весов блоков палитры.
	 * Ключ — базовый blockId (например "minecraft:stone").
	 * Значение — список вариантов с uniqueKey и весом.
	 */
	public void saveBlockSelectors(Map<String, WeightedSelector<BlockData>> selectors) {
		JsonObject root = readRoot();

		if (selectors == null || selectors.isEmpty()) {
			root.remove(KEY_BLOCK_SELECTORS);
			writeRoot(root);
			return;
		}

		JsonObject selectorsObj = new JsonObject();

		for (Map.Entry<String, WeightedSelector<BlockData>> entry : selectors.entrySet()) {
			WeightedSelector<BlockData> selector = entry.getValue();
			JsonObject selectorObj = new JsonObject();
			selectorObj.addProperty("mode", selector.getMode().name());

			JsonArray entriesArr = new JsonArray();

			for (WeightedSelector.Entry<BlockData> e : selector.getEntries()) {
				JsonObject item = new JsonObject();
				item.addProperty("uniqueKey", e.value().getUniqueKey());
				item.addProperty("weight", e.weight());
				entriesArr.add(item);
			}

			selectorObj.add("entries", entriesArr);
			selectorsObj.add(entry.getKey(), selectorObj);
		}

		root.add(KEY_BLOCK_SELECTORS, selectorsObj);
		writeRoot(root);
	}

	/**
	 * Загружает пользовательские селекторы весов блоков палитры.
	 * Для восстановления {@link BlockData} используется {@code paletteBlocks} —
	 * полная карта всех блоков палитры (uniqueKey → BlockData).
	 *
	 * @param paletteBlocks карта uniqueKey → BlockData для восстановления объектов
	 * @return карта baseId → WeightedSelector, или пустая карта если данных нет
	 */
	public Map<String, WeightedSelector<BlockData>> loadBlockSelectors(Map<String, BlockData> paletteBlocks) {
		JsonObject root = readRoot();

		if (!root.has(KEY_BLOCK_SELECTORS)) {
			return new HashMap<>();
		}

		Map<String, WeightedSelector<BlockData>> result = new HashMap<>();

		try {
			JsonObject selectorsObj = root.getAsJsonObject(KEY_BLOCK_SELECTORS);

			for (String baseId : selectorsObj.keySet()) {
				JsonObject selectorObj = selectorsObj.getAsJsonObject(baseId);
				WeightedSelector.Mode mode = WeightedSelector.Mode.valueOf(
					selectorObj.get("mode").getAsString()
				);

				JsonArray entriesArr = selectorObj.getAsJsonArray("entries");
				List<WeightedSelector.Entry<BlockData>> entries = new ArrayList<>();

				for (int i = 0; i < entriesArr.size(); i++) {
					JsonObject item = entriesArr.get(i).getAsJsonObject();
					String uniqueKey = item.get("uniqueKey").getAsString();
					int weight = item.get("weight").getAsInt();
					BlockData block = paletteBlocks.get(uniqueKey);

					if (block != null) {
						entries.add(new WeightedSelector.Entry<>(block, weight));
					}
				}

				if (!entries.isEmpty()) {
					result.put(baseId, new WeightedSelector<>(entries, mode));
				}
			}
		} catch (Exception ignored) {
			// повреждённые данные — возвращаем пустую карту
		}

		return result;
	}

	public void saveTheme(String themeName) {
		putString(KEY_THEME, themeName);
	}

	public String loadTheme(String defaultValue) {
		return getString(KEY_THEME, defaultValue);
	}

	public void saveSchematicFormat(SchematicFormat format) {
		putString(KEY_SCHEMATIC_FORMAT, format.name());
	}

	public SchematicFormat loadSchematicFormat() {
		String saved = getString(KEY_SCHEMATIC_FORMAT, SchematicFormat.NBT.name());

		try {
			return SchematicFormat.valueOf(saved);
		} catch (IllegalArgumentException ignored) {
			return SchematicFormat.NBT;
		}
	}

	public void saveDitherSettings(DitherSettings settings) {
		JsonObject root = readRoot();
		root.addProperty(KEY_ERR_RATE_R, settings.errRateR());
		root.addProperty(KEY_ERR_RATE_G, settings.errRateG());
		root.addProperty(KEY_ERR_RATE_B, settings.errRateB());
		root.addProperty(KEY_NOISE_LEVEL, settings.noiseLevel());
		root.addProperty(KEY_COLOR_METRIC, settings.colorMetric().name());
		writeRoot(root);
	}

	public void saveErrRateStrength(double value) {
		JsonObject root = readRoot();
		root.addProperty(KEY_ERR_RATE_STRENGTH, value);
		writeRoot(root);
	}

	public double loadErrRateStrength(double defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_ERR_RATE_STRENGTH)
			? root.get(KEY_ERR_RATE_STRENGTH).getAsDouble()
			: defaultValue;
	}

	public void saveErrRateLinked(boolean linked) {
		JsonObject root = readRoot();
		root.addProperty(KEY_ERR_RATE_LINKED, linked);
		writeRoot(root);
	}

	public boolean loadErrRateLinked() {
		JsonObject root = readRoot();
		return root.has(KEY_ERR_RATE_LINKED) && root.get(KEY_ERR_RATE_LINKED).getAsBoolean();
	}

	public DitherSettings loadDitherSettings() {
		DitherSettings defaults = DitherSettings.defaults();
		JsonObject root = readRoot();
		double errRateR = root.has(KEY_ERR_RATE_R)
			? root.get(KEY_ERR_RATE_R).getAsDouble()
			: defaults.errRateR();
		double errRateG = root.has(KEY_ERR_RATE_G)
			? root.get(KEY_ERR_RATE_G).getAsDouble()
			: defaults.errRateG();
		double errRateB = root.has(KEY_ERR_RATE_B)
			? root.get(KEY_ERR_RATE_B).getAsDouble()
			: defaults.errRateB();
		double noiseLevel = root.has(KEY_NOISE_LEVEL)
			? root.get(KEY_NOISE_LEVEL).getAsDouble()
			: defaults.noiseLevel();
		ColorMetric metric = defaults.colorMetric();

		if (root.has(KEY_COLOR_METRIC)) {
			try {
				metric = ColorMetric.valueOf(root.get(KEY_COLOR_METRIC).getAsString());
			} catch (IllegalArgumentException ignored) {
				// неизвестная метрика — оставляем дефолт
			}
		}

		return new DitherSettings(errRateR, errRateG, errRateB, noiseLevel, metric);
	}

	private void putString(String key, String value) {
		JsonObject root = readRoot();
		root.addProperty(key, value);
		writeRoot(root);
	}

	private void putInt(String key, int value) {
		JsonObject root = readRoot();
		root.addProperty(key, value);
		writeRoot(root);
	}

	private String getString(String key, String defaultValue) {
		JsonObject root = readRoot();
		return root.has(key) ? root.get(key).getAsString() : defaultValue;
	}

	private int getInt(String key, int defaultValue) {
		JsonObject root = readRoot();
		return root.has(key) ? root.get(key).getAsInt() : defaultValue;
	}

	private JsonObject readRoot() {
		if (Files.notExists(SETTINGS_FILE)) {
			return new JsonObject();
		}

		try {
			String content = Files.readString(SETTINGS_FILE);
			return JsonParser.parseString(content).getAsJsonObject();
		} catch (IOException | IllegalStateException ignored) {
			return new JsonObject();
		}
	}

	public void saveAnimations(boolean enabled) {
		JsonObject root = readRoot();
		root.addProperty(KEY_ANIMATIONS, enabled);
		writeRoot(root);
	}

	public boolean loadAnimations(boolean defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_ANIMATIONS) ? root.get(KEY_ANIMATIONS).getAsBoolean() : defaultValue;
	}

	public void saveDiscordRpc(boolean enabled) {
		JsonObject root = readRoot();
		root.addProperty(KEY_DISCORD_RPC, enabled);
		writeRoot(root);
	}

	public boolean loadDiscordRpc(boolean defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_DISCORD_RPC) ? root.get(KEY_DISCORD_RPC).getAsBoolean() : defaultValue;
	}

	/**
	 * Сохраняет состояние всех слоёв sourcePreview: путь к файлу, имя, видимость и трансформ.
	 * Активный слой сохраняется отдельным ключом.
	 *
	 * @param layers список записей слоёв (path, name, visible, scaleX, scaleY, offsetX, offsetY)
	 * @param activeIndex индекс активного слоя
	 */
	public void saveLayerStates(List<LayerState> layers, int activeIndex) {
		JsonObject root = readRoot();
		JsonArray arr = new JsonArray();

		for (LayerState state : layers) {
			JsonObject obj = new JsonObject();
			obj.addProperty("path", state.path());
			obj.addProperty("name", state.name());
			obj.addProperty("visible", state.visible());
			obj.addProperty("normW", state.normW());
			obj.addProperty("normH", state.normH());
			obj.addProperty("normOffsetX", state.normOffsetX());
			obj.addProperty("normOffsetY", state.normOffsetY());
			arr.add(obj);
		}

		root.add(KEY_LAYERS, arr);
		root.addProperty(KEY_ACTIVE_LAYER, activeIndex);
		root.remove(KEY_IMAGE_PATH);
		writeRoot(root);
	}

	/**
	 * Загружает сохранённые состояния слоёв. Возвращает пустой список если данных нет.
	 *
	 * @return список записей слоёв в порядке снизу вверх
	 */
	public List<LayerState> loadLayerStates() {
		JsonObject root = readRoot();

		if (!root.has(KEY_LAYERS)) {
			return List.of();
		}

		List<LayerState> result = new ArrayList<>();

		try {
			JsonArray arr = root.getAsJsonArray(KEY_LAYERS);

			for (int i = 0; i < arr.size(); i++) {
				JsonObject obj = arr.get(i).getAsJsonObject();
				result.add(new LayerState(
					obj.get("path").getAsString(),
					obj.get("name").getAsString(),
					obj.get("visible").getAsBoolean(),
					obj.get("normW").getAsDouble(),
					obj.get("normH").getAsDouble(),
					obj.get("normOffsetX").getAsDouble(),
					obj.get("normOffsetY").getAsDouble()
				));
			}
		} catch (Exception ignored) {
			return List.of();
		}

		return result;
	}

	public int loadActiveLayerIndex(int defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_ACTIVE_LAYER) ? root.get(KEY_ACTIVE_LAYER).getAsInt() : defaultValue;
	}

	/**
	 * Снимок состояния одного слоя для сериализации в settings.json.
	 * Трансформ хранится в нормализованных координатах относительно размера сетки:
	 * normW = scaleX * imageW / gridW, normOffsetX = offsetX / gridW.
	 * Это позволяет корректно восстанавливать позицию при любом размере окна.
	 */
	public record LayerState(
		String path,
		String name,
		boolean visible,
		double normW,
		double normH,
		double normOffsetX,
		double normOffsetY
	) {}

	public void saveKeyBinds(Map<String, String> binds) {
		JsonObject root = readRoot();
		JsonObject obj = new JsonObject();
		binds.forEach(obj::addProperty);
		root.add(KEY_KEYBINDS, obj);
		writeRoot(root);
	}

	public Map<String, String> loadKeyBinds() {
		JsonObject root = readRoot();
		Map<String, String> result = new HashMap<>();

		if (!root.has(KEY_KEYBINDS)) {
			return result;
		}

		JsonObject obj = root.getAsJsonObject(KEY_KEYBINDS);

		for (String key : obj.keySet()) {
			result.put(key, obj.get(key).getAsString());
		}

		return result;
	}

	public void saveImportAddToBlocks(boolean value) {
		JsonObject root = readRoot();
		root.addProperty(KEY_IMPORT_ADD_TO_BLOCKS, value);
		writeRoot(root);
	}

	public boolean loadImportAddToBlocks(boolean defaultValue) {
		JsonObject root = readRoot();
		return root.has(KEY_IMPORT_ADD_TO_BLOCKS)
			? root.get(KEY_IMPORT_ADD_TO_BLOCKS).getAsBoolean()
			: defaultValue;
	}

	private void writeRoot(JsonObject root) {
		try {
			Files.writeString(SETTINGS_FILE, GSON.toJson(root));
		} catch (IOException ignored) {
			// не критично — настройки просто не сохранятся
		}
	}
}
