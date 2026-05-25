package org.duollectis.mapart.tools.nativee;

import lombok.Getter;

import java.io.File;

/**
 * Хранит ссылку на извлечённую нативную библиотеку дизеринга.
 * Инициализация выполняется один раз при старте приложения через {@link #init()}.
 */
public class NativeHolder {

	private static final String LIBRARY_BASE_NAME = "mt-ditherer";

	@Getter
	private static File lib;

	public static void init() {
		String fileName = NativeLibraryLoader.resolveFileName(LIBRARY_BASE_NAME);
		lib = NativeLibraryLoader.extractToTemp(fileName);
	}
}
