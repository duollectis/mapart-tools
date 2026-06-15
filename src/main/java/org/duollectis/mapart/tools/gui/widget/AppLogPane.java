package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

/**
 * Панель лога приложения с поддержкой свёрнутых стектрейсов и изменения высоты
 * путём перетаскивания верхней границы.
 */
public class AppLogPane extends JPanel {

	private static final int MAX_ENTRIES = 500;
	private static final int MIN_HEIGHT = 40;
	private static final int MAX_HEIGHT = 600;
	private static final int HANDLE_H = 5;
	private static final Font LOG_FONT = new Font("Monospaced", Font.PLAIN, 11);
	private static final Font STACK_FONT = new Font("Monospaced", Font.PLAIN, 10);

	private final List<LogEntry> entries = new ArrayList<>();
	private final JPanel contentPanel;
	private final InertialScrollPane scrollPane;

	public AppLogPane() {
		super(new BorderLayout());
		setOpaque(false);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setOpaque(false);
		contentPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		scrollPane = new InertialScrollPane(contentPanel);
		scrollPane.setOpaque(true);
		scrollPane.getViewport().setOpaque(true);
		scrollPane.setBorder(BorderFactory.createLineBorder(GuiApp.theme.getBorder(), 1, true));
		scrollPane.setBackground(GuiApp.theme.getBgCard());
		scrollPane.getViewport().setBackground(GuiApp.theme.getBgCard());
		scrollPane.setPreferredSize(new Dimension(0, 80));
		UpdatableRegistry.onThemeAnimFrame(() -> {
			scrollPane.setBorder(BorderFactory.createLineBorder(GuiApp.theme.getBorder(), 1, true));
			scrollPane.setBackground(GuiApp.theme.getBgCard());
			scrollPane.getViewport().setBackground(GuiApp.theme.getBgCard());
		});

		add(buildResizeHandle(), BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	/** Добавляет обычную строку лога. */
	public void appendLine(String text) {
		SwingUtilities.invokeLater(() -> {
			trimIfNeeded();
			LogEntry entry = new LogEntry(text, EntryType.PLAIN);
			entries.add(entry);
			contentPanel.add(buildPlainRow(entry));
			scrollToBottom();
		});
	}

	/**
	 * Добавляет запись об ошибке: заголовок исключения + свёрнутый стектрейс.
	 *
	 * @param header     первая строка (тип исключения + сообщение)
	 * @param stackLines строки стектрейса (начинаются с {@code \tat ...})
	 */
	public void appendException(String header, List<String> stackLines) {
		SwingUtilities.invokeLater(() -> {
			trimIfNeeded();
			LogEntry entry = new LogEntry(header, EntryType.EXCEPTION);
			entry.stackLines = new ArrayList<>(stackLines);
			entries.add(entry);
			contentPanel.add(buildExceptionRow(entry));
			scrollToBottom();
		});
	}

	/** Очищает весь лог. */
	public void clear() {
		SwingUtilities.invokeLater(() -> {
			entries.clear();
			contentPanel.removeAll();
			contentPanel.revalidate();
			contentPanel.repaint();
		});
	}

	/**
	 * Строит невидимую полосу-ручку над scrollPane.
	 * При перетаскивании вверх/вниз меняет preferredSize у scrollPane,
	 * что растягивает консоль вверх за счёт BorderLayout.SOUTH в родителе.
	 */
	private JPanel buildResizeHandle() {
		JPanel handle = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				int cx = getWidth() / 2;
				int cy = getHeight() / 2;
				g.setColor(GuiApp.theme.getBorder());
				g.fillRoundRect(cx - 20, cy - 1, 40, 2, 2, 2);
			}
		};
		handle.setOpaque(false);
		handle.setPreferredSize(new Dimension(0, HANDLE_H));
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
		UpdatableRegistry.onThemeAnimFrame(handle::repaint);

		int[] dragStartY = {0};
		int[] dragStartH = {0};

		handle.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dragStartY[0] = e.getYOnScreen();
				dragStartH[0] = scrollPane.getPreferredSize().height;
			}
		});

		handle.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				int delta = dragStartY[0] - e.getYOnScreen();
				int newHeight = Math.clamp(dragStartH[0] + delta, MIN_HEIGHT, MAX_HEIGHT);
				scrollPane.setPreferredSize(new Dimension(0, newHeight));
				revalidate();
			}
		});

		return handle;
	}

	private void trimIfNeeded() {
		while (entries.size() >= MAX_ENTRIES) {
			entries.remove(0);
			contentPanel.remove(0);
		}
	}

	private void scrollToBottom() {
		contentPanel.revalidate();
		contentPanel.repaint();
		SwingUtilities.invokeLater(() -> {
			JScrollBar bar = scrollPane.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
	}

	private JPanel buildPlainRow(LogEntry entry) {
		JLabel label = new JLabel(entry.text);
		label.setFont(LOG_FONT);
		label.setForeground(GuiApp.theme.getTextDim());
		label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);

		return row;
	}

	private JPanel buildExceptionRow(LogEntry entry) {
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);

		JPanel headerRow = buildExceptionHeader(entry, wrapper);
		wrapper.add(headerRow);

		JPanel stackPanel = buildStackPanel(entry.stackLines);
		stackPanel.setVisible(false);
		entry.stackPanel = stackPanel;
		wrapper.add(stackPanel);

		return wrapper;
	}

	private JPanel buildExceptionHeader(LogEntry entry, JPanel wrapper) {
		JLabel arrow = buildExceptionArrow(false);

		JLabel text = new JLabel(entry.text);
		text.setFont(LOG_FONT);
		text.setForeground(GuiApp.theme.getError());

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
		row.setOpaque(false);
		UiAnimator.applyHandCursor(row);
		row.add(arrow);
		row.add(text);

		row.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				boolean nowVisible = !entry.stackPanel.isVisible();
				entry.stackPanel.setVisible(nowVisible);
				arrow.putClientProperty("expanded", nowVisible);
				arrow.repaint();
				wrapper.revalidate();
				wrapper.repaint();
				scrollPane.revalidate();
				scrollPane.repaint();
			}
		});

		return row;
	}

	/**
	 * Рисует маленький треугольник-стрелку для заголовка исключения.
	 * Повёрнут вправо (свёрнуто) или вниз (развёрнуто) в зависимости от
	 * client property {@code "expanded"}.
	 */
	private JLabel buildExceptionArrow(boolean expanded) {
		JLabel arrow = new JLabel() {
			private static final int SIZE = 7;

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getError());

				int cx = getWidth() / 2;
				int cy = getHeight() / 2;
				boolean isExpanded = Boolean.TRUE.equals(getClientProperty("expanded"));

				if (isExpanded) {
					// треугольник вниз
					int[] xs = {cx - SIZE / 2, cx + SIZE / 2, cx};
					int[] ys = {cy - SIZE / 2, cy - SIZE / 2, cy + SIZE / 2};
					g2.fillPolygon(xs, ys, 3);
				} else {
					// треугольник вправо
					int[] xs = {cx - SIZE / 2, cx + SIZE / 2, cx - SIZE / 2};
					int[] ys = {cy - SIZE / 2, cy, cy + SIZE / 2};
					g2.fillPolygon(xs, ys, 3);
				}

				g2.dispose();
			}

			@Override
			public Dimension getPreferredSize() {
				return new Dimension(14, 14);
			}
		};

		arrow.putClientProperty("expanded", expanded);
		arrow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		UpdatableRegistry.onThemeAnimFrame(arrow::repaint);

		return arrow;
	}

	private JPanel buildStackPanel(List<String> lines) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(0, 14, 2, 0));

		for (String line : lines) {
			JLabel label = new JLabel(line);
			label.setFont(STACK_FONT);
			label.setForeground(GuiApp.theme.getTextDim());

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.add(label, BorderLayout.WEST);
			panel.add(row);
		}

		return panel;
	}

	// ── Внутренние типы ───────────────────────────────────────────────────────

	private enum EntryType {
		PLAIN, EXCEPTION
	}

	private static final class LogEntry {

		final String text;
		final EntryType type;
		List<String> stackLines;
		JPanel stackPanel;

		LogEntry(String text, EntryType type) {
			this.text = text;
			this.type = type;
		}
	}
}
