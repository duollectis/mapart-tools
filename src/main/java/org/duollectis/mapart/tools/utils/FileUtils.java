package org.duollectis.mapart.tools.utils;

import java.io.File;

public class FileUtils {

    public static File getFileOrExit(
        File parent,
        String name,
        boolean isDir,
        String errorMessage) {

        if (parent == null) {
            return null;
        }

        File[] c =
            parent.listFiles(f -> (isDir ? f.isDirectory() : f.isFile()) && f.getName().equals(name));

        if (c == null || c.length == 0) {
            System.err.println(errorMessage);
            System.exit(-1);
            return null;
        }

        return c[0];
    }
}
