package org.duollectis.mapart.tools.nativee;

import lombok.Getter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeHolder {

	private static final String LIBRARY_NAME = "mt-ditherer";

	@Getter
	private static File lib;

	public static void init() {

		String name = getFileName(LIBRARY_NAME);

		try (InputStream stream = NativeHolder.class.getClassLoader().getResourceAsStream("native/" + name)) {
			if (stream == null) {
				throw new RuntimeException("Lib '%s' not found!".formatted(name));
			}

			Path tempLib = Files.createTempFile("native_lib_", name);
			Files.copy(stream, tempLib, StandardCopyOption.REPLACE_EXISTING);

			lib = tempLib.toFile();
			lib.deleteOnExit();

			if (!System.getProperty("os.name").toLowerCase().contains("win") && !lib.setExecutable(true)) {
				System.err.println("Can't set lib as executable!");
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getFileName(String name) {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			return "lib" + name + ".dll";
		}

		if (os.contains("mac")) {
			return "lib" + name + ".dylib";
		}

		return "lib" + name + ".so";
	}
}
