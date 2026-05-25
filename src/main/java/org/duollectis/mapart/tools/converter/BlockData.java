package org.duollectis.mapart.tools.converter;

import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
public class BlockData {

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
	private final Map<String, String> properties = null;

	public Map<String, String> getProperties() {
		return properties == null ? Collections.emptyMap() : properties;
	}
}
