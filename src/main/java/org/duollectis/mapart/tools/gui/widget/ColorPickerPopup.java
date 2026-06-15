package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.GuiApp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Consumer;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;

/**
 * Компактный всплывающий пикер цвета в стиле приложения.
 * Реализован как {@link JPanel} добавляемая в {@link JLayeredPane} родительского окна —
 * без отдельного окна, без мерцания.
 * Закрывается по Escape или клику вне панели.
 */
public class ColorPickerPopup extends JPanel {

	private static final int POPUP_WIDTH = 260;
	private static final int POPUP_HEIGHT = 300;
	private static final int CORNER_RADIUS = 14;
	private static final int PADDING = 14;
	private static final int SB_SIZE = 180;
	private static final int HUE_HEIGHT = 14;
	private static final int PREVIEW_SIZE = 28;

	private final Consumer<Color> onPick;
	private final JLayeredPane layeredPane;
	private final AWTEventListener outsideClickListener;

	private float hue;
	private float saturation;
	private float brightness;

	private BufferedImage sbGradient;
	private boolean sbGradientDirty = true;
	private BufferedImage cachedBackground;

	private final SbSquare sbSquare = new SbSquare();
	private final HueSlider hueSlider = new HueSlider();
	private final JTextField hexField;
	private final PreviewSwatch previewSwatch = new PreviewSwatch();

	private boolean updatingHex = false;

	private ColorPickerPopup(JLayeredPane layeredPane, Color initial, Point anchorInLayer, Consumer<Color> onPick) {
		super(null);
		this.onPick = onPick;
		this.layeredPane = layeredPane;

		float[] hsb = Color.RGBtoHSB(initial.getRed(), initial.getGreen(), initial.getBlue(), null);
		hue = hsb[0];
		saturation = hsb[1];
		brightness = hsb[2];

		hexField = buildHexField();
		setOpaque(false);
		setDoubleBuffered(true);

		buildLayout();
		setBounds(anchorInLayer.x, anchorInLayer.y, POPUP_WIDTH, POPUP_HEIGHT);

		outsideClickListener = event -> {
			if (event instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
				if (!isShowing()) {
					return;
				}

				Point p = me.getLocationOnScreen();
				Point myScreen = getLocationOnScreen();
				Rectangle myBounds = new Rectangle(myScreen.x, myScreen.y, getWidth(), getHeight());

				if (!myBounds.contains(p)) {
					close();
				}
			}
		};
	}

	/**
	 * Открывает попап рядом с точкой {@code anchor} на экране.
	 *
	 * @param parent  родительское окно (для получения {@link JLayeredPane})
	 * @param initial начальный цвет
	 * @param anchor  точка на экране (обычно позиция курсора мыши)
	 * @param onPick  колбэк с выбранным цветом — вызывается при нажатии OK
	 */
	public static void show(Window parent, Color initial, Point anchor, Consumer<Color> onPick) {
		JRootPane rootPane = getRootPane(parent);
		if (rootPane == null) {
			return;
		}

		JLayeredPane layeredPane = rootPane.getLayeredPane();
		Point anchorInLayer = new Point(anchor);
		SwingUtilities.convertPointFromScreen(anchorInLayer, layeredPane);
		anchorInLayer = clampToLayer(anchorInLayer, layeredPane);

		ColorPickerPopup popup = new ColorPickerPopup(layeredPane, initial, anchorInLayer, onPick);
		// Захватываем фон ДО добавления попапа в иерархию — один раз, без рывков при перетаскивании
		popup.cachedBackground = popup.captureBlurredBackground();
		layeredPane.add(popup, JLayeredPane.POPUP_LAYER);
		layeredPane.revalidate();
		layeredPane.repaint();

		popup.installEscapeClose(rootPane);
		Toolkit.getDefaultToolkit().addAWTEventListener(popup.outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
		popup.sbSquare.requestFocusInWindow();
	}

	private void close() {
		Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
		layeredPane.remove(this);
		layeredPane.revalidate();
		layeredPane.repaint();
	}

	private void confirm() {
		onPick.accept(currentColor());
		close();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Shape clip = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
		g2.setClip(clip);

		if (cachedBackground != null) {
			g2.drawImage(cachedBackground, 0, 0, null);
		}

		Color bg = GuiApp.theme.getBgCard();
		g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 210));
		g2.fill(clip);

		g2.setClip(null);
		g2.setColor(GuiApp.theme.getBorder());
		g2.setStroke(new BasicStroke(1f));
		g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS));

		g2.dispose();
	}

	private BufferedImage captureBlurredBackground() {
		if (layeredPane == null || getWidth() <= 0 || getHeight() <= 0) {
			return null;
		}

		BufferedImage capture = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D cg = capture.createGraphics();

		// Рисуем содержимое layeredPane под нашим попапом (все слои ниже POPUP_LAYER)
		cg.translate(-getX(), -getY());
		for (Component comp : layeredPane.getComponents()) {
			if (comp == this) {
				continue;
			}

			Integer layer = layeredPane.getLayer(comp);
			if (layer >= JLayeredPane.POPUP_LAYER) {
				continue;
			}

			cg.translate(comp.getX(), comp.getY());
			comp.paint(cg);
			cg.translate(-comp.getX(), -comp.getY());
		}

		cg.dispose();
		return applyBoxBlur(capture, 8);
	}

	private static BufferedImage applyBoxBlur(BufferedImage src, int radius) {
		int size = radius * 2 + 1;
		float[] data = new float[size * size];
		float value = 1f / (size * size);
		java.util.Arrays.fill(data, value);
		Kernel kernel = new Kernel(size, size, data);
		ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
		return op.filter(src, null);
	}

	private void buildLayout() {
		int x = PADDING;
		int y = PADDING;

		sbSquare.setBounds(x, y, SB_SIZE, SB_SIZE);
		add(sbSquare);

		y += SB_SIZE + 10;
		hueSlider.setBounds(x, y, SB_SIZE, HUE_HEIGHT);
		add(hueSlider);

		y += HUE_HEIGHT + 10;
		previewSwatch.setBounds(x, y, PREVIEW_SIZE, PREVIEW_SIZE);
		add(previewSwatch);

		hexField.setBounds(x + PREVIEW_SIZE + 8, y, SB_SIZE - PREVIEW_SIZE - 8, PREVIEW_SIZE);
		add(hexField);

		y += PREVIEW_SIZE + 10;
		JButton okBtn = buildOkButton();
		okBtn.setBounds(x, y, SB_SIZE, 30);
		add(okBtn);
	}

	private JTextField buildHexField() {
		JTextField field = new JTextField() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(GuiApp.theme.getBgInput());
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};

		field.setOpaque(false);
		field.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		field.setForeground(GuiApp.theme.getText());
		field.setCaretColor(GuiApp.theme.getText());
		field.setText(toHex(Color.getHSBColor(hue, saturation, brightness)));

		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { onHexChanged(); }
			@Override public void removeUpdate(DocumentEvent e) { onHexChanged(); }
			@Override public void changedUpdate(DocumentEvent e) {}
		});

		return field;
	}

	private JButton buildOkButton() {
		JButton btn = new JButton("OK") {
			private float hover = 0f;
			private Timer hoverTimer;

			{
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						animateHover(1f);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						animateHover(0f);
					}
				});
			}

			private void animateHover(float to) {
				if (hoverTimer != null) {
					hoverTimer.stop();
				}

				float from = hover;
				long start = System.currentTimeMillis();
				hoverTimer = new Timer(16, ev -> {
					float t = Math.min(1f, (System.currentTimeMillis() - start) / 120f);
					hover = from + (to - from) * t;
					repaint();

					if (t >= 1f) {
						((Timer) ev.getSource()).stop();
					}
				});
				hoverTimer.start();
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color bg = lerpColor(GuiApp.theme.getAccent(), GuiApp.theme.getAccentBright(), hover);
				g2.setColor(bg);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

				g2.setColor(GuiApp.theme.getTextOnAccent());
				g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
				FontMetrics fm = g2.getFontMetrics();
				int tx = (getWidth() - fm.stringWidth(getText())) / 2;
				int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
				g2.drawString(getText(), tx, ty);
				g2.dispose();
			}
		};

		btn.setOpaque(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		UiAnimator.applyHandCursor(btn);
		btn.addActionListener(e -> confirm());

		return btn;
	}

	private void onHexChanged() {
		if (updatingHex) {
			return;
		}

		String text = hexField.getText().replace("#", "").trim();

		if (text.length() != 6) {
			return;
		}

		try {
			Color parsed = Color.decode("#" + text);
			float[] hsb = Color.RGBtoHSB(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), null);
			hue = hsb[0];
			saturation = hsb[1];
			brightness = hsb[2];
			sbGradientDirty = true;
			repaint();
		} catch (NumberFormatException ignored) {}
	}

	private void syncHexField() {
		updatingHex = true;
		hexField.setText(toHex(currentColor()));
		updatingHex = false;
	}

	private Color currentColor() {
		return Color.getHSBColor(hue, saturation, brightness);
	}

	private void installEscapeClose(JRootPane rootPane) {
		KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "closeColorPicker");
		rootPane.getActionMap().put("closeColorPicker", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				close();
				rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(escape);
				rootPane.getActionMap().remove("closeColorPicker");
			}
		});
	}

	private static Point clampToLayer(Point p, JLayeredPane layer) {
		int x = Math.max(0, Math.min(p.x, layer.getWidth() - POPUP_WIDTH));
		int y = Math.max(0, Math.min(p.y, layer.getHeight() - POPUP_HEIGHT));
		return new Point(x, y);
	}

	private static JRootPane getRootPane(Window window) {
		if (window instanceof JFrame f) {
			return f.getRootPane();
		}

		if (window instanceof JDialog d) {
			return d.getRootPane();
		}

		return null;
	}

	private static String toHex(Color color) {
		return String.format("%06X", color.getRGB() & 0xFFFFFF);
	}

	private static Color lerpColor(Color a, Color b, float t) {
		int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		return new Color(
			Math.clamp(r, 0, 255),
			Math.clamp(g, 0, 255),
			Math.clamp(bl, 0, 255)
		);
	}

	// ── Внутренние компоненты ──────────────────────────────────────────────────

	private final class SbSquare extends JPanel {

		SbSquare() {
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

			MouseAdapter adapter = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					updateFromMouse(e);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					updateFromMouse(e);
				}
			};

			addMouseListener(adapter);
			addMouseMotionListener(adapter);
		}

		private void updateFromMouse(MouseEvent e) {
			saturation = Math.clamp((float) e.getX() / getWidth(), 0f, 1f);
			brightness = 1f - Math.clamp((float) e.getY() / getHeight(), 0f, 1f);
			syncHexField();
			repaint();
			previewSwatch.repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			if (sbGradientDirty || sbGradient == null
				|| sbGradient.getWidth() != getWidth()
				|| sbGradient.getHeight() != getHeight()
			) {
				sbGradient = buildSbGradient(getWidth(), getHeight());
				sbGradientDirty = false;
			}

			g2.drawImage(sbGradient, 0, 0, null);

			g2.setColor(GuiApp.theme.getBorder());
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

			int cx = (int) (saturation * getWidth());
			int cy = (int) ((1f - brightness) * getHeight());
			g2.setColor(Color.WHITE);
			g2.fillOval(cx - 6, cy - 6, 12, 12);
			g2.setColor(brightness > 0.5f ? Color.BLACK : Color.WHITE);
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawOval(cx - 6, cy - 6, 12, 12);

			g2.dispose();
		}

		private BufferedImage buildSbGradient(int w, int h) {
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			for (int px = 0; px < w; px++) {
				float s = (float) px / w;
				for (int py = 0; py < h; py++) {
					float b = 1f - (float) py / h;
					img.setRGB(px, py, Color.HSBtoRGB(hue, s, b));
				}
			}
			return img;
		}
	}

	private final class HueSlider extends JPanel {

		private static final BufferedImage HUE_STRIP = buildHueStrip();

		HueSlider() {
			setOpaque(false);
			UiAnimator.applyHandCursor(this);

			MouseAdapter adapter = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					updateFromMouse(e);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					updateFromMouse(e);
				}
			};

			addMouseListener(adapter);
			addMouseMotionListener(adapter);
		}

		private void updateFromMouse(MouseEvent e) {
			hue = Math.clamp((float) e.getX() / getWidth(), 0f, 1f);
			sbGradientDirty = true;
			syncHexField();
			repaint();
			sbSquare.repaint();
			previewSwatch.repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int arc = getHeight();
			Shape clip = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
			g2.setClip(clip);
			g2.drawImage(HUE_STRIP, 0, 0, getWidth(), getHeight(), null);
			g2.setClip(null);

			g2.setColor(GuiApp.theme.getBorder());
			g2.setStroke(new BasicStroke(1f));
			g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));

			int tx = (int) (hue * getWidth());
			g2.setColor(Color.WHITE);
			g2.fillRoundRect(tx - 4, 0, 8, getHeight(), 4, 4);
			g2.setColor(new Color(0, 0, 0, 80));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(tx - 4, 0, 8, getHeight(), 4, 4);

			g2.dispose();
		}

		private static BufferedImage buildHueStrip() {
			BufferedImage img = new BufferedImage(360, 1, BufferedImage.TYPE_INT_RGB);
			for (int i = 0; i < 360; i++) {
				img.setRGB(i, 0, Color.HSBtoRGB(i / 360f, 1f, 1f));
			}
			return img;
		}
	}

	private final class PreviewSwatch extends JPanel {

		PreviewSwatch() {
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2.setColor(currentColor());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

			g2.setColor(GuiApp.theme.getBorder());
			g2.setStroke(new BasicStroke(1f));
			g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));

			g2.dispose();
		}
	}
}
