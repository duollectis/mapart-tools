package org.duollectis.mapart.tools.commands;

import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;

@Command(
	name = "list",
	description = "Показывает список доступных версий Майнкрафта.",
	mixinStandardHelpOptions = true
)
public class ListCommand implements Runnable {

	private static final String VERSIONS_RESOURCE = "versions/versions.txt";

	@Override
	public void run() {
		System.out.println("Доступные версии:");

		try (InputStream stream = getClass().getClassLoader().getResourceAsStream(VERSIONS_RESOURCE)) {
			if (stream == null) {
				throw new RuntimeException("Файл списка версий не найден в ресурсах!");
			}

			String content = new String(stream.readAllBytes());

			for (String version : content.split("\n")) {
				System.out.println(" - " + version);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
