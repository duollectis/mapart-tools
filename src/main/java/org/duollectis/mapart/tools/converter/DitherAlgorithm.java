package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.HasDescription;

@Getter
@RequiredArgsConstructor
public enum DitherAlgorithm implements HasDescription {
	// Без дизеринга
	NONE(12, AlgorithmType.NONE, "algorithm.group.none", false, false),

	// Диффузия ошибки
	FLOYD_STEINBERG(0, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	FLOYD_STEINBERG_20(21, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	FLOYD_STEINBERG_24(22, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	STUCKI(1, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	JJN(2, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	BURKES(3, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	SIERRA3(4, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	SIERRA2(7, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	SIERRA_LITE(5, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	ATKINSON(6, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),
	FILTER_LITE(8, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.error_diffusion", true, true),

	// Однострочные фильтры диффузии
	FAN(23, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),
	SHIAU_FAN(24, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),
	SHIAU_FAN_2(25, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),
	PIGEON(26, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),
	NAKANO(27, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),
	ZHOU_FANG(28, AlgorithmType.ERROR_DIFFUSION, "algorithm.group.row_diffusion", true, true),

	// Байер
	BAYER_2X2(9, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),
	BAYER_3X3(17, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),
	BAYER_4X4(10, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),
	BAYER_8X8(11, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),
	BAYER_16X16(13, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),
	BAYER_32X32(31, AlgorithmType.ORDERED, "algorithm.group.bayer", true, false),

	// Упорядоченный дизеринг
	ORDERED_3X3(18, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	CLUSTERED_DOT(14, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	CLUSTERED_DOT_4X4(19, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	HALFTONE(15, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	VOID_AND_CLUSTER(16, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	VOID_AND_CLUSTER_14X14(20, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	DISPERSED_DOT_4X4(29, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	DISPERSED_DOT_8X8(30, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	MAGIC_SQUARE_5X5(32, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false),
	BLUE_NOISE_16X16(33, AlgorithmType.ORDERED, "algorithm.group.ordered", true, false);

	private final int id;
	private final AlgorithmType type;
	private final String groupKey;
	/** Показывать ли слайдер уровня шума в панели настроек дизеринга. */
	private final boolean showsNoise;
	/** Показывать ли слайдер скорости ошибки (errRate) в панели настроек дизеринга. */
	private final boolean showsErrRate;

	@Override
	public String toString() {
		try {
			return UpdatableRegistry.translate("algorithm." + name());
		} catch (Exception ignored) {
			return name();
		}
	}

	@Override
	public String getDescription() {
		try {
			return UpdatableRegistry.translate("algorithm." + name() + ".desc");
		} catch (Exception ignored) {
			return null;
		}
	}
}
