package org.duollectis.mapart.tools.commands;

import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;

@Command(
		name = "list",
		description = "Show all available versions."
)
public class ListCommand implements Runnable {

	@Override
	public void run() {
		System.out.println("Available versions:");

		try (InputStream stream = getClass().getClassLoader().getResourceAsStream("versions/versions.txt")) {
			assert stream != null;
			String versions = new String(stream.readAllBytes());

			for (String ver : versions.split("\n")) {
				System.out.println(" - " + ver);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
