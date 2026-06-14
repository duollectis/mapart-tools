package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.app.AppPreferences;
import org.duollectis.mapart.tools.gui.i18n.AppLocale;
import org.duollectis.mapart.tools.gui.theme.AppState;
import org.duollectis.mapart.tools.gui.theme.AppTheme;
import org.duollectis.mapart.tools.gui.theme.BuiltinTheme;
import org.duollectis.mapart.tools.gui.dialog.ThemeEditorDialog;
import org.duollectis.mapart.tools.gui.dialog.KeyBindsDialog;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.gui.anim.ThemeTransition;
import org.duollectis.mapart.tools.gui.anim.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Настройки приложения»: язык, тема, анимации, Discord RPC, горячие клавиши.
 * Тумблер полноэкранного режима удалён — окно запоминает своё состояние через {@code UiStateRegistry}.
 */
final class AppSettingsSectionBuilder {

	private AppSettingsSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildLangAccordion(w));
		inner.add(Box.createVerticalStrut(4));
		inner.add(buildThemeAccordion(w));
		inner.add(Box.createVerticalStrut(8));
		inner.add(buildAnimationsRow(w));
		inner.add(Box.createVerticalStrut(4));
		inner.add(buildDiscordRpcRow(w));
		inner.add(Box.createVerticalStrut(4));
		inner.add(buildKeyBindsButton(w));

		w.appSettingsAccordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.app_settings", w.appSettingsAccordion::setTitle);
		UiStateRegistry.bindAccordion("accordion.app_settings", w.appSettingsAccordion);

		return w.appSettingsAccordion;
	}

	private static JComponent buildLangAccordion(MainWindow w) {
		w.langCombo = new SelectionPanel<>(AppLocale.values());
		w.langCombo.setDisplayConverter(item -> item instanceof AppLocale l ? l.getDisplayName() : item.toString());

		// Восстанавливаем визуальный выбор без уведомления слушателей —
		// язык уже загружен через AppState.init() до создания окна.
		w.langCombo.setSelectedItemSilently(AppState.getLocale());

		w.langCombo.addSelectionListener(locale -> {
			if (locale == null) {
				return;
			}

			AppPreferences.saveLocale(locale.getCode());
			UpdatableRegistry.load(locale);
			UpdatableRegistry.fireLang(w);
		});

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.langCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.language", accordion::setTitle);
		w.langCombo.addInitializedSelectionListener(locale -> {
			if (locale == null) {
				return;
			}

			accordion.setSubtitle(w.langCombo.getDisplayText(locale));
		});

		return accordion;
	}

	private static JComponent buildThemeAccordion(MainWindow w) {
		w.themeCombo = new SelectionPanel<>(AppTheme.buildThemeMenuItems());
		w.themeCombo.setDisplayConverter(item -> item instanceof BuiltinTheme bt ? bt.getDisplayName() : item.toString());

		// Восстанавливаем визуальный выбор без уведомления слушателей —
		// тема уже загружена через AppState.init() до создания окна.
		String savedThemeName = AppState.getThemeName();
		Object themeToSelect = BuiltinTheme.isBuiltin(savedThemeName)
				? BuiltinTheme.fromId(savedThemeName)
				: savedThemeName;
		w.themeCombo.setSelectedItemSilently(themeToSelect);

		w.themeCombo.addSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator) {
				return;
			}

			String themeName = item instanceof BuiltinTheme bt ? bt.getId() : item.toString();
			AppPreferences.saveTheme(themeName);
			ThemeTransition.applyColorOnly(w, themeName);
		});

		w.themeCombo.setRowActionProvider(item -> buildThemeRowActions(w, item));

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.themeCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.theme", accordion::setTitle);
		w.themeCombo.addInitializedSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator || item == null) {
				return;
			}

			accordion.setSubtitle(w.themeCombo.getDisplayText(item));
		});

		JButton addThemeBtn = buildAddThemeButton(w);
		accordion.setHeaderTrailingComponent(addThemeBtn);

		return accordion;
	}

	private static List<SelectionPanel.RowAction> buildThemeRowActions(MainWindow w, Object item) {
		List<SelectionPanel.RowAction> actions = new ArrayList<>();

		actions.add(new SelectionPanel.RowAction(AppIcon.EDIT, () -> {
			String themeId = item instanceof BuiltinTheme bt ? bt.getId() : item.toString();
			new ThemeEditorDialog(w, themeId, () -> {
				w.themeCombo.setItems(AppTheme.buildThemeMenuItems());
				String savedTheme = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());
				ThemeTransition.applyColorOnly(w, savedTheme);
			});
		}));

		if (item instanceof BuiltinTheme) {
			return actions;
		}

		actions.add(new SelectionPanel.RowAction(AppIcon.CROSS, () -> {
			String themeId = item.toString();
			AppTheme.deleteCustomTheme(themeId);
			w.themeCombo.setItems(AppTheme.buildThemeMenuItems());
			String savedTheme = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());
			ThemeTransition.applyColorOnly(w, savedTheme);
		}));

		return actions;
	}

	private static JButton buildAddThemeButton(MainWindow w) {
		JButton btn = buildIconButton(AppIcon.PALETTE, new Insets(3, 5, 3, 5), w);
		UpdatableRegistry.registerLang("btn.new_theme", t -> AppTooltip.install(btn, t));
		btn.addActionListener(e -> new ThemeEditorDialog(w, null, () -> {
			w.themeCombo.setItems(AppTheme.buildThemeMenuItems());
			String savedTheme = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());
			ThemeTransition.applyColorOnly(w, savedTheme);
		}));

		// Клик на кнопку не должен переключать аккордеон
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				e.consume();
			}
		});

		return btn;
	}

	private static JPanel buildAnimationsRow(MainWindow w) {
		boolean initial = AppPreferences.loadAnimations(true);
		UiAnimator.animationsEnabled = initial;

		return buildToggleRow("app_settings.animations", initial, enabled -> {
			UiAnimator.animationsEnabled = enabled;
			AppPreferences.saveAnimations(enabled);
		});
	}

	private static JPanel buildDiscordRpcRow(MainWindow w) {
		return buildToggleRow("app_settings.discord_rpc", AppPreferences.loadDiscordRpc(false), enabled -> {
			AppPreferences.saveDiscordRpc(enabled);
			DiscordRpc.setEnabled(enabled);
		});
	}

	private static JPanel buildKeyBindsButton(MainWindow w) {
		JButton btn = buildAccentButton("", w);
		UpdatableRegistry.registerLang("btn.key_binds", btn::setText);
		btn.addActionListener(e -> new KeyBindsDialog(w));

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(btn, BorderLayout.CENTER);

		return row;
	}
}
