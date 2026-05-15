package org.duollectis.mapart.tools.app;

import org.duollectis.mapart.tools.nativee.NativeHolder;
import picocli.CommandLine;

public class MTBootstrap {

    public static void main(String[] args) {

        // TODO: Make this....
        try {
            java.lang.foreign.Arena.ofShared().close();
        } catch (UnsupportedClassVersionError error) {
            System.err.println("Please run programm with a --enable-preview flag!");
            System.exit(-1);
        }

        NativeHolder.init();
        System.exit(new CommandLine(new RootCommand()).execute(args));
    }
}
