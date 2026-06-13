package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Brightness {
	LOW(180, 1),
	NORMAL(220, 0),
	HIGH(255, -1);

	private final int modifier;
	private final int levelDelta;
}
