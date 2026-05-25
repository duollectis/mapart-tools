package org.duollectis.mapart.tools.app;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.nativee.NativeHolder;
import picocli.CommandLine;

public class MTBootstrap {

	public static void main(String[] args) {
		// TODO: Проверка поддержки FFM API (Project Panama) — требует --enable-preview
		try {
			java.lang.foreign.Arena.ofShared().close();
		} catch (UnsupportedClassVersionError error) {
			System.err.println("Запустите программу с флагом --enable-preview!");
			System.exit(-1);
		}

		NativeHolder.init();

		if (args.length == 0) {
			GuiApp.launch();
			return;
		}

		CommandLine cli = new CommandLine(new RootCommand());
		cli.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
			ex.printStackTrace(System.err);
			return 1;
		});

		System.exit(cli.execute(args));
	}
}
