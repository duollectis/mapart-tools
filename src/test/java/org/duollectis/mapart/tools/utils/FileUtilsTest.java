package org.duollectis.mapart.tools.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

	@TempDir
	Path tempDir;

	// ─── getNameOnly ─────────────────────────────────────────────────────────────

	@Test
	void getNameOnly_fileWithExtension_returnsNameWithoutExtension() {
		File file = new File("image.png");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo("image");
	}

	@Test
	void getNameOnly_fileWithLongExtension_returnsNameWithoutExtension() {
		File file = new File("schematic.litematic");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo("schematic");
	}

	@Test
	void getNameOnly_fileWithoutExtension_returnsFullName() {
		File file = new File("README");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo("README");
	}

	@Test
	void getNameOnly_fileWithLeadingDot_returnsFullName() {
		// ".gitignore" — dot at position 0, lastDot = 0, condition lastDot > 0 is false
		File file = new File(".gitignore");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo(".gitignore");
	}

	@Test
	void getNameOnly_fileWithMultipleDots_returnsNameUpToLastDot() {
		File file = new File("archive.tar.gz");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo("archive.tar");
	}

	@Test
	void getNameOnly_fileWithPath_returnsOnlyFileNameWithoutExtension() {
		File file = new File("/some/path/to/image.png");

		assertThat(FileUtils.getNameOnly(file)).isEqualTo("image");
	}

	// ─── getFileOrExit ───────────────────────────────────────────────────────────

	@Test
	void getFileOrExit_nullParent_returnsNull() {
		File result = FileUtils.getFileOrExit(null, "any.txt", false, "error");

		assertThat(result).isNull();
	}

	@Test
	void getFileOrExit_existingFile_returnsFile() throws IOException {
		File parent = tempDir.toFile();
		File target = new File(parent, "test.txt");
		target.createNewFile();

		File result = FileUtils.getFileOrExit(parent, "test.txt", false, "error");

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("test.txt");
	}

	@Test
	void getFileOrExit_existingDirectory_returnsDirectory() {
		File parent = tempDir.toFile();
		File subDir = new File(parent, "subdir");
		subDir.mkdir();

		File result = FileUtils.getFileOrExit(parent, "subdir", true, "error");

		assertThat(result).isNotNull();
		assertThat(result.isDirectory()).isTrue();
	}
}
