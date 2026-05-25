package org.duollectis.mapart.tools.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Brightness {
	LOW(180),
	NORMAL(220),
	HIGH(255);

	private final int modifier;
}
