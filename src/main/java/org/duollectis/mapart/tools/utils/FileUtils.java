package org.duollectis.mapart.tools.utils;

import lombok.experimental.UtilityClass;

import java.io.File;

@UtilityClass
public class FileUtils {

	public File getFileOrExit(File parent, String name, boolean isDir, String errorMessage) {
		if (parent == null) {
			return null;
		}

		File[] matches = parent.listFiles(f -> f.getName().equals(name) && (isDir ? f.isDirectory() : f.isFile()));

		if (matches == null || matches.length == 0) {
			System.err.println(errorMessage);
			System.exit(-1);
			return null;
		}

		return matches[0];
	}

	public String getNameOnly(File file) {
		String fileName = file.getName();
		int lastDot = fileName.lastIndexOf('.');

		return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
	}
}
