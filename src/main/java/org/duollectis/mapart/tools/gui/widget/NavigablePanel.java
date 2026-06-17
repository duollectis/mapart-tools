package org.duollectis.mapart.tools.gui.widget;

/**
 * Маркерный интерфейс для панелей, управляемых {@link PanelNavigator}.
 * Реализующие классы предоставляют заголовок для общей навигационной шапки.
 */
public interface NavigablePanel {

	/** Возвращает заголовок, отображаемый в навигационной шапке. */
	String getNavTitle();
}
