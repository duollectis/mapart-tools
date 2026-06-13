package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.HasDescription;

import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum StaircaseMode implements HasDescription {
	// Классические режимы — все три яркости
	STAIRCASE("staircase.staircase", null, false, false),
	VALLEY("staircase.valley", null, false, true),

	// Однотонные режимы — одна яркость
	DARK("staircase.dark", Brightness.LOW, false, false),
	LIGHT("staircase.light", Brightness.HIGH, false, false),

	// Плоские режимы
	FLAT("staircase.flat", Brightness.NORMAL, true, false),
	FLAT_DARK("staircase.flat_dark", Brightness.LOW, true, false),
	FLAT_LIGHT("staircase.flat_light", Brightness.HIGH, true, false),

	// Направленные режимы — только вверх или только вниз
	UPWARDS_ONLY("staircase.upwards_only", null, false, false),
	REVERSE_UPWARDS_ONLY("staircase.reverse_upwards_only", null, false, false);

	private final String langKey;
	/** Яркость для фильтрации палитры; null — определяется через {@link #getAllowedBrightnesses()}. */
	private final Brightness paletteBrightness;
	private final boolean flat;
	/** Применять Valley-нормализацию по алгоритму Rebasin. */
	private final boolean normalize;

	/**
	 * Возвращает набор яркостей, доступных в данном режиме.
	 * Используется при построении палитры в {@code ImageConverter.loadPalette()}.
	 */
	public Set<Brightness> getAllowedBrightnesses() {
		if (paletteBrightness != null) {
			return EnumSet.of(paletteBrightness);
		}

		return switch (this) {
			case UPWARDS_ONLY -> EnumSet.of(Brightness.NORMAL, Brightness.HIGH);
			case REVERSE_UPWARDS_ONLY -> EnumSet.of(Brightness.LOW, Brightness.NORMAL);
			default -> EnumSet.allOf(Brightness.class);
		};
	}

	@Override
	public String toString() {
		return UpdatableRegistry.translate(langKey);
	}

	@Override
	public String getDescription() {
		return UpdatableRegistry.translate(langKey + ".desc");
	}
}
