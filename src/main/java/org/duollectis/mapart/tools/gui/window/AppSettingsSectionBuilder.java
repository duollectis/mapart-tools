package org.duollectis.mapart.tools.gui.window;

import org.duollectis.mapart.tools.gui.AppLocale;
import org.duollectis.mapart.tools.gui.AppPreferences;
import org.duollectis.mapart.tools.gui.AppTheme;
import org.duollectis.mapart.tools.gui.BuiltinTheme;
import org.duollectis.mapart.tools.gui.dialog.ThemeEditorDialog;
import org.duollectis.mapart.tools.gui.dialog.KeyBindsDialog;
import org.duollectis.mapart.tools.gui.util.AppIcon;
import org.duollectis.mapart.tools.gui.util.AppTooltip;
import org.duollectis.mapart.tools.app.DiscordRpc;
import org.duollectis.mapart.tools.gui.util.ThemeTransition;
import org.duollectis.mapart.tools.gui.util.UiAnimator;
import org.duollectis.mapart.tools.gui.util.UiStateRegistry;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.AccordionPanel;
import org.duollectis.mapart.tools.gui.widget.SelectionPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static org.duollectis.mapart.tools.gui.window.SettingsWidgetFactory.*;

/**
 * Строит аккордеон «Настройки приложения»: язык, тема, анимации, Discord RPC, горячие клавиши.
 * Тумблер полноэкранного режима удалён — окно запоминает своё состояние через {@code UiStateRegistry}.
 */
final class AppSettingsSectionBuilder {

	private AppSettingsSectionBuilder() {}

	static AccordionPanel build(MainWindow w) {
		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(buildVersionRow(w));
		inner.add(Box.createVerticalStrut(8));
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

	private static JComponent buildVersionRow(MainWindow w) {
		String[] versions = w.actions.loadVersions();
		java.util.List<Object> versionItems = new ArrayList<>();

		for (String v : versions) {
			versionItems.add(v);
		}

		w.versionCombo = new SelectionPanel<>(versionItems);

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.versionCombo);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("label.version", accordion::setTitle);
		w.versionCombo.addInitializedSelectionListener(
			item -> accordion.setSubtitle(item != null ? item.toString() : null)
		);

		return accordion;
	}

	private static JComponent buildLangAccordion(MainWindow w) {
		w.langCombo = new SelectionPanel<>(AppLocale.values());
		w.langCombo.setDisplayConverter(item -> item instanceof AppLocale l ? l.getDisplayName() : item.toString());
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
		w.themeCombo.addSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator) {
				return;
			}

			String themeName = item instanceof BuiltinTheme bt ? bt.getId() : item.toString();
			AppPreferences.saveTheme(themeName);
			ThemeTransition.applyColorOnly(w, themeName);
		});

		JButton editThemeBtn = buildIconButton(AppIcon.EDIT, new Insets(4, 8, 4, 8), w);
		UpdatableRegistry.registerLang("btn.edit_theme", t -> AppTooltip.install(editThemeBtn, t));
		editThemeBtn.addActionListener(e -> {
			String currentThemeId = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());
			new ThemeEditorDialog(w, currentThemeId, () -> {
				w.themeCombo.setItems(AppTheme.buildThemeMenuItems());
				String savedTheme = AppPreferences.loadTheme(BuiltinTheme.DARK.getId());
				ThemeTransition.applyColorOnly(w, savedTheme);
			});
		});

		JPanel inner = AccordionPanel.createContentPanel();
		inner.add(w.themeCombo);
		inner.add(Box.createVerticalStrut(4));
		inner.add(editThemeBtn);

		AccordionPanel accordion = new AccordionPanel("", inner);
		UpdatableRegistry.registerLang("section.theme", accordion::setTitle);
		w.themeCombo.addInitializedSelectionListener(item -> {
			if (item instanceof SelectionPanel.Separator || item == null) {
				return;
			}

			accordion.setSubtitle(w.themeCombo.getDisplayText(item));
		});

		return accordion;
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
