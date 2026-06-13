package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.gui.AppMessages;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.worker.ExportWorker;
import org.duollectis.mapart.tools.gui.worker.ImportWorker;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Отвечает за экспорт схематиков, импорт схематиков, сохранение превью
 * и управление состоянием UI во время этих операций.
 */
final class ExportImportActions {

	private final MainWindow w;

	ExportImportActions(MainWindow window) {
		w = window;
	}

	void startExport() {
		if (w.lastDitherer == null) {
			return;
		}

		if (w.activeExportWorker != null && !w.activeExportWorker.isDone()) {
			return;
		}

		File outDir = w.actions.resolveOutDir();

		if (outDir == null) {
			return;
		}

		int mapWidth = w.mapSizeControl.getMapWidth();
		int mapHeight = w.mapSizeControl.getMapHeight();

		setExportingState(true);
		DiscordRpc.setExporting();
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_EXPORT_START, outDir.getAbsolutePath()));

		SupportBlockSettings effectiveSupport = w.supportSettings != null
			? w.supportSettings
			: SupportBlockSettings.single(MainWindow.DEFAULT_SUPPORT_BLOCK);

		SchematicFormat format = w.formatCombo != null
			? (SchematicFormat) w.formatCombo.getSelectedItem()
			: SchematicFormat.NBT;

		StaircaseMode staircaseMode = w.staircaseModeCombo != null
				&& w.staircaseModeCombo.getSelectedItem() instanceof StaircaseMode mode
			? mode
			: StaircaseMode.STAIRCASE;

		w.activeExportWorker = new ExportWorker(
			w.lastDitherer,
			outDir,
			mapWidth,
			mapHeight,
			effectiveSupport,
			format,
			staircaseMode,
			w.actions::log,
			this::onExportSuccess,
			this::onExportError
		);

		w.activeExportWorker.execute();
	}

	void savePreview() {
		if (w.lastDitherer == null) {
			return;
		}

		FileDialog dialog = new FileDialog(w, UpdatableRegistry.translate("dialog.save_preview"), FileDialog.SAVE);
		dialog.setFile("preview.png");
		dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		File file = new File(dialog.getDirectory(), dialog.getFile());

		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getAbsolutePath() + ".png");
		}

		try {
			BufferedImage preview = w.lastDitherer.createPreview();
			ImageIO.write(preview, "PNG", file);
			w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_PREVIEW_SAVED, file.getAbsolutePath()));
		} catch (IOException e) {
			w.actions.showError(UpdatableRegistry.translate("error.preview_save_failed", e.getMessage()));
		}
	}

	void startImport() {
		if (w.activeImportWorker != null && !w.activeImportWorker.isDone()) {
			return;
		}

		FileDialog dialog = new FileDialog(w, UpdatableRegistry.translate("dialog.choose_schematic"), FileDialog.LOAD);
		dialog.setMultipleMode(true);
		dialog.setFilenameFilter((dir, name) -> {
			String lower = name.toLowerCase();
			return lower.endsWith(".nbt") || lower.endsWith(".litematic");
		});
		dialog.setVisible(true);

		File[] selected = dialog.getFiles();

		if (selected == null || selected.length == 0) {
			return;
		}

		startImportFromFiles(Arrays.stream(selected).toList());
	}

	void startImportFromFiles(List<File> schematicFiles) {
		if (w.activeImportWorker != null && !w.activeImportWorker.isDone()) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		String paletteJson;

		try {
			paletteJson = w.actions.loadPaletteJson(version);
		} catch (Exception e) {
			w.actions.showError(UpdatableRegistry.translate("error.import_palette_failed", e.getMessage()));
			return;
		}

		boolean addToBlocks = w.importAddToBlocks;

		String firstFileName = schematicFiles.size() == 1
			? schematicFiles.get(0).getName()
			: UpdatableRegistry.translate(AppMessages.LOG_IMPORT_START_MULTI, schematicFiles.size());

		setImportingState(true);
		DiscordRpc.setImporting();
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_IMPORT_START, firstFileName));

		w.progressBar.setIndeterminate(false);
		w.progressBar.setValue(0);

		w.activeImportWorker = new ImportWorker(
			schematicFiles,
			paletteJson,
			msg -> w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_IMPORT_PROGRESS, msg)),
			results -> onImportSuccess(results, addToBlocks),
			this::onImportError
		);

		w.activeImportWorker.addPropertyChangeListener(event -> {
			if ("progress".equals(event.getPropertyName())) {
				w.progressBar.setValue((Integer) event.getNewValue());
			}
		});

		w.activeImportWorker.execute();
	}

	void onExportSuccess() {
		setExportingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setString(AppMessages.PROGRESS_EXPORT_DONE);
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_EXPORT_DONE, w.outPathField.getText()));
	}

	void onExportError(String message) {
		setExportingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setString(AppMessages.PROGRESS_EXPORT_ERROR);
		w.progressBar.setForeground(GuiApp.theme.getError());
		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_EXPORT_ERROR, message));
		w.actions.showError(UpdatableRegistry.translate("error.export", message));
	}

	void onImportSuccess(List<ImportWorker.SingleResult> results, boolean addToBlocks) {
		setImportingState(false);
		DiscordRpc.setIdle();

		if (results.isEmpty()) {
			w.progressBar.setString(AppMessages.PROGRESS_IMPORT_ERROR);
			w.progressBar.setForeground(GuiApp.theme.getError());
			return;
		}

		int[] gridSize = detectGridSize(results);
		int totalCols = gridSize[0];
		int totalRows = gridSize[1];
		boolean hasGridCoords = results.stream().anyMatch(r -> r.gridCol() >= 0 && r.gridRow() >= 0);

		// Сетку только расширяем — никогда не сужаем, чтобы не выбивать уже добавленные слои
		int newCols = Math.max(w.mapSizeControl.getMapWidth(), totalCols);
		int newRows = Math.max(w.mapSizeControl.getMapHeight(), totalRows);
		w.mapSizeControl.setMapWidth(Math.clamp(newCols, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.mapSizeControl.setMapHeight(Math.clamp(newRows, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.sourcePreview.setMapCount(newCols, newRows);

		SchematicImportResult lastImport = null;
		BufferedImage lastPreview = null;

		for (ImportWorker.SingleResult single : results) {
			SchematicImportResult importResult = single.importResult();
			BufferedImage preview = single.preview();
			String layerName = importResult.blockIds().isEmpty()
				? single.fileName()
				: "map_" + (single.gridCol() + 1) + "_" + (single.gridRow() + 1);

			int col = single.gridCol() >= 0 ? single.gridCol() : (newCols - 1) / 2;
				int row = single.gridRow() >= 0 ? single.gridRow() : (newRows - 1) / 2;
				w.sourcePreview.addLayerAtGridCell(preview, layerName, col, row, newCols, newRows);

			if (addToBlocks) {
				w.enabledBlocks.addAll(importResult.blockIds());
			}

			lastImport = importResult;
			lastPreview = preview;
		}

		w.resetSourceViewOnNextImage = true;
		w.lastImportResult = lastImport;

		saveImportPreviewAsTemp(lastPreview);

		if (addToBlocks) {
			w.actions.syncBlocksFromFieldIfNeeded();
		}

		w.progressBar.setString(AppMessages.PROGRESS_IMPORT_DONE);
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		w.actions.log(UpdatableRegistry.translate(
			"log.import_done",
			totalCols,
			totalRows,
			lastImport.blockIds().size()
		));
	}

	private int[] detectGridSize(List<ImportWorker.SingleResult> results) {
		int maxCol = 0;
		int maxRow = 0;

		for (ImportWorker.SingleResult single : results) {
			if (single.gridCol() >= 0) {
				maxCol = Math.max(maxCol, single.gridCol());
			}

			if (single.gridRow() >= 0) {
				maxRow = Math.max(maxRow, single.gridRow());
			}
		}

		// Если ни у одного файла нет координат — сетка 1×1 (одиночный файл)
		return new int[]{maxCol + 1, maxRow + 1};
	}

	void saveImportPreviewAsTemp(BufferedImage preview) {
		try {
			File tempFile = Files.createTempFile("mapart_import_", ".png").toFile();
			tempFile.deleteOnExit();
			ImageIO.write(preview, "png", tempFile);

			w.selectedImageFile = tempFile;
			w.rawSourceImage = preview;
		} catch (IOException e) {
			w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_ERROR, e.getMessage()));
		}
	}

	void onImportError(String message) {
		setImportingState(false);
		DiscordRpc.setIdle();
		w.progressBar.setString(AppMessages.PROGRESS_IMPORT_ERROR);
		w.progressBar.setForeground(GuiApp.theme.getError());
		System.err.println("[Import error] " + message);
		w.actions.showError(UpdatableRegistry.translate("error.import_failed", message));
	}

	void setImportingState(boolean importing) {
		if (w.importButton != null) {
			w.importButton.setEnabled(!importing);
		}

		w.convertButton.setEnabled(!importing);

		if (importing) {
			w.progressBar.setIndeterminate(false);
			w.progressBar.setValue(0);
			w.progressBar.setString(AppMessages.PROGRESS_IMPORTING);
			w.progressBar.setForeground(GuiApp.theme.getImportingProgressFg());
		}
	}

	void setExportingState(boolean exporting) {
		w.exportButton.setEnabled(!exporting);
		w.convertButton.setEnabled(!exporting);
		w.progressBar.setIndeterminate(exporting);

		if (exporting) {
			w.progressBar.setString(AppMessages.PROGRESS_EXPORTING);
			w.progressBar.setForeground(GuiApp.theme.getWarn());
		}
	}

	void syncExportButtonLabel() {
		if (w.exportButton == null || w.formatCombo == null) {
			return;
		}

		SchematicFormat selected = w.formatCombo.getSelectedItem();
		String key = selected == SchematicFormat.LITEMATIC ? "btn.export_litematic" : "btn.export_nbt";
		w.exportButton.setText(UpdatableRegistry.translate(key));
	}

}
