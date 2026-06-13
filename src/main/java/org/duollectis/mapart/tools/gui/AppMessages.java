package org.duollectis.mapart.tools.gui;

import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import java.util.function.Consumer;

/**
 * Переведённые строки для нон-GUI контекстов: исключения, логи, прогресс-бар, воркеры.
 *
 * <p>Эти ключи никогда не попадают в {@link UpdatableRegistry} через {@code registerLang()},
 * потому что не привязаны к живым Swing-компонентам. Регистрируя их здесь, мы гарантируем,
 * что {@link UpdatableRegistry} видит полный набор ключей приложения при валидации.
 *
 * <p>Поля обновляются автоматически при смене языка через {@link UpdatableRegistry#fireLang}.
 */
public final class AppMessages {

	public static String ERROR_VERSION_NOT_FOUND;
	public static String ERROR_PALETTE_ENTRY_NOT_FOUND;

	public static String LOG_DITHER_START;
	public static String LOG_DITHER_DONE;
	public static String LOG_IMAGE_LOADED;
	public static String LOG_BLOCKS_LOADED;
	public static String LOG_BLOCKS_PICKED;
	public static String LOG_BLOCK_REMOVED;
	public static String LOG_EXPORT_START;
	public static String LOG_EXPORT_DONE;
	public static String LOG_EXPORT_ERROR;
	public static String LOG_IMPORT_START;
	public static String LOG_IMPORT_START_MULTI;
	public static String LOG_IMPORT_PROGRESS;
	public static String LOG_PREVIEW_SAVED;
	public static String LOG_CLIPBOARD_PASTE;
	public static String LOG_CANCELLED;
	public static String LOG_ERROR;

	public static String PROGRESS_LOADING_PALETTE;
	public static String PROGRESS_PREPARING_IMAGE;
	public static String PROGRESS_PREPARING;
	public static String PROGRESS_DITHERING;
	public static String PROGRESS_DITHER_DONE;
	public static String PROGRESS_DITHER_ERROR;
	public static String PROGRESS_CANCELLED;
	public static String PROGRESS_EXPORT_DONE;
	public static String PROGRESS_EXPORT_ERROR;
	public static String PROGRESS_EXPORTING;
	public static String PROGRESS_IMPORTING;
	public static String PROGRESS_IMPORT_DONE;
	public static String PROGRESS_IMPORT_ERROR;

	public static String IMPORT_FILE_SKIP;
	public static String IMPORT_FILE_TOO_LARGE;

	static {
		reg("error.version_not_found", v -> ERROR_VERSION_NOT_FOUND = v);
		reg("error.palette_entry_not_found", v -> ERROR_PALETTE_ENTRY_NOT_FOUND = v);

		reg("log.dither_start", v -> LOG_DITHER_START = v);
		reg("log.dither_done", v -> LOG_DITHER_DONE = v);
		reg("log.image_loaded", v -> LOG_IMAGE_LOADED = v);
		reg("log.blocks_loaded", v -> LOG_BLOCKS_LOADED = v);
		reg("log.blocks_picked", v -> LOG_BLOCKS_PICKED = v);
		reg("log.block_removed", v -> LOG_BLOCK_REMOVED = v);
		reg("log.export_start", v -> LOG_EXPORT_START = v);
		reg("log.export_done", v -> LOG_EXPORT_DONE = v);
		reg("log.export_error", v -> LOG_EXPORT_ERROR = v);
		reg("log.import_start", v -> LOG_IMPORT_START = v);
		reg("log.import_start_multi", v -> LOG_IMPORT_START_MULTI = v);
		reg("log.import_progress", v -> LOG_IMPORT_PROGRESS = v);
		reg("log.preview_saved", v -> LOG_PREVIEW_SAVED = v);
		reg("log.clipboard_paste", v -> LOG_CLIPBOARD_PASTE = v);
		reg("log.cancelled", v -> LOG_CANCELLED = v);
		reg("log.error", v -> LOG_ERROR = v);

		reg("progress.loading_palette", v -> PROGRESS_LOADING_PALETTE = v);
		reg("progress.preparing_image", v -> PROGRESS_PREPARING_IMAGE = v);
		reg("progress.preparing", v -> PROGRESS_PREPARING = v);
		reg("progress.dithering", v -> PROGRESS_DITHERING = v);
		reg("progress.dither_done", v -> PROGRESS_DITHER_DONE = v);
		reg("progress.dither_error", v -> PROGRESS_DITHER_ERROR = v);
		reg("progress.cancelled", v -> PROGRESS_CANCELLED = v);
		reg("progress.export_done", v -> PROGRESS_EXPORT_DONE = v);
		reg("progress.export_error", v -> PROGRESS_EXPORT_ERROR = v);
		reg("progress.exporting", v -> PROGRESS_EXPORTING = v);
		reg("progress.importing", v -> PROGRESS_IMPORTING = v);
		reg("progress.import_done", v -> PROGRESS_IMPORT_DONE = v);
		reg("progress.import_error", v -> PROGRESS_IMPORT_ERROR = v);

		reg("import.file.skip", v -> IMPORT_FILE_SKIP = v);
		reg("import.file.too_large", v -> IMPORT_FILE_TOO_LARGE = v);
	}

	private static void reg(String key, Consumer<String> setter) {
		UpdatableRegistry.registerLang(key, setter);
	}

	private AppMessages() {}
}
