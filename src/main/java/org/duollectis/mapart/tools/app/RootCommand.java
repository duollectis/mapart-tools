package org.duollectis.mapart.tools.app;

import org.duollectis.mapart.tools.cli.ConvertCommand;
import org.duollectis.mapart.tools.cli.ListCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

@Command(
	name = "mapart-tools",
	description = "Инструменты для создания карт-артов в Майнкрафте.",
	mixinStandardHelpOptions = true,
	subcommands = {
		ListCommand.class,
		ConvertCommand.class
	}
)
public class RootCommand implements Runnable {

	@Spec
	private CommandLine.Model.CommandSpec spec;

	@Override
	public void run() {
		CommandLine cmd = spec.commandLine();

		if (cmd.getParseResult().subcommand() == null) {
			throw new CommandLine.ParameterException(
				cmd,
				"Укажите подкоманду. Запустите 'mapart-tools --help' для справки."
			);
		}
	}
}
