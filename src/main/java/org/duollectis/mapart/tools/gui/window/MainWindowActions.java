package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;

import javax.swing.*;
import javax.swing.Timer;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Фасад действий {@link MainWindow}. Делегирует каждую группу операций
 * специализированному классу, сам предоставляет единую точку входа
 * для всех вызовов из UI-компонентов и секционных билдеров.
 */
class MainWindowActions {

	final ConversionActions conversion;
	final ExportImportActions exportImport;
	final BlockActions blocks;
	final FileActions files;

	private final MainWindow w;

	MainWindowActions(MainWindow window) {
		w = window;
		conversion = new ConversionActions(window);
		exportImport = new ExportImportActions(window);
		blocks = new BlockActions(window);
		files = new FileActions(window);
	}

	// ── Превью и конвертация ───────────────────────────────────────────────────

	void scheduleSourcePreview() {
		conversion.scheduleSourcePreview();
	}

	void runSourcePreviewWorker() {
		conversion.runSourcePreviewWorker();
	}

	void scheduleConversion() {
		conversion.scheduleConversion();
	}

	void scheduleConversionIfAuto() {
		conversion.scheduleConversionIfAuto();
	}

	void startConversion() {
		conversion.startConversion();
	}

	String loadPaletteJson(String version) throws Exception {
		return conversion.loadPaletteJson(version);
	}

	void onDitheringSuccess(Ditherer ditherer) {
		conversion.onDitheringSuccess(ditherer);
	}

	void onConversionError(String message) {
		conversion.onConversionError(message);
	}

	void onConversionCancelled() {
		conversion.onConversionCancelled();
	}

	void setConvertingState(boolean converting) {
		conversion.setConvertingState(converting);
	}

	void closePreviousDitherer() {
		conversion.closePreviousDitherer();
	}

	void startConversionForBlockList() {
		conversion.startConversionForBlockList();
	}

	void syncSourcePreviewMapCount() {
		conversion.syncSourcePreviewMapCount();
	}

	void scaleMapCount(double factor) {
		conversion.scaleMapCount(factor);
	}

	void autoFitMapCount() {
		conversion.autoFitMapCount();
	}

	DitherSettings buildDitherSettingsFromUi() {
		return conversion.buildDitherSettingsFromUi();
	}

	ImageAdjustments buildAdjustments() {
		return conversion.buildAdjustments();
	}

	// ── Экспорт и импорт ──────────────────────────────────────────────────────

	void startExport() {
		exportImport.startExport();
	}

	void savePreview() {
		exportImport.savePreview();
	}

	void startImport() {
		exportImport.startImport();
	}

	void startImportFromFiles(List<File> schematicFiles) {
		exportImport.startImportFromFiles(schematicFiles);
	}

	void setImportingState(boolean importing) {
		exportImport.setImportingState(importing);
	}

	void setExportingState(boolean exporting) {
		exportImport.setExportingState(exporting);
	}

	void syncExportButtonLabel() {
		exportImport.syncExportButtonLabel();
	}

	// ── Блоки ─────────────────────────────────────────────────────────────────

	void removeBlockAndReconvert(String blockId) {
		blocks.removeBlockAndReconvert(blockId);
	}

	void saveBlocksToFile(File file) {
		blocks.saveBlocksToFile(file);
	}

	String buildBlocksFileContent(Map<String, BlockData> paletteBlocks) {
		return blocks.buildBlocksFileContent(paletteBlocks);
	}

	int computeBlockPercent(BlockData block, WeightedSelector<BlockData> selector) {
		return blocks.computeBlockPercent(block, selector);
	}

	void tryAutoLoadBlocks() {
		blocks.tryAutoLoadBlocks();
	}

	void loadBlocksFromFile(File file) {
		blocks.loadBlocksFromFile(file);
	}


	void syncBlocksFromFieldIfNeeded() {
		blocks.syncBlocksFromFieldIfNeeded();
	}

	File resolveBlocksTargetFile() {
		return blocks.resolveBlocksTargetFile();
	}

	// ── Файловые операции ─────────────────────────────────────────────────────

	void chooseImageOrSchematic() {
		files.chooseImageOrSchematic();
	}

	void chooseImage() {
		files.chooseImage();
	}

	void chooseImageAsNewLayer() {
		files.chooseImageAsNewLayer();
	}

	void chooseBlocks() {
		files.chooseBlocks();
	}

	void chooseOutDir() {
		files.chooseOutDir();
	}

	File resolveImageFile() {
		return files.resolveImageFile();
	}

	File resolveBlocksFile() {
		return files.resolveBlocksFile();
	}

	File resolveOutDir() {
		return files.resolveOutDir();
	}

	void setupImageDropTarget(ImagePreviewPanel panel) {
		files.setupImageDropTarget(panel);
	}

	void setupWindowDropTarget(java.awt.Component component) {
		files.setupWindowDropTarget(component);
	}

	void pasteImageFromClipboard() {
		files.pasteImageFromClipboard();
	}

	String[] loadVersions() {
		return files.loadVersions();
	}

	// ── Утилиты ───────────────────────────────────────────────────────────────

	void log(String message) {
		w.logArea.appendLine(message);
	}

	void showError(String message) {
		w.progressBar.setValue(100);
		w.progressBar.setString(message);
		w.progressBar.setForeground(GuiApp.theme.getError());
		w.progressBar.repaint();
		log(UpdatableRegistry.translate("dialog.error_title") + ": " + message);
	}

	void addHoverEffect(JButton btn) {
		UiAnimator.addHoverEffect(btn);
	}
}
