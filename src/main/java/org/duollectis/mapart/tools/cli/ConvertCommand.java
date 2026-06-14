package org.duollectis.mapart.tools.cli;

import org.duollectis.mapart.tools.converter.ImageConverter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Command(
	name = "convert",
	description = "Конвертирует изображение в схематики карт Майнкрафта.",
	mixinStandardHelpOptions = true
)
public class ConvertCommand implements Runnable {

	private static final File DEFAULT_OUT_PATH = new File("./rendered");

	@Option(
		names = {"-v", "--version"},
		description = "Целевая версия Майнкрафта (имя файла в папке versions без расширения).",
		required = true
	)
	private String version;

	@Option(
		names = {"-w", "--width"},
		description = "Количество карт по горизонтали.",
		required = true
	)
	private int width;

	@Option(
		names = {"-h", "--height"},
		description = "Количество карт по вертикали.",
		required = true
	)
	private int height;

	@Option(
		names = {"-p", "--image-path"},
		description = "Путь к исходному изображению.",
		required = true
	)
	private File imagePath;

	@Option(
		names = {"-b", "--blocks"},
		description = "Путь к файлу со списком разрешённых блоков.",
		required = true
	)
	private File blocksPath;

	@Option(
		names = {"-o", "--out-path"},
		description = "Директория для сохранения схематик."
	)
	private File outPath = DEFAULT_OUT_PATH;

	@Override
	public void run() {
		validateImagePath();

		try (InputStream versionStream = openVersionStream()) {
			File resolvedOutDir = resolveOutDir();
			String paletteJson = new String(versionStream.readAllBytes());

			new ImageConverter().run(paletteJson, imagePath, resolvedOutDir, blocksPath, width, height);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void validateImagePath() {
		if (!imagePath.exists()) {
			System.err.println("Указанный путь к изображению не существует!");
			System.exit(-1);
		}

		if (!imagePath.isFile()) {
			System.err.println("Указанный путь должен быть файлом, а не директорией!");
			System.exit(-1);
		}
	}

	private InputStream openVersionStream() {
		String resourcePath = "versions/" + version + ".zip";
		String jsonEntry = version + ".json";

		InputStream raw = getClass().getClassLoader().getResourceAsStream(resourcePath);

		if (raw == null) {
			throw new RuntimeException("Версия '%s' не найдена в ресурсах!".formatted(version));
		}

		try {
			java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(raw);
			java.util.zip.ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(jsonEntry)) {
					return zip;
				}

				zip.closeEntry();
			}

			zip.close();
		} catch (IOException e) {
			throw new RuntimeException("Ошибка чтения архива версии '%s'!".formatted(version), e);
		}

		throw new RuntimeException("Файл %s не найден в архиве версии %s!".formatted(jsonEntry, version));
	}

	private File resolveOutDir() {
		File dir = outPath.isFile() ? outPath.getParentFile() : outPath;

		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Не удалось создать директорию вывода: '%s'".formatted(dir));
		}

		return dir;
	}
}
