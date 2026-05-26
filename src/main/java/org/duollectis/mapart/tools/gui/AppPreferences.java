package org.duollectis.mapart.tools.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.converter.DitherSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
	private static final String KEY_LOCALE = "locale";
	private static final String KEY_BRIGHTNESS = "brightness";
	private static final String KEY_CONTRAST = "contrast";
	private static final String KEY_SATURATION = "saturation";
	private static final String KEY_GAMMA = "gamma";
	private static final String KEY_HUE = "hue";
	private static final String KEY_AUTO_CONVERT = "auto_convert";
	private static final String KEY_ERROR_DIFFUSION_RATE = "error_diffusion_rate";
	private static final String KEY_NOISE_LEVEL = "noise_level";

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

	public void saveDitherSettings(DitherSettings settings) {
		JsonObject root = readRoot();
		root.addProperty(KEY_ERROR_DIFFUSION_RATE, settings.errorDiffusionRate());
		root.addProperty(KEY_NOISE_LEVEL, settings.noiseLevel());
		writeRoot(root);
	}

	public DitherSettings loadDitherSettings() {
		DitherSettings defaults = DitherSettings.defaults();
		JsonObject root = readRoot();
		double errorRate = root.has(KEY_ERROR_DIFFUSION_RATE)
				? root.get(KEY_ERROR_DIFFUSION_RATE).getAsDouble()
				: defaults.errorDiffusionRate();
		double noiseLevel = root.has(KEY_NOISE_LEVEL)
				? root.get(KEY_NOISE_LEVEL).getAsDouble()
				: defaults.noiseLevel();
		return new DitherSettings(errorRate, noiseLevel);
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

	private void writeRoot(JsonObject root) {
		try {
			Files.writeString(SETTINGS_FILE, GSON.toJson(root));
		} catch (IOException ignored) {
			// не критично — настройки просто не сохранятся
		}
	}
}
