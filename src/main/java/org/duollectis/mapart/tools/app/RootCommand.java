package org.duollectis.mapart.tools.app;

import org.duollectis.mapart.tools.commands.ConvertCommand;
import org.duollectis.mapart.tools.commands.ListCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

@Command(
        name = "mapart-tools",
        description = "",
        mixinStandardHelpOptions = true,
        subcommands = {
            ListCommand.class,
            ConvertCommand.class
        }
)
public class RootCommand implements Runnable {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        CommandLine cmd = spec.commandLine();

        if (cmd.getParseResult().subcommand() == null) {
            throw new CommandLine.ParameterException(
                    cmd,
                    "Please specify a command. Run 'mapart-tools --help' for usage."
            );
        }
    }
}
