package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.converter.BlockData;
import org.duollectis.mapart.tools.converter.WeightedSelector;
import org.duollectis.mapart.tools.gui.AppMessages;
import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.dialog.BlockListDialog;
import org.duollectis.mapart.tools.gui.dialog.BlockPickerDialog;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Отвечает за управление набором блоков: загрузку, сохранение, выбор через диалог,
 * открытие списка использованных блоков и реконвертацию после изменений.
 */
final class BlockActions {

	private final MainWindow w;

	BlockActions(MainWindow window) {
		w = window;
	}

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
		w.blocksCountLabel.setText(UpdatableRegistry.translate("label.blocks_count", w.enabledBlocks.size()));

		File blocksFile = resolveBlocksTargetFile();
		saveBlocksToFile(blocksFile);
		w.blocksPathField.setText(blocksFile.getAbsolutePath());

		w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_BLOCK_REMOVED, blockId));
		w.actions.startConversionForBlockList();
	}

	void saveBlocksToFile(File file) {
		try {
			String version = (String) w.versionCombo.getSelectedItem();
			Map<String, BlockData> paletteBlocks = w.prefs.parsePaletteBlocks(version);
			String content = buildBlocksFileContent(paletteBlocks);
			Files.writeString(file.toPath(), content);
		} catch (IOException e) {
			w.actions.showError(UpdatableRegistry.translate("error.blocks_save_failed", e.getMessage()));
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
			w.blocksCountLabel.setText(UpdatableRegistry.translate("label.blocks_count", w.enabledBlocks.size()));
			w.blocksCountLabel.setForeground(GuiApp.theme.getSuccess());
			w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_BLOCKS_LOADED, file.getName(), w.enabledBlocks.size()));
		} catch (IOException e) {
			w.actions.showError(UpdatableRegistry.translate("error.blocks_load_failed", e.getMessage()));
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
			w.blocksCountLabel.setText(UpdatableRegistry.translate("label.blocks_count", w.enabledBlocks.size()));
			w.blocksCountLabel.setForeground(GuiApp.theme.getSuccess());
			w.actions.log(UpdatableRegistry.translate(AppMessages.LOG_BLOCKS_PICKED, w.enabledBlocks.size(), targetFile.getName()));
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
}
