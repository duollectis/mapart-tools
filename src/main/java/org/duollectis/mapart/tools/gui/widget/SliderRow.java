package org.duollectis.mapart.tools.gui.widget;

import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.window.MainWindow;

import javax.swing.*;
import java.awt.*;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;


/**
 * Инкапсулирует строку слайдера в двухстрочном формате:
 * <pre>
 *   [имя]                    [значение ◀ ▶] [сброс]
 *   [══════════════ слайдер ══════════════════════]
 * </pre>
 * Слайдер растягивается на всю доступную ширину.
 * Каждый экземпляр занимает 2 строки в GridBag-сетке (row*2 и row*2+1).
 */
public class SliderRow {

	private static final int VALUE_LABEL_WIDTH = 28;
	private static final int LABEL_HEIGHT = 16;

	private final StyledSlider slider;
	private final JLabel valueLabel;
	private final FadingLabel nameLabel;
	private final int defaultValue;
	private final boolean isGamma;

	public SliderRow(int min, int max, int defaultValue, boolean isGamma) {
		this.defaultValue = defaultValue;
		this.isGamma = isGamma;

		slider = new StyledSlider(min, max, defaultValue);
		valueLabel = buildSliderValueLabel(defaultValue, isGamma);
		nameLabel = buildFadingDimLabel("");

		slider.addChangeListener(e -> valueLabel.setText(formatSliderValue(slider.getValue(), isGamma)));
	}

	public void addChangeListener(Runnable onChange) {
		slider.addChangeListener(e -> onChange.run());
	}

	public void registerLang(String langKey) {
		UpdatableRegistry.registerLang(langKey, nameLabel::setTextInstant);
	}

	/**
	 * Добавляет строку в GridBag-сетку без дополнительного компонента.
	 * Занимает строки {@code row*2} и {@code row*2+1}.
	 */
	public void addToGrid(JPanel grid, GridBagConstraints gbc, int row, MainWindow w) {
		addToGrid(grid, gbc, row, null, w);
	}

	/**
	 * Добавляет строку в GridBag-сетку с опциональным extra-компонентом после кнопки сброса.
	 * Занимает строки {@code row*2} (заголовок) и {@code row*2+1} (слайдер).
	 */
	public void addToGrid(JPanel grid, GridBagConstraints gbc, int row, JComponent extra, MainWindow w) {
		int headerRow = row * 2;
		int sliderRow = row * 2 + 1;

		JButton resetBtn = buildResetButton(w);
		resetBtn.addActionListener(e -> reset());

		JPanel headerPanel = buildHeaderPanel(resetBtn, extra, w);

		gbc.gridy = headerRow;
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(4, 0, 1, 0);
		grid.add(headerPanel, gbc);

		gbc.gridy = sliderRow;
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 4, 0);
		grid.add(slider, gbc);
	}

	public void setRowVisible(boolean visible) {
		nameLabel.setVisible(visible);
		slider.setVisible(visible);
		valueLabel.setVisible(visible);

		Container parent = slider.getParent();

		if (parent == null) {
			return;
		}

		GridBagLayout layout = parent.getLayout() instanceof GridBagLayout gbl ? gbl : null;

		if (layout == null) {
			return;
		}

		int sliderGridY = layout.getConstraints(slider).gridy;
		int headerGridY = sliderGridY - 1;

		for (Component child : parent.getComponents()) {
			int childY = layout.getConstraints(child).gridy;

			if (childY == sliderGridY || childY == headerGridY) {
				child.setVisible(visible);
			}
		}
	}

	public int getValue() {
		return slider.getValue();
	}

	public void setValue(int value) {
		slider.setValue(value);
	}

	/** Устанавливает значение без уведомления слушателей — для программной синхронизации каналов. */
	public void setValueSilently(int value) {
		slider.setValueSilently(value);
		valueLabel.setText(formatSliderValue(value, isGamma));
	}

	public void reset() {
		slider.setValue(defaultValue);
		valueLabel.setText(formatSliderValue(defaultValue, isGamma));
	}

	public StyledSlider getSlider() {
		return slider;
	}

	public FadingLabel getNameLabel() {
		return nameLabel;
	}

	private JPanel buildHeaderPanel(JButton resetBtn, JComponent extra, MainWindow w) {
		JPanel valueCell = buildValueCell(w);

		JPanel header = new JPanel(new GridBagLayout());
		header.setOpaque(false);

		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, StyledSlider.TRACK_PADDING, 0, 4);
		header.add(nameLabel, c);

		c.gridx = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(0, 0, 0, 2);
		header.add(valueCell, c);

		c.gridx = 2;
		c.insets = new Insets(0, 0, 0, 0);
		header.add(resetBtn, c);

		if (extra != null) {
			c.gridx = 3;
			c.insets = new Insets(0, 4, 0, 0);
			header.add(extra, c);
		}

		return header;
	}

	private JPanel buildValueCell(MainWindow w) {
		JButton decBtn = buildIconButton(AppIcon.PREV, new Insets(2, 1, 2, 1), w);
		JButton incBtn = buildIconButton(AppIcon.NEXT, new Insets(2, 1, 2, 1), w);

		decBtn.addActionListener(e -> {
			int next = Math.max(slider.getMinimum(), slider.getValue() - 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});

		incBtn.addActionListener(e -> {
			int next = Math.min(slider.getMaximum(), slider.getValue() + 1);
			slider.setValue(next);
			valueLabel.setText(formatSliderValue(next, isGamma));
		});

		Dimension labelSize = new Dimension(VALUE_LABEL_WIDTH, LABEL_HEIGHT);
		valueLabel.setPreferredSize(labelSize);
		valueLabel.setMinimumSize(labelSize);
		valueLabel.setMaximumSize(labelSize);

		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.X_AXIS));
		cell.setOpaque(false);
		cell.add(decBtn);
		cell.add(valueLabel);
		cell.add(incBtn);

		return cell;
	}
}
