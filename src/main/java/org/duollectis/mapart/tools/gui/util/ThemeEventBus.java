package org.duollectis.mapart.tools.gui.util;

import lombok.experimental.UtilityClass;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Шина событий смены темы.
 *
 * <p>Компоненты, кеширующие цвета через {@code setBackground()} / {@code setForeground()},
 * регистрируют лямбду обновления через {@link #register(Runnable)}.
 * При смене темы вызывается {@link #fire(Component)} — все обработчики получают
 * уведомление, после чего всё дерево компонентов принудительно перерисовывается.
 *
 * <p>При пересборке UI (смена языка) необходимо вызвать {@link #clear()},
 * чтобы удалить обработчики старых компонентов перед регистрацией новых.
 */
@UtilityClass
public class ThemeEventBus {

	private static final List<Runnable> listeners = new ArrayList<>();

	/**
	 * Регистрирует обработчик обновления цветов компонента.
	 * Вызывается один раз при создании компонента.
	 *
	 * @param onThemeChanged лямбда, обновляющая цвета компонента из {@code GuiApp.theme}
	 */
	public static void register(Runnable onThemeChanged) {
		listeners.add(onThemeChanged);
	}

	/**
	 * Уведомляет всех подписчиков о смене темы, затем рекурсивно
	 * вызывает {@code repaint()} на всём дереве компонентов начиная с {@code root}.
	 *
	 * @param root корневой компонент для обхода (обычно главное окно)
	 */
	public static void fire(Component root) {
		for (Runnable listener : listeners) {
			listener.run();
		}

		repaintTree(root);
	}

	/**
	 * Очищает список подписчиков.
	 * Вызывается перед пересборкой UI, чтобы старые компоненты не накапливались.
	 */
	public static void clear() {
		listeners.clear();
	}

	private static void repaintTree(Component component) {
		component.repaint();

		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				repaintTree(child);
			}
		}
	}
}
