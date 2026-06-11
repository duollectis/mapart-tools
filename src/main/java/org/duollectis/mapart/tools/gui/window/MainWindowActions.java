package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.*;
import org.duollectis.mapart.tools.converter.schematic.SchematicImportResult;
import org.duollectis.mapart.tools.gui.AppPreferences;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.Lang;
import org.duollectis.mapart.tools.gui.dialog.BlockListDialog;
import org.duollectis.mapart.tools.gui.dialog.BlockPickerDialog;
import org.duollectis.mapart.tools.gui.dialog.SettingsDialog;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.ThemeTransition;
import org.duollectis.mapart.tools.gui.util.UiAnimator;
import org.duollectis.mapart.tools.gui.widget.ImagePreviewPanel;
import org.duollectis.mapart.tools.gui.widget.ModernCheckBox;
import org.duollectis.mapart.tools.gui.worker.ConversionWorker;
import org.duollectis.mapart.tools.gui.worker.ExportWorker;
import org.duollectis.mapart.tools.gui.worker.ImportWorker;
import org.duollectis.mapart.tools.utils.image.ImageAdjustments;
import org.duollectis.mapart.tools.utils.image.ImageUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Содержит всю бизнес-логику MainWindow: конвертацию, экспорт, импорт,
 * управление блоками, файловые операции и вспомогательные методы.
 * Получает доступ к полям окна через package-private ссылку.
 */
class MainWindowActions {

	private final MainWindow w;

	MainWindowActions(MainWindow window) {
		w = window;
	}

	// ── Превью источника ───────────────────────────────────────────────────────

	/**
	 * Throttle-обновление превью оригинала: запускает обработку мгновенно в фоновом потоке.
	 * Если воркер уже работает — ставит флаг pending, и по завершении текущего
	 * автоматически запускается ещё один с актуальными настройками.
	 */
	void scheduleSourcePreview() {
		if (w.rawSourceImage == null) {
			return;
		}

		w.sourcePreviewPending = true;

		if (w.sourcePreviewRunning) {
			return;
		}

		runSourcePreviewWorker();
	}

	void runSourcePreviewWorker() {
		w.sourcePreviewPending = false;
		w.sourcePreviewRunning = true;

		BufferedImage src = w.rawSourceImage;
		ImageAdjustments snapshot = buildAdjustments();

		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() {
				return ImageUtils.applyAdjustments(src, snapshot);
			}

			@Override
			protected void done() {
				try {
					w.sourcePreview.setImage(get());

					if (w.resetSourceViewOnNextImage) {
						w.resetSourceViewOnNextImage = false;
						w.sourcePreview.resetDisplayOffset();
					}
				} catch (Exception ignored) {
				}

				w.sourcePreviewRunning = false;

				if (w.sourcePreviewPending) {
					runSourcePreviewWorker();
				}
			}
		}.execute();
	}

	// ── Конвертация ────────────────────────────────────────────────────────────

	/**
	 * Запускает конвертацию с debounce 400ms только если включена кнопка «Авто».
	 */
	void scheduleConversion() {
		if (w.autoConvertToggle == null || !w.autoConvertToggle.isSelected()) {
			return;
		}

		if (w.conversionDebounceTimer != null) {
			w.conversionDebounceTimer.stop();
		}

		w.conversionDebounceTimer = new Timer(400, e -> startConversion());
		w.conversionDebounceTimer.setRepeats(false);
		w.conversionDebounceTimer.start();
	}

	void scheduleConversionIfAuto() {
		scheduleConversion();
	}

	void startConversion() {
		if (w.enabledBlocks.isEmpty()) {
			showError(Lang.t("error.no_blocks_selected"));
			return;
		}

		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}

		File imageFile = resolveImageFile();
		File blocksFile = resolveBlocksFile();

		if (imageFile == null || blocksFile == null) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		int mapWidth = (int) w.widthSpinner.getValue();
		int mapHeight = (int) w.heightSpinner.getValue();
		Ditherer.Algorithm algorithm = resolveAlgorithm();

		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			onConversionError(e.getMessage());
			return;
		}

		closePreviousDitherer();
		setConvertingState(true);
		log(Lang.t("log.dither_start", imageFile.getName(), mapWidth, mapHeight));

		StaircaseMode staircaseMode = w.staircaseModeCombo.getSelectedItem() instanceof StaircaseMode mode
			? mode
			: StaircaseMode.STAIRCASE;

		w.activeConversionWorker = new ConversionWorker(
			paletteJson,
			imageFile,
			blocksFile,
			mapWidth,
			mapHeight,
			algorithm,
			buildAdjustments(),
			buildDitherSettingsFromUi(),
			buildCropSettingsFromUi(),
			w.blockSelectors,
			staircaseMode,
			this::log,
			this::onDitheringSuccess,
			this::onConversionError,
			this::onConversionCancelled
		);

		w.activeConversionWorker.addPropertyChangeListener(event -> {
			if (!"progress".equals(event.getPropertyName())) {
				return;
			}

			int percent = (int) event.getNewValue();
			w.progressBar.setValue(percent);
			w.progressBar.setString(Lang.t("progress.dithering") + " " + percent + "%");
		});

		w.activeConversionWorker.execute();
	}

	String loadPaletteJson(String version) throws Exception {
		String cached = w.paletteCache.get(version);

		if (cached != null) {
			return cached;
		}

		String resourcePath = "versions/" + version + ".zip";
		String jsonEntry = version + ".json";

		try (InputStream raw = w.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (raw == null) {
				throw new RuntimeException(Lang.t("error.version_not_found", version));
			}

			try (ZipInputStream zip = new ZipInputStream(raw)) {
				ZipEntry entry;

				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().equals(jsonEntry)) {
						String json = new String(zip.readAllBytes());
						w.paletteCache.put(version, json);
						return json;
					}

					zip.closeEntry();
				}
			}
		}

		throw new RuntimeException(Lang.t("error.palette_entry_not_found", jsonEntry, version));
	}

	// ── Экспорт ────────────────────────────────────────────────────────────────

	void startExport() {
		if (w.lastDitherer == null) {
			return;
		}

		if (w.activeExportWorker != null && !w.activeExportWorker.isDone()) {
			return;
		}

		File outDir = resolveOutDir();

		if (outDir == null) {
			return;
		}

		int mapWidth = (int) w.widthSpinner.getValue();
		int mapHeight = (int) w.heightSpinner.getValue();

		setExportingState(true);
		log(Lang.t("log.export_start", outDir.getAbsolutePath()));

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
			this::log,
			this::onExportSuccess,
			this::onExportError
		);

		w.activeExportWorker.execute();
	}

	void savePreview() {
		if (w.lastDitherer == null) {
			return;
		}

		FileDialog dialog = new FileDialog(w, Lang.t("dialog.save_preview"), FileDialog.SAVE);
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
			log(Lang.t("log.preview_saved", file.getAbsolutePath()));
		} catch (IOException e) {
			showError(Lang.t("error.preview_save_failed", e.getMessage()));
		}
	}

	// ── Импорт ─────────────────────────────────────────────────────────────────

	void startImport() {
		if (w.activeImportWorker != null && !w.activeImportWorker.isDone()) {
			return;
		}

		FileDialog dialog = new FileDialog(w, Lang.t("dialog.choose_schematic"), FileDialog.LOAD);
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

		List<File> schematicFiles = Arrays.stream(selected).toList();
		int[] grid = detectGrid(schematicFiles);

		String version = (String) w.versionCombo.getSelectedItem();
		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			showError(Lang.t("error.import_palette_failed", e.getMessage()));
			return;
		}

		ImportOptions options = showImportOptionsDialog();

		String firstFileName = schematicFiles.size() == 1
			? schematicFiles.get(0).getName()
			: Lang.t("log.import_start_multi", schematicFiles.size());

		setImportingState(true);
		log(Lang.t("log.import_start", firstFileName));

		w.activeImportWorker = new ImportWorker(
			schematicFiles,
			paletteJson,
			grid[0],
			grid[1],
			options.xyOrder(),
			msg -> log(Lang.t("log.import_progress", msg)),
			(result, preview) -> onImportSuccess(result, preview, options.addToBlocks()),
			this::onImportError
		);

		w.activeImportWorker.execute();
	}

	int[] detectGrid(List<File> files) {
		if (files.size() == 1) {
			return new int[]{1, 1};
		}

		java.util.regex.Pattern mapPattern = java.util.regex.Pattern.compile("map_(\\d+)_(\\d+)(?:\\..+)?$");
		int maxX = 0;
		int maxY = 0;

		for (File file : files) {
			java.util.regex.Matcher matcher = mapPattern.matcher(file.getName());

			if (matcher.find()) {
				maxX = Math.max(maxX, Integer.parseInt(matcher.group(1)));
				maxY = Math.max(maxY, Integer.parseInt(matcher.group(2)));
			}
		}

		if (maxX > 0 && maxY > 0) {
			return new int[]{maxX, maxY};
		}

		int autoWidth = (int) Math.ceil(Math.sqrt(files.size()));
		int autoHeight = (int) Math.ceil((double) files.size() / autoWidth);

		return new int[]{autoWidth, autoHeight};
	}

	ImportOptions showImportOptionsDialog() {
		ModernCheckBox addBlocksCheckBox = new ModernCheckBox(Lang.t("import.add_to_palette"));
		addBlocksCheckBox.setSelected(false);
		addBlocksCheckBox.setForeground(GuiApp.theme.getText());
		addBlocksCheckBox.setOpaque(false);

		boolean[] xySelected = {true};

		JButton xyButton = buildOrderToggleButton("XY", true, xySelected);
		JButton yxButton = buildOrderToggleButton("YX", false, xySelected);

		xyButton.addActionListener(e -> {
			xySelected[0] = true;
			xyButton.repaint();
			yxButton.repaint();
		});

		yxButton.addActionListener(e -> {
			xySelected[0] = false;
			xyButton.repaint();
			yxButton.repaint();
		});

		JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		orderRow.setOpaque(false);
		orderRow.add(xyButton);
		orderRow.add(yxButton);

		JLabel orderLabel = new JLabel(Lang.t("import.order_label"));
		orderLabel.setForeground(GuiApp.theme.getTextDim());
		orderLabel.setFont(new Font("SansSerif", Font.BOLD, 11));

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(GuiApp.theme.getBgCard());
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		panel.add(addBlocksCheckBox);
		panel.add(Box.createVerticalStrut(12));
		panel.add(orderLabel);
		panel.add(Box.createVerticalStrut(6));
		panel.add(orderRow);

		JOptionPane.showMessageDialog(w, panel, Lang.t("import.options_title"), JOptionPane.PLAIN_MESSAGE);

		return new ImportOptions(addBlocksCheckBox.isSelected(), xySelected[0]);
	}

	private JButton buildOrderToggleButton(String label, boolean isXy, boolean[] xySelected) {
		JButton btn = new JButton(label) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				boolean active = isXy == xySelected[0];
				Color bg = active ? GuiApp.theme.getAccent() : GuiApp.theme.getBgInput();
				Color fg = active ? GuiApp.theme.getBgDeep() : GuiApp.theme.getText();
				Color border = active ? GuiApp.theme.getAccent() : GuiApp.theme.getBorder();

				if (getModel().isRollover() && !active) {
					bg = new Color(GuiApp.theme.getBorder().getRed(), GuiApp.theme.getBorder().getGreen(), GuiApp.theme.getBorder().getBlue());
				}

				g2.setColor(bg);

				if (isXy) {
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
					g2.fillRect(getWidth() / 2, 0, getWidth() / 2, getHeight());
				} else {
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
					g2.fillRect(0, 0, getWidth() / 2, getHeight());
				}

				g2.setColor(border);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

				if (!isXy) {
					g2.drawLine(0, 0, 0, getHeight());
				}

				g2.dispose();
				setForeground(fg);
				super.paintComponent(g);
			}
		};

		btn.setFont(new Font("SansSerif", Font.BOLD, 13));
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createEmptyBorder(7, 22, 7, 22));

		return btn;
	}

	record ImportOptions(boolean addToBlocks, boolean xyOrder) {}

	// ── Обработчики результатов ────────────────────────────────────────────────

	void onDitheringSuccess(Ditherer ditherer) {
		w.lastDitherer = ditherer;
		w.resultPreview.setImage(ditherer.createPreview());
		w.resultPreview.setMapCount((int) w.widthSpinner.getValue(), (int) w.heightSpinner.getValue());

		// Дизеренное изображение имеет размер mapW*128 × mapH*128 — точно пропорции сетки.
		// Растягиваем его на всю сетку без полей (stretch, не fit).
		SwingUtilities.invokeLater(() -> w.resultPreview.resetDisplayOffsetStretch());

		setConvertingState(false);
		w.progressBar.setString(Lang.t("progress.dither_done", ditherer.getDitherTime()));
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		w.exportButton.setEnabled(true);
		w.blockListButton.setEnabled(true);
		log(Lang.t("log.dither_done", ditherer.getDitherTime()));
	}

	void onConversionError(String message) {
		setConvertingState(false);
		w.progressBar.setString(Lang.t("progress.dither_error"));
		w.progressBar.setForeground(GuiApp.theme.getError());
		log(Lang.t("log.error", message));
		showError(Lang.t("error.dither", message));
	}

	void onConversionCancelled() {
		setConvertingState(false);
		w.progressBar.setValue(0);
		w.progressBar.setString(Lang.t("progress.cancelled"));
		w.progressBar.setForeground(GuiApp.theme.getWarn());
		log(Lang.t("log.cancelled"));
	}

	void onExportSuccess() {
		setExportingState(false);
		w.progressBar.setString(Lang.t("progress.export_done"));
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		log(Lang.t("log.export_done", w.outPathField.getText()));
	}

	void onExportError(String message) {
		setExportingState(false);
		w.progressBar.setString(Lang.t("progress.export_error"));
		w.progressBar.setForeground(GuiApp.theme.getError());
		log(Lang.t("log.export_error", message));
		showError(Lang.t("error.export", message));
	}

	void onImportSuccess(ImportWorker.Result result, BufferedImage preview, boolean addToBlocks) {
		setImportingState(false);

		w.lastImportResult = result.importResult();

		w.sourcePreview.setImage(preview);
		w.resetSourceViewOnNextImage = true;
		w.sourcePreview.resetDisplayOffset();

		SchematicImportResult importResult = w.lastImportResult;
		w.widthSpinner.setValue(Math.clamp(importResult.mapWidth(), MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.heightSpinner.setValue(Math.clamp(importResult.mapHeight(), MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));

		saveImportPreviewAsTemp(preview);

		if (addToBlocks) {
			w.enabledBlocks.addAll(importResult.blockIds());
			syncBlocksFromFieldIfNeeded();
		}

		w.progressBar.setString(Lang.t("progress.import_done"));
		w.progressBar.setForeground(GuiApp.theme.getSuccess());
		log(Lang.t(
			"log.import_done",
			importResult.mapWidth(),
			importResult.mapHeight(),
			importResult.blockIds().size()
		));
	}

	void saveImportPreviewAsTemp(BufferedImage preview) {
		try {
			File tempFile = Files.createTempFile("mapart_import_", ".png").toFile();
			tempFile.deleteOnExit();
			ImageIO.write(preview, "png", tempFile);

			w.selectedImageFile = tempFile;
			w.rawSourceImage = preview;
			w.imagePathField.setText(tempFile.getAbsolutePath());
		} catch (IOException e) {
			log(Lang.t("log.error", e.getMessage()));
		}
	}

	void onImportError(String message) {
		setImportingState(false);
		w.progressBar.setString(Lang.t("progress.import_error"));
		w.progressBar.setForeground(GuiApp.theme.getError());
		log(Lang.t("log.error", message));
		showError(Lang.t("error.import_failed", message));
	}

	// ── Управление состоянием UI ───────────────────────────────────────────────

	void setImportingState(boolean importing) {
		w.importButton.setEnabled(!importing);
		w.convertButton.setEnabled(!importing);
		w.progressBar.setIndeterminate(importing);

		if (importing) {
			w.progressBar.setString(Lang.t("progress.importing"));
			w.progressBar.setForeground(GuiApp.theme.getImportingProgressFg());
		}
	}

	void setConvertingState(boolean converting) {
		w.progressBar.setIndeterminate(false);

		if (converting) {
			w.progressBar.setValue(0);
			w.progressBar.setString(Lang.t("progress.dithering") + " 0%");
			w.progressBar.setForeground(GuiApp.theme.getAccent());
			w.exportButton.setEnabled(false);
			w.blockListButton.setEnabled(false);
			switchConvertButtonToStop();
		} else {
			switchConvertButtonToConvert();
		}
	}

	private void switchConvertButtonToStop() {
		for (var listener : w.convertButton.getActionListeners()) {
			w.convertButton.removeActionListener(listener);
		}

		w.convertButton.setText(Lang.t("btn.stop"));
		w.convertButton.setIcon(AppIcon.STOP.colored(w.convertButton.getForeground()));
		w.convertButton.setBackground(GuiApp.theme.getError());
		w.convertButton.setEnabled(true);
		w.convertButton.addActionListener(e -> cancelConversion());
	}

	private void switchConvertButtonToConvert() {
		for (var listener : w.convertButton.getActionListeners()) {
			w.convertButton.removeActionListener(listener);
		}

		w.convertButton.setText(Lang.t("btn.convert"));
		w.convertButton.setIcon(AppIcon.PLAY.colored(w.convertButton.getForeground()));
		w.convertButton.setBackground(MainWindow.ACCENT());
		w.convertButton.setEnabled(true);
		w.convertButton.addActionListener(e -> startConversion());
	}

	private void cancelConversion() {
		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}
	}

	void setExportingState(boolean exporting) {
		w.exportButton.setEnabled(!exporting);
		w.convertButton.setEnabled(!exporting);
		w.progressBar.setIndeterminate(exporting);

		if (exporting) {
			w.progressBar.setString(Lang.t("progress.exporting"));
			w.progressBar.setForeground(GuiApp.theme.getWarn());
		}
	}

	void closePreviousDitherer() {
		if (w.lastDitherer == null) {
			return;
		}

		w.lastDitherer.close();
		w.lastDitherer = null;
		w.exportButton.setEnabled(false);
		w.blockListButton.setEnabled(false);
	}

	// ── Список блоков ──────────────────────────────────────────────────────────

	void openBlockList() {
		if (w.lastDitherer == null) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		String primarySupportId = (w.supportSettings != null && !w.supportSettings.isEmpty())
			? w.supportSettings.getEntries().getFirst().blockId()
			: null;

		w.activeBlockListDialog = new BlockListDialog(
			w,
			w.lastDitherer.getUsedBlockCounts(),
			version,
			primarySupportId,
			w.lastDitherer.getSupportBlockCount(),
			this::removeBlockAndReconvert
		);

		w.activeBlockListDialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				w.activeBlockListDialog = null;
			}
		});
	}

	/**
	 * Удаляет блок из активного набора, сохраняет обновлённый файл блоков
	 * и запускает реконвертацию. По завершении обновляет открытый диалог.
	 *
	 * @param blockId идентификатор блока для удаления (например "minecraft:stone")
	 */
	void removeBlockAndReconvert(String blockId) {
		// Удаляем все uniqueKey, соответствующие данному getId():
		// точное совпадение (блок без свойств) или начинающиеся с "blockId_" (варианты с axis/facing/etc.)
		w.enabledBlocks.removeIf(key -> key.equals(blockId) || key.startsWith(blockId + "_"));
		w.blocksCountLabel.setText(Lang.t("label.blocks_count", w.enabledBlocks.size()));

		File blocksFile = resolveBlocksTargetFile();
		saveBlocksToFile(blocksFile);
		w.blocksPathField.setText(blocksFile.getAbsolutePath());

		log(Lang.t("log.block_removed", blockId));
		startConversionForBlockList();
	}

	void saveBlocksToFile(File file) {
		try {
			String version = (String) w.versionCombo.getSelectedItem();
			Map<String, BlockData> paletteBlocks = w.prefs.parsePaletteBlocks(version);
			String content = buildBlocksFileContent(paletteBlocks);
			Files.writeString(file.toPath(), content);
		} catch (IOException e) {
			showError(Lang.t("error.blocks_save_failed", e.getMessage()));
		}
	}

	/**
	 * Строит содержимое файла блоков: каждая строка — uniqueKey блока,
	 * а если для него задан нестандартный вес — добавляется комментарий с процентом.
	 * Формат: {@code minecraft:stone # 80%} или {@code minecraft:stone_axis_y # 25%}
	 */
	String buildBlocksFileContent(Map<String, BlockData> paletteBlocks) {
		StringBuilder sb = new StringBuilder();

		for (String uniqueKey : w.enabledBlocks) {
			sb.append(uniqueKey);

			BlockData block = paletteBlocks.get(uniqueKey);

			if (block != null) {
				String baseId = block.getId();
				WeightedSelector<BlockData> selector = w.blockSelectors.get(baseId);

				if (selector != null) {
					int percent = computeBlockPercent(block, selector);
					sb.append(" # ").append(percent).append('%');
				}
			}

			sb.append('\n');
		}

		return sb.isEmpty() ? "" : sb.substring(0, sb.length() - 1);
	}

	int computeBlockPercent(BlockData block, WeightedSelector<BlockData> selector) {
		int totalWeight = selector.getEntries().stream().mapToInt(WeightedSelector.Entry::weight).sum();

		if (totalWeight == 0) {
			return 0;
		}

		return selector.getEntries().stream()
			.filter(e -> e.value().getUniqueKey().equals(block.getUniqueKey()))
			.mapToInt(WeightedSelector.Entry::weight)
			.map(weight -> (int) Math.round(weight * 100.0 / totalWeight))
			.findFirst()
			.orElse(0);
	}

	void startConversionForBlockList() {
		if (w.activeConversionWorker != null && !w.activeConversionWorker.isDone()) {
			w.activeConversionWorker.cancel(true);
		}

		File imageFile = resolveImageFile();
		File blocksFile = resolveBlocksFile();

		if (imageFile == null || blocksFile == null) {
			return;
		}

		String version = (String) w.versionCombo.getSelectedItem();
		int mapWidth = (int) w.widthSpinner.getValue();
		int mapHeight = (int) w.heightSpinner.getValue();
		Ditherer.Algorithm algorithm = resolveAlgorithm();

		String paletteJson;

		try {
			paletteJson = loadPaletteJson(version);
		} catch (Exception e) {
			onConversionError(e.getMessage());
			return;
		}

		closePreviousDitherer();
		setConvertingState(true);
		log(Lang.t("log.dither_start", imageFile.getName(), mapWidth, mapHeight));

		StaircaseMode staircaseMode = w.staircaseModeCombo.getSelectedItem() instanceof StaircaseMode mode
			? mode
			: StaircaseMode.STAIRCASE;

		w.activeConversionWorker = new ConversionWorker(
			paletteJson,
			imageFile,
			blocksFile,
			mapWidth,
			mapHeight,
			algorithm,
			buildAdjustments(),
			buildDitherSettingsFromUi(),
			buildCropSettingsFromUi(),
			w.blockSelectors,
			staircaseMode,
			this::log,
			this::onDitheringSuccessFromBlockList,
			this::onConversionError,
			this::onConversionCancelled
		);

		w.activeConversionWorker.addPropertyChangeListener(event -> {
			if (!"progress".equals(event.getPropertyName())) {
				return;
			}

			int percent = (int) event.getNewValue();
			w.progressBar.setValue(percent);
			w.progressBar.setString(Lang.t("progress.dithering") + " " + percent + "%");
		});

		w.activeConversionWorker.execute();
	}

	void onDitheringSuccessFromBlockList(Ditherer ditherer) {
		onDitheringSuccess(ditherer);

		if (w.activeBlockListDialog != null && w.activeBlockListDialog.isVisible()) {
			w.activeBlockListDialog.refresh(ditherer.getUsedBlockCounts(), ditherer.getSupportBlockCount());
		}
	}

	// ── Файловые операции ──────────────────────────────────────────────────────

	void syncSourcePreviewMapCount() {
		if (w.sourcePreview == null || w.widthSpinner == null || w.heightSpinner == null) {
			return;
		}

		w.sourcePreview.setMapCount(
			(int) w.widthSpinner.getValue(),
			(int) w.heightSpinner.getValue()
		);
	}

	void scaleMapCount(double factor) {
		int w2 = (int) Math.round((int) w.widthSpinner.getValue() * factor);
		int h2 = (int) Math.round((int) w.heightSpinner.getValue() * factor);
		w.widthSpinner.setValue(Math.clamp(w2, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.heightSpinner.setValue(Math.clamp(h2, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
	}

	void autoFitMapCount() {
		if (w.rawSourceImage == null) {
			return;
		}

		int maps = (int) Math.ceil(w.rawSourceImage.getWidth() / 128.0);
		int mapsH = (int) Math.ceil(w.rawSourceImage.getHeight() / 128.0);

		w.widthSpinner.setValue(Math.clamp(maps, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
		w.heightSpinner.setValue(Math.clamp(mapsH, MainWindow.SPINNER_MIN, MainWindow.SPINNER_MAX));
	}

	CropSettings buildCropSettingsFromUi() {
		int mapWidth = (int) w.widthSpinner.getValue();
		int mapHeight = (int) w.heightSpinner.getValue();
		int targetW = mapWidth * 128;
		int targetH = mapHeight * 128;
		return w.sourcePreview.buildCropSettings(targetW, targetH);
	}

	private Ditherer.Algorithm resolveAlgorithm() {
		Object selected = w.algorithmCombo.getSelectedItem();
		return selected instanceof Ditherer.Algorithm algorithm
			? algorithm
			: Ditherer.Algorithm.NONE;
	}

	DitherSettings buildDitherSettingsFromUi() {
		DitherSettings defaults = DitherSettings.defaults();
		double errRateR = w.errRateRSlider != null
			? w.errRateRSlider.getValue() / 100.0
			: defaults.errRateR();
		double errRateG = w.errRateGSlider != null
			? w.errRateGSlider.getValue() / 100.0
			: defaults.errRateG();
		double errRateB = w.errRateBSlider != null
			? w.errRateBSlider.getValue() / 100.0
			: defaults.errRateB();
		double noiseLevel = w.noiseLevelSlider != null
			? w.noiseLevelSlider.getValue() / 100.0
			: defaults.noiseLevel();
		ColorMetric metric = w.colorMetricCombo != null
			? (ColorMetric) w.colorMetricCombo.getSelectedItem()
			: defaults.colorMetric();
		return new DitherSettings(errRateR, errRateG, errRateB, noiseLevel, metric);
	}

	ImageAdjustments buildAdjustments() {
		return new ImageAdjustments(
			w.brightnessSlider.getValue(),
			w.contrastSlider.getValue(),
			w.saturationSlider.getValue(),
			w.gammaSlider.getValue(),
			w.hueSlider.getValue()
		);
	}

	void tryAutoLoadBlocks() {
		if (!w.enabledBlocks.isEmpty()) {
			return;
		}

		File defaultBlocks = new File("./blocks.txt");

		if (defaultBlocks.exists() && defaultBlocks.isFile()) {
			loadBlocksFromFile(defaultBlocks);
		}
	}

	void loadBlocksFromFile(File file) {
		try {
			String content = Files.readString(file.toPath());
			w.enabledBlocks = new HashSet<>();

			for (String line : content.split("\n")) {
				String trimmed = line.strip();

				if (trimmed.isBlank() || trimmed.startsWith("#")) {
					continue;
				}

				int commentIdx = trimmed.indexOf('#');
				String uniqueKey = commentIdx >= 0
					? trimmed.substring(0, commentIdx).strip()
					: trimmed;

				if (!uniqueKey.isBlank()) {
					w.enabledBlocks.add(uniqueKey);
				}
			}

			w.blocksPathField.setText(file.getAbsolutePath());
			w.blocksCountLabel.setText(Lang.t("label.blocks_count", w.enabledBlocks.size()));
			w.blocksCountLabel.setForeground(GuiApp.theme.getSuccess());
			log(Lang.t("log.blocks_loaded", file.getName(), w.enabledBlocks.size()));
		} catch (IOException e) {
			showError(Lang.t("error.blocks_load_failed", e.getMessage()));
		}
	}

	void openBlockPicker() {
		syncBlocksFromFieldIfNeeded();

		String version = (String) w.versionCombo.getSelectedItem();
		File targetFile = resolveBlocksTargetFile();

		BlockPickerDialog dialog = new BlockPickerDialog(
			w,
			version,
			targetFile,
			w.enabledBlocks,
			w.supportSettings,
			w.blockSelectors
		);

		if (dialog.isConfirmed()) {
			w.enabledBlocks = new HashSet<>(dialog.getEnabledBlocks());
			w.blockSelectors = new HashMap<>(dialog.getBlockSelectors());
			w.supportSettings = dialog.getSupportSettings();
			w.prefs.savePreferences();

			w.blocksPathField.setText(targetFile.getAbsolutePath());
			w.blocksCountLabel.setText(Lang.t("label.blocks_count", w.enabledBlocks.size()));
			w.blocksCountLabel.setForeground(GuiApp.theme.getSuccess());
			log(Lang.t("log.blocks_picked", w.enabledBlocks.size(), targetFile.getName()));
		}
	}

	void syncBlocksFromFieldIfNeeded() {
		String path = w.blocksPathField.getText().strip();

		if (path.isBlank() || !w.enabledBlocks.isEmpty()) {
			return;
		}

		File file = new File(path);

		if (file.exists() && file.isFile()) {
			loadBlocksFromFile(file);
		}
	}

	File resolveBlocksTargetFile() {
		String path = w.blocksPathField.getText().strip();
		return path.isBlank() ? new File("./blocks.txt") : new File(path);
	}

	void chooseImage() {
		FileDialog dialog = new FileDialog(w, Lang.t("dialog.choose_image"), FileDialog.LOAD);
		dialog.setFilenameFilter((dir, name) -> {
			String lower = name.toLowerCase();
			return lower.endsWith(".png")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".bmp")
				|| lower.endsWith(".gif");
		});
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		loadImageFile(new File(dialog.getDirectory(), dialog.getFile()));
	}

	void chooseBlocks() {
		FileDialog dialog = new FileDialog(w, Lang.t("dialog.choose_blocks"), FileDialog.LOAD);
		dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
		dialog.setVisible(true);

		if (dialog.getFile() == null) {
			return;
		}

		loadBlocksFromFile(new File(dialog.getDirectory(), dialog.getFile()));
	}

	void chooseOutDir() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(Lang.t("dialog.choose_outdir"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		if (chooser.showOpenDialog(w) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		w.outPathField.setText(chooser.getSelectedFile().getAbsolutePath());
	}

	void loadImageFile(File file) {
		try {
			BufferedImage image = ImageIO.read(file);

			if (image == null) {
				showError(Lang.t("error.image_load_failed", file.getName()));
				return;
			}

			w.selectedImageFile = file;
			w.rawSourceImage = image;
			w.imagePathField.setText(file.getAbsolutePath());

			w.resetSourceViewOnNextImage = true;

			if (w.resultPreview != null) {
				w.resultPreview.clear();
			}

			scheduleSourcePreview();
			log(Lang.t("log.image_loaded", file.getName(), image.getWidth(), image.getHeight()));
		} catch (IOException e) {
			showError(Lang.t("error.image_load_failed", e.getMessage()));
		}
	}

	File resolveImageFile() {
		if (w.selectedImageFile != null && w.selectedImageFile.exists()) {
			return w.selectedImageFile;
		}

		String path = w.imagePathField.getText().strip();

		if (path.isBlank()) {
			showError(Lang.t("error.no_image"));
			return null;
		}

		File file = new File(path);

		if (!file.exists() || !file.isFile()) {
			showError(Lang.t("error.image_not_found", path));
			return null;
		}

		w.selectedImageFile = file;
		return file;
	}

	File resolveBlocksFile() {
		String path = w.blocksPathField.getText().strip();

		if (!path.isBlank()) {
			File file = new File(path);

			if (file.exists() && file.isFile()) {
				return file;
			}

			showError(Lang.t("error.blocks_not_found", path));
			return null;
		}

		File defaultFile = new File("./blocks.txt");

		if (defaultFile.exists()) {
			return defaultFile;
		}

		showError(Lang.t("error.blocks_not_found", "./blocks.txt"));
		return null;
	}

	File resolveOutDir() {
		String path = w.outPathField.getText().strip();
		File dir = path.isBlank() ? new File("./rendered") : new File(path);

		if (dir.exists()) {
			return dir;
		}

		if (!dir.mkdirs()) {
			showError(Lang.t("error.outdir_failed", dir.getAbsolutePath()));
			return null;
		}

		return dir;
	}

	void setupImageDropTarget(ImagePreviewPanel panel) {
		new DropTarget(
			panel, DnDConstants.ACTION_COPY, new java.awt.dnd.DropTargetAdapter() {
				@Override
				public void drop(DropTargetDropEvent event) {
					try {
						event.acceptDrop(DnDConstants.ACTION_COPY);
						List<?> files = (List<?>) event.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);

						if (!files.isEmpty()) {
							loadImageFile((File) files.get(0));
						}
					} catch (Exception e) {
						showError(Lang.t("error.drop_failed", e.getMessage()));
					}
				}
			}
		);
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
			loadImageFile(tempFile);
			log(Lang.t("log.clipboard_paste", image.getWidth(), image.getHeight()));
		} catch (Exception e) {
			showError(Lang.t("error.image_load_failed", e.getMessage()));
		}
	}

	void syncExportButtonLabel() {
		if (w.exportButton == null || w.formatCombo == null) {
			return;
		}

		SchematicFormat selected = (SchematicFormat) w.formatCombo.getSelectedItem();
		String key = selected == SchematicFormat.LITEMATIC ? "btn.export_litematic" : "btn.export_nbt";
		w.exportButton.setText("📤  " + Lang.t(key));
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

	// ── Утилиты ────────────────────────────────────────────────────────────────

	void log(String message) {
		w.logArea.appendLine(message);
	}

	void showError(String message) {
		JOptionPane.showMessageDialog(w, message, Lang.t("dialog.error_title"), JOptionPane.ERROR_MESSAGE);
	}

	void openSettings() {
		SettingsDialog dialog = new SettingsDialog(w);

		if (!dialog.isConfirmed()) {
			return;
		}

		Lang.load(AppPreferences.loadLocale("ru_ru"));
		GuiApp.applyTheme(AppPreferences.loadTheme("dark"));
		w.setTitle(Lang.t("app.title"));
		ThemeTransition.apply(w, w::rebuildUi);
	}

	void addHoverEffect(JButton btn) {
		btn.putClientProperty("hoverProgress", 0f);

		btn.addMouseListener(new MouseAdapter() {
			private Timer activeTimer;

			@Override
			public void mouseEntered(MouseEvent e) {
				stopPrevious();
				float from = hoverProgress(btn);
				activeTimer = UiAnimator.animateFloat(from, 1f, 150, progress -> {
					btn.putClientProperty("hoverProgress", progress);
					btn.repaint();
				}, null);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				stopPrevious();
				float from = hoverProgress(btn);
				activeTimer = UiAnimator.animateFloat(from, 0f, 150, progress -> {
					btn.putClientProperty("hoverProgress", progress);
					btn.repaint();
				}, null);
			}

			private void stopPrevious() {
				if (activeTimer != null) {
					activeTimer.stop();
				}
			}
		});
	}

	private static float hoverProgress(JButton btn) {
		Object value = btn.getClientProperty("hoverProgress");
		return value instanceof Float f ? f : 0f;
	}
}