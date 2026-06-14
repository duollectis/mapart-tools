package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.app.AppMessages;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Отвечает за файловые операции: выбор изображения/схематика/блоков/папки вывода,
 * загрузку изображения, drag-and-drop, вставку из буфера обмена, загрузку версий.
 */
final class FileActions {

	private final MainWindow w;

	FileActions(MainWindow window) {
		w = window;
	}

	void chooseImageOrSchematic() {
		FileDialog dialog = new FileDialog(w, UpdatableRegistry.translate("dialog.choose_image_or_schematic"), FileDialog.LOAD);
		dialog.setMultipleMode(true);
		dialog.setFilenameFilter((dir, name) -> {
			String lower = name.toLowerCase();
			return lower.endsWith(".png")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".bmp")
				|| lower.endsWith(".gif")
				|| lower.endsWith(".nbt")
				|| lower.endsWith(".litematic");
		});
		dialog.setVisible(true);

		File[] selected = dialog.getFiles();

		if (selected == null || selected.length == 0) {
			return;
		}

		String firstName = selected[0].getName().toLowerCase();
		boolean isSchematic = firstName.endsWith(".nbt") || firstName.endsWith(".litematic");

		if (isSchematic) {
			w.actions.startImportFromFiles(Arrays.stream(selected).toList());
		} else {
			for (File file : selected) {
				addLayerFromFile(file);
			}
		}
	}

	void chooseImage() {
		FileDialog dialog = new FileDialog(w, UpdatableRegistry.translate("dialog.choose_image"), FileDialog.LOAD);
		dialog.setMultipleMode(true);
		dialog.setFilenameFilter((dir, name) -> isImageFile(name));
		dialog.setVisible(true);

		File[] selected = dialog.getFiles();

		if (selected == null || selected.length == 0) {
			return;
		}

		for (File file : selected) {
			addLayerFromFile(file);
		}
	}

	void chooseImageAsNewLayer() {
		chooseImage();
	}

	void chooseBlocks() {
		FileDialog dialog = new FileDialog(w, UpdatableRegistry.translate("dialog.choose_blocks"), FileDialog.LOAD);
		dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		w.actions.loadBlocksFromFile(new File(dialog.getDirectory(), dialog.getFile()));
	}

	void chooseOutDir() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(UpdatableRegistry.translate("dialog.choose_outdir"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		if (chooser.showOpenDialog(w) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		w.outPathField.setText(chooser.getSelectedFile().getAbsolutePath());
	}

	void addLayerFromFile(File file) {
		try {
			BufferedImage image = ImageIO.read(file);

			if (image == null) {
				w.actions.showError(UpdatableRegistry.translate("error.image_load_failed", file.getName()));
				return;
			}

			w.selectedImageFile = file;
			w.rawSourceImage = image;
			w.sourcePreview.addLayer(image, file.getName());
			w.sourcePreview.setActiveLayerSourcePath(file.getAbsolutePath());
			w.actions.scheduleSourcePreview();
			w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_IMAGE_LOADED, file.getName(), image.getWidth(), image.getHeight()));
		} catch (IOException e) {
			w.actions.showError(UpdatableRegistry.translate("error.image_load_failed", e.getMessage()));
		}
	}

	private static boolean isImageFile(String name) {
		String lower = name.toLowerCase();
		return lower.endsWith(".png")
			|| lower.endsWith(".jpg")
			|| lower.endsWith(".jpeg")
			|| lower.endsWith(".bmp")
			|| lower.endsWith(".gif");
	}

	File resolveImageFile() {
		if (w.selectedImageFile != null && w.selectedImageFile.exists()) {
			return w.selectedImageFile;
		}

		w.actions.showError(UpdatableRegistry.translate("error.no_image"));
		return null;
	}

	File resolveBlocksFile() {
		String path = w.blocksPathField.getText().strip();

		if (!path.isBlank()) {
			File file = new File(path);

			if (file.exists() && file.isFile()) {
				return file;
			}

			w.actions.showError(UpdatableRegistry.translate("error.blocks_not_found", path));
			return null;
		}

		File defaultFile = new File("./blocks.txt");

		if (defaultFile.exists()) {
			return defaultFile;
		}

		w.actions.showError(UpdatableRegistry.translate("error.blocks_not_found", "./blocks.txt"));
		return null;
	}

	File resolveOutDir() {
		String path = w.outPathField.getText().strip();
		File dir = path.isBlank() ? new File("./rendered") : new File(path);

		if (dir.exists()) {
			return dir;
		}

		if (!dir.mkdirs()) {
			w.actions.showError(UpdatableRegistry.translate("error.outdir_failed", dir.getAbsolutePath()));
			return null;
		}

		return dir;
	}

	void setupImageDropTarget(ImagePreviewPanel panel) {
		new DropTarget(
			panel, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
				@Override
				public void drop(DropTargetDropEvent event) {
					handleFileDrop(event);
				}
			}
		);
	}

	void setupWindowDropTarget(Component component) {
		new DropTarget(
			component, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
				@Override
				public void drop(DropTargetDropEvent event) {
					handleFileDrop(event);
				}
			}
		);
	}

	private void handleFileDrop(DropTargetDropEvent event) {
		try {
			event.acceptDrop(DnDConstants.ACTION_COPY);
			List<?> raw = (List<?>) event.getTransferable()
				.getTransferData(DataFlavor.javaFileListFlavor);

			if (raw.isEmpty()) {
				return;
			}

			List<File> files = raw.stream().map(o -> (File) o).toList();

			List<File> schematics = files.stream()
				.filter(f -> {
					String lower = f.getName().toLowerCase();
					return lower.endsWith(".nbt") || lower.endsWith(".litematic");
				})
				.toList();

			if (!schematics.isEmpty()) {
				w.actions.startImportFromFiles(schematics);
				return;
			}

			files.stream()
				.filter(f -> isImageFile(f.getName()))
				.forEach(this::addLayerFromFile);
		} catch (Exception e) {
			w.actions.showError(UpdatableRegistry.translate("error.drop_failed", e.getMessage()));
		}
	}

	void pasteImageFromClipboard() {
		try {
			var transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);

			if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				return;
			}

			BufferedImage image = (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor);

			if (image == null) {
				return;
			}

			File tempFile = Files.createTempFile("mapart_paste_", ".png").toFile();
			tempFile.deleteOnExit();
			ImageIO.write(image, "png", tempFile);
			addLayerFromFile(tempFile);
			w.actions.log(UpdatableRegistry.translate("log.clipboard_paste", image.getWidth(), image.getHeight()));
		} catch (Exception e) {
			w.actions.showError(UpdatableRegistry.translate("error.image_load_failed", e.getMessage()));
		}
	}

	String[] loadVersions() {
		try (InputStream stream = w.getClass().getClassLoader().getResourceAsStream("versions/versions.txt")) {
			if (stream == null) {
				return new String[]{"1.21.11"};
			}

			String content = new String(stream.readAllBytes());
			String[] versions = content.lines()
				.map(String::strip)
				.filter(l -> !l.isBlank())
				.toArray(String[]::new);

			return versions.length > 0 ? versions : new String[]{"1.21.11"};
		} catch (IOException e) {
			return new String[]{"1.21.11"};
		}
	}
}
