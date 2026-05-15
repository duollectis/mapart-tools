package org.duollectis.mapart.tools.commands;

import org.duollectis.mapart.tools.converter.ImageConverter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Command(
		name = "convert",
		description = "Converts the original image using palette.",
		mixinStandardHelpOptions = true
)
public class ConvertCommand implements Runnable {

	@Option(
			names = {"-v", "--version"},
			description = "The target Minecraft version (matches the data folder name).",
			required = true
	)
	private String version;

	@Option(
			names = {"-w", "--width"},
			description = "Maps count on X.",
			required = true
	)
	private int width;

	@Option(
			names = {"-h", "--height"},
			description = "Maps count on Y.",
			required = true
	)
	private int height;

	@Option(
			names = {"-p", "--image-path"},
			description = "Path to the target image.",
			required = true
	)
	private File path;

	@Option(
			names = {"-b", "--blocks"},
			description = "Path to block list..",
			required = true
	)
	private File blocksPath;

	@Option(
			names = {"-o", "--out-path"},
			description = "Path to the converted image dir."
	)
	private File outPath = new File("./rendered");

	@Override
	public void run() {
		if (!path.exists()) {
			System.err.println("Input path not exists!");
			System.exit(-1);
			return;
		}

		if (!path.isFile()) {
			System.err.println("Input path must be a file!");
			System.exit(-1);
			return;
		}

		try (InputStream stream = getClass().getClassLoader().getResourceAsStream("versions/" + version + ".json")) {
			if (stream == null) {
				throw new RuntimeException("Version '%s' not found!".formatted(version));
			}

			if (outPath.isFile()) {
				outPath = outPath.getParentFile();
			}

			if (!outPath.exists() && !outPath.mkdirs()) {
				throw new RuntimeException("Can't create output dir at '%s'".formatted(outPath));
			}

			ImageConverter converter = new ImageConverter();
			converter.run(new String(stream.readAllBytes()), path, outPath, blocksPath, width, height);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
