package org.duollectis.mapart.tools.commands;

import org.duollectis.mapart.tools.MapartTools;
import picocli.CommandLine.Command;

@Command(
    name = "list",
    description = "Show all available versions.")
public class ListCommand implements Runnable {

    @Override
    public void run() {
        if (!MapartTools.DATA_DIR.exists() && !MapartTools.DATA_DIR.mkdirs()) {
            System.err.println("Can't create a data dir!");
            return;
        }

        System.out.println("Available versions:");

        String[] files = MapartTools.DATA_DIR.list();

        if (files != null) {
            for (String name : files) {
                System.out.println(" - " + name);
            }
        }

        return;
    }
}
