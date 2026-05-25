package org.duollectis.mapart.tools.nativee;

import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Отвечает исключительно за извлечение нативной библиотеки из ресурсов JAR
 * во временный файл на диске, пригодный для загрузки через {@link System#load}.
 */
@UtilityClass
public class NativeLibraryLoader {

	private static final String RESOURCE_PREFIX = "native/";
	private static final String TEMP_FILE_PREFIX = "native_lib_";

	/**
	 * Извлекает нативную библиотеку из ресурсов JAR во временный файл.
	 * Файл помечается на удаление при завершении JVM.
	 *
	 * @param resourceName имя файла библиотеки (например, {@code libmt-ditherer.so})
	 * @return {@link File} временного файла с нативной библиотекой
	 * @throws RuntimeException если ресурс не найден или произошла ошибка ввода-вывода
	 */
	public File extractToTemp(String resourceName) {
		String resourcePath = RESOURCE_PREFIX + resourceName;

		try (InputStream stream = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (stream == null) {
				throw new RuntimeException("Нативная библиотека '%s' не найдена в ресурсах!".formatted(resourceName));
			}

			Path tempFile = Files.createTempFile(TEMP_FILE_PREFIX, resourceName);
			Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);

			File libFile = tempFile.toFile();
			libFile.deleteOnExit();

			ensureExecutable(libFile);

			return libFile;
		} catch (IOException e) {
			throw new RuntimeException("Ошибка при извлечении нативной библиотеки: " + resourceName, e);
		}
	}

	/**
	 * Определяет платформо-зависимое имя файла библиотеки.
	 *
	 * @param baseName базовое имя библиотеки без префикса и расширения (например, {@code mt-ditherer})
	 * @return полное имя файла с учётом ОС (например, {@code libmt-ditherer.so})
	 */
	public String resolveFileName(String baseName) {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			return "lib" + baseName + ".dll";
		}

		if (os.contains("mac")) {
			return "lib" + baseName + ".dylib";
		}

		return "lib" + baseName + ".so";
	}

	private void ensureExecutable(File file) {
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

		if (isWindows) {
			return;
		}

		if (file.setExecutable(true)) {
			return;
		}

		System.err.println("Не удалось установить флаг исполняемого файла для: " + file.getName());
	}
}
