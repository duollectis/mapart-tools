package org.duollectis.mapart.tools.converter;

import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
public class BlockData {

	private static final String SLAB_SUFFIX = "_slab";
	private static final String SLAB_TYPE_KEY = "type";
	private static final String SLAB_TYPE_TOP = "top";

	private final String id;

	@SerializedName("need_support")
	private boolean needSupport;

	/** Имя файла иконки без расширения (например "minecraft_oak_log_axis_y"). Может быть null. */
	private String icon;

	/**
	 * Gson при десериализации может оставить поле null если в JSON нет "properties".
	 * Геттер написан вручную — возвращает emptyMap() вместо null.
	 */
	@Getter(AccessLevel.NONE)
	private Map<String, String> properties;

	/** Конструктор для создания блока только по ID (без properties). */
	public BlockData(String id) {
		this.id = id;
	}

	/** Конструктор для создания блока с явными properties (например, для слябов с type=top). */
	public BlockData(String id, Map<String, String> properties) {
		this.id = id;
		this.properties = Map.copyOf(properties);
	}

	public Map<String, String> getProperties() {
		return properties == null ? Collections.emptyMap() : properties;
	}

	public boolean isSlab() {
		return id.endsWith(SLAB_SUFFIX);
	}

	/**
	 * Возвращает новый экземпляр этого блока с добавленным/переопределённым property.
	 * Используется для создания варианта сляба с {@code type=top} при размещении в качестве опоры.
	 */
	public BlockData withProperty(String key, String value) {
		Map<String, String> merged = new HashMap<>(getProperties());
		merged.put(key, value);
		return new BlockData(id, merged);
	}

	public BlockData asTopSlab() {
		return withProperty(SLAB_TYPE_KEY, SLAB_TYPE_TOP);
	}

	/**
	 * Уникальный ключ блока с учётом block states.
	 * Используется в UI для различения вариантов одного блока (например, axis=x vs axis=y).
	 * В blocks.txt сохраняется только {@link #getId()} — без properties.
	 */
	public String getUniqueKey() {
		Map<String, String> props = getProperties();

		if (props.isEmpty()) {
			return id;
		}

		String suffix = props.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> e.getKey() + "_" + e.getValue())
			.collect(Collectors.joining("_"));

		return id + "_" + suffix;
	}
}
