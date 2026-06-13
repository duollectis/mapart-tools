package org.duollectis.mapart.tools.gui.dialog;

import org.duollectis.mapart.tools.gui.GuiApp;
import org.duollectis.mapart.tools.gui.keybind.KeyBind;
import org.duollectis.mapart.tools.gui.keybind.KeyBindAction;
import org.duollectis.mapart.tools.gui.keybind.KeyBindManager;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Диалог редактирования горячих клавиш.
 * Каждая строка — одно действие. Клик по кнопке переводит её в режим захвата:
 * следующее нажатие клавиши (с учётом модификаторов) становится новым биндом.
 * Escape во время захвата отменяет редактирование строки.
 */
public class KeyBindsDialog extends JDialog {

	private static final int ROW_HEIGHT = 36;
	private static final int LABEL_WIDTH = 220;
	private static final int BIND_BTN_WIDTH = 160;

	private final Map<KeyBindAction, JButton> bindButtons = new EnumMap<>(KeyBindAction.class);
	private KeyBindAction capturing;

	public KeyBindsDialog(JFrame parent) {
		super(parent, UpdatableRegistry.translate("keybinds.title"), true);
		buildUi();
		pack();
		setResizable(false);
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	private void buildUi() {
		JPanel content = buildCard();
		content.setLayout(new BorderLayout(0, 12));
		content.setBorder(new EmptyBorder(16, 16, 16, 16));

		content.add(buildBindsGrid(), BorderLayout.CENTER);
		content.add(buildBottomBar(), BorderLayout.SOUTH);

		setContentPane(content);
	}

	private JPanel buildCard() {
		return new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgCard());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
				g2.dispose();
			}
		};
	}

	private JPanel buildBindsGrid() {
		JPanel grid = new JPanel();
		grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
		grid.setOpaque(false);

		for (KeyBindAction action : KeyBindAction.values()) {
			grid.add(buildRow(action));
			grid.add(Box.createVerticalStrut(4));
		}

		return grid;
	}

	private JPanel buildRow(KeyBindAction action) {
		JLabel label = new JLabel(UpdatableRegistry.translate(action.getLangKey()));
		label.setForeground(GuiApp.theme.getText());
		label.setPreferredSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));

		JButton bindBtn = buildBindButton(action);
		bindButtons.put(action, bindBtn);

		JButton resetBtn = buildResetButton(action, bindBtn);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		row.add(label);
		row.add(bindBtn);
		row.add(resetBtn);

		return row;
	}

	private JButton buildBindButton(KeyBindAction action) {
		JButton btn = new JButton(KeyBindManager.getBind(action).displayText()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color bg = capturing == action
					? GuiApp.theme.getAccent()
					: GuiApp.theme.getBgInput();

				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(GuiApp.theme.getBorder());
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

				FontMetrics fm = g2.getFontMetrics();
				String text = getText();
				int tx = (getWidth() - fm.stringWidth(text)) / 2;
				int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
				g2.setColor(capturing == action ? GuiApp.theme.getTextOnAccent() : GuiApp.theme.getText());
				g2.drawString(text, tx, ty);
				g2.dispose();
			}
		};

		btn.setPreferredSize(new Dimension(BIND_BTN_WIDTH, ROW_HEIGHT));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));

		btn.addActionListener(e -> startCapture(action, btn));

		return btn;
	}

	private JButton buildResetButton(KeyBindAction action, JButton bindBtn) {
		JButton btn = new JButton("↺") {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgInput());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.setColor(GuiApp.theme.getBorder());
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				FontMetrics fm = g2.getFontMetrics();
				String text = getText();
				int tx = (getWidth() - fm.stringWidth(text)) / 2;
				int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
				g2.setColor(GuiApp.theme.getTextDim());
				g2.drawString(text, tx, ty);
				g2.dispose();
			}
		};

		btn.setPreferredSize(new Dimension(ROW_HEIGHT, ROW_HEIGHT));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setToolTipText(UpdatableRegistry.translate("keybinds.reset_one"));

		btn.addActionListener(e -> {
			KeyBindManager.resetToDefault(action);
			bindBtn.setText(KeyBindManager.getBind(action).displayText());
			bindBtn.repaint();
		});

		return btn;
	}

	private void startCapture(KeyBindAction action, JButton btn) {
		capturing = action;
		btn.setText(UpdatableRegistry.translate("keybinds.press_key"));
		btn.repaint();

		btn.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (isModifierOnly(e.getKeyCode())) {
					return;
				}

				btn.removeKeyListener(this);

				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					btn.setText(KeyBindManager.getBind(action).displayText());
					capturing = null;
					btn.repaint();
					return;
				}

				KeyBind newBind = new KeyBind(e.getKeyCode(), e.getModifiersEx());
				KeyBindManager.setBind(action, newBind);
				btn.setText(newBind.displayText());
				capturing = null;
				btn.repaint();
				e.consume();
			}
		});

		btn.requestFocusInWindow();
	}

	private JPanel buildBottomBar() {
		JButton resetAll = buildAccentButton(
			UpdatableRegistry.translate("keybinds.reset_all"),
			GuiApp.theme.getBgInput(),
			GuiApp.theme.getText()
		);

		resetAll.addActionListener(e -> {
			KeyBindManager.resetAllToDefault();
			refreshAllButtons();
		});

		JButton close = buildAccentButton(
			UpdatableRegistry.translate("keybinds.close"),
			GuiApp.theme.getAccent(),
			GuiApp.theme.getTextOnAccent()
		);

		close.addActionListener(e -> dispose());

		JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		bar.setOpaque(false);
		bar.add(resetAll);
		bar.add(close);

		return bar;
	}

	private void refreshAllButtons() {
		for (KeyBindAction action : KeyBindAction.values()) {
			JButton btn = bindButtons.get(action);

			if (btn != null) {
				btn.setText(KeyBindManager.getBind(action).displayText());
				btn.repaint();
			}
		}
	}

	private JButton buildAccentButton(String text, Color bg, Color fg) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				FontMetrics fm = g2.getFontMetrics();
				String t = getText();
				int tx = (getWidth() - fm.stringWidth(t)) / 2;
				int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
				g2.setColor(fg);
				g2.drawString(t, tx, ty);
				g2.dispose();
			}
		};

		btn.setPreferredSize(new Dimension(140, 32));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);

		return btn;
	}

	private static boolean isModifierOnly(int keyCode) {
		return keyCode == KeyEvent.VK_CONTROL
			|| keyCode == KeyEvent.VK_SHIFT
			|| keyCode == KeyEvent.VK_ALT
			|| keyCode == KeyEvent.VK_META;
	}
}
