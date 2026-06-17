package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import org.duollectis.mapart.tools.gui.anim.UiAnimator;

/**
 * Панель лога приложения с поддержкой свёрнутых стектрейсов и изменения высоты
 * путём перетаскивания верхней границы.
 * Обычные строки выводятся в единый JTextArea — выделение через несколько строк
 * и копирование Ctrl+C работают нативно на любой раскладке.
 */
public class AppLogPane extends JPanel {

	private static final int MAX_PLAIN_LINES = 500;
	private static final int MIN_HEIGHT = 40;
	private static final int MAX_HEIGHT = 600;
	private static final int HANDLE_H = 5;
	private static final Font LOG_FONT = new Font("Monospaced", Font.PLAIN, 11);
	private static final Font STACK_FONT = new Font("Monospaced", Font.PLAIN, 10);

	private final JTextArea logArea;
	private final JPanel exceptionsPanel;
	private final JPanel contentPanel;
	private final InertialScrollPane scrollPane;
	private int plainLineCount = 0;

	public AppLogPane() {
		super(new BorderLayout());
		setOpaque(false);

		logArea = buildMainLogArea();

		exceptionsPanel = new JPanel();
		exceptionsPanel.setLayout(new BoxLayout(exceptionsPanel, BoxLayout.Y_AXIS));
		exceptionsPanel.setOpaque(false);

		contentPanel = new JPanel(new BorderLayout());
		contentPanel.setOpaque(false);
		contentPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		contentPanel.add(logArea, BorderLayout.NORTH);
		contentPanel.add(exceptionsPanel, BorderLayout.CENTER);

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
			logArea.setForeground(GuiApp.theme.getTextDim());
		});

		add(buildResizeHandle(), BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	/** Добавляет обычную строку лога. */
	public void appendLine(String text) {
		SwingUtilities.invokeLater(() -> {
			trimPlainLinesIfNeeded();
			if (plainLineCount > 0) {
				logArea.append("\n");
			}
			logArea.append(text);
			plainLineCount++;
			scrollToBottom();
		});
	}

	public void appendException(String header, List<String> stackLines) {
		SwingUtilities.invokeLater(() -> {
			exceptionsPanel.add(buildExceptionRow(header, new ArrayList<>(stackLines)));
			exceptionsPanel.revalidate();
			exceptionsPanel.repaint();
			scrollToBottom();
		});
	}

	/** Очищает весь лог. */
	public void clear() {
		SwingUtilities.invokeLater(() -> {
			logArea.setText("");
			plainLineCount = 0;
			exceptionsPanel.removeAll();
			exceptionsPanel.revalidate();
			exceptionsPanel.repaint();
		});
	}

	private JTextArea buildMainLogArea() {
		JTextArea area = new JTextArea();
		area.setFont(LOG_FONT);
		area.setForeground(GuiApp.theme.getTextDim());
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(false);
		area.setWrapStyleWord(false);
		area.setBorder(BorderFactory.createEmptyBorder());
		area.setFocusable(true);
		DefaultCaret caret = (DefaultCaret) area.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		return area;
	}

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

	private void trimPlainLinesIfNeeded() {
		if (plainLineCount < MAX_PLAIN_LINES) {
			return;
		}
		String text = logArea.getText();
		int newlineIdx = text.indexOf('\n');
		if (newlineIdx < 0) {
			logArea.setText("");
			plainLineCount = 0;
			return;
		}
		logArea.setText(text.substring(newlineIdx + 1));
		plainLineCount--;
	}

	private void scrollToBottom() {
		contentPanel.revalidate();
		contentPanel.repaint();
		SwingUtilities.invokeLater(() -> {
			JScrollBar bar = scrollPane.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
	}

	private JPanel buildExceptionRow(String header, List<String> stackLines) {
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);

		JPanel[] stackPanelRef = {null};
		JPanel headerRow = buildExceptionHeader(header, wrapper, stackPanelRef);
		wrapper.add(headerRow);

		JPanel stackPanel = buildStackPanel(stackLines);
		stackPanel.setVisible(false);
		stackPanelRef[0] = stackPanel;
		wrapper.add(stackPanel);

		return wrapper;
	}

	private JPanel buildExceptionHeader(String headerText, JPanel wrapper, JPanel[] stackPanelRef) {
		JLabel arrow = buildExceptionArrow(false);

		JLabel text = new JLabel(headerText);
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
				JPanel stackPanel = stackPanelRef[0];
				if (stackPanel == null) {
					return;
				}
				boolean nowVisible = !stackPanel.isVisible();
				stackPanel.setVisible(nowVisible);
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
					int[] xs = {cx - SIZE / 2, cx + SIZE / 2, cx};
					int[] ys = {cy - SIZE / 2, cy - SIZE / 2, cy + SIZE / 2};
					g2.fillPolygon(xs, ys, 3);
				} else {
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

		JTextArea stackArea = new JTextArea(String.join("\n", lines));
		stackArea.setFont(STACK_FONT);
		stackArea.setForeground(GuiApp.theme.getTextDim());
		stackArea.setEditable(false);
		stackArea.setOpaque(false);
		stackArea.setLineWrap(false);
		stackArea.setWrapStyleWord(false);
		stackArea.setBorder(BorderFactory.createEmptyBorder());
		stackArea.setFocusable(true);
		DefaultCaret caret = (DefaultCaret) stackArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		panel.add(stackArea);
		return panel;
	}
}
