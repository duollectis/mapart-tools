package org.duollectis.mapart.tools.gui.widget;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

/**
 * Аккордеон-виджет: заголовок-кнопка раскрывает произвольную {@link JPanel}
 * с анимацией прямо в потоке layout — соседние элементы сдвигаются вниз.
 * Вся логика анимации и рисования унаследована от {@link ExpandableWidget}.
 * <p>
 * Автоматически отслеживает все {@link ExpandableWidget} в иерархии contentPanel
 * и растягивается вместе с ними при раскрытии.
 */
public class AccordionPanel extends ExpandableWidget {

	private static final int CONTENT_PADDING = 10;

	private final String title;
	private final JPanel contentPanel;

	/**
	 * @param title        текст заголовка кнопки
	 * @param contentPanel произвольная панель, которая раскрывается внутри
	 */
	public AccordionPanel(String title, JPanel contentPanel) {
		this.title = title;
		this.contentPanel = contentPanel;

		contentPanel.setBorder(BorderFactory.createEmptyBorder(8, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));
		contentPanel.setVisible(false);
		add(contentPanel);

		installRecursiveWatcher(contentPanel);
	}

	@Override
	protected String getHeaderLabel() {
		return title;
	}

	@Override
	protected Component getExpandedContent() {
		return contentPanel;
	}

	@Override
	protected int computeMaxContentHeight() {
		contentPanel.validate();
		return contentPanel.getPreferredSize().height + 18;
	}

	@Override
	protected void onHeaderClick() {
		toggle();
	}

	/**
	 * Рекурсивно обходит иерархию контейнера и подписывается на preferredSize
	 * всех уже добавленных и будущих {@link ExpandableWidget}.
	 */
	private void installRecursiveWatcher(Container container) {
		for (Component child : container.getComponents()) {
			watchIfExpandable(child);

			if (child instanceof Container nested) {
				installRecursiveWatcher(nested);
			}
		}

		container.addContainerListener(new ContainerAdapter() {
			@Override
			public void componentAdded(ContainerEvent e) {
				Component added = e.getChild();
				watchIfExpandable(added);

				if (added instanceof Container nested) {
					installRecursiveWatcher(nested);
				}
			}
		});
	}

	private void watchIfExpandable(Component component) {
		if (component instanceof ExpandableWidget expandable) {
			expandable.addPropertyChangeListener("preferredSize", evt ->
				SwingUtilities.invokeLater(this::onContentResized)
			);
		}
	}
}
