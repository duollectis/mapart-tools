package org.duollectis.mapart.tools.gui;

/**
 * Контракт для элементов выпадающего списка, которые могут предоставить
 * краткое описание своей функции для отображения в GUI.
 */
public interface HasDescription {

	/**
	 * Возвращает локализованное описание элемента или {@code null},
	 * если описание недоступно.
	 */
	String getDescription();
}
