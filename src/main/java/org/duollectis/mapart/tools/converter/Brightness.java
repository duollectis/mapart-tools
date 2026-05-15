package org.duollectis.mapart.tools.converter;

import lombok.Getter;

@Getter
public enum Brightness {
	LOW(180),
	NORMAL(220),
	HIGH(255);

	private final int modifier;

	Brightness(final int k) {
		this.modifier = k;
	}
}
