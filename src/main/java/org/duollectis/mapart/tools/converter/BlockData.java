package org.duollectis.mapart.tools.converter;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class BlockData {

	private final String id;
	private final Map<String, String> properties = new HashMap<>();
}
