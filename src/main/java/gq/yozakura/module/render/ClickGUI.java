package gq.yozakura.module.render;

import gq.yozakura.core.Client;
import gq.yozakura.core.ClientLanguage;
import gq.yozakura.ui.click.material.MaterialClickGui;
import gq.yozakura.ui.click.sakura.SakuraClickGui;
import gq.yozakura.ui.click.web.WebClickGuiService;
import gq.yozakura.ui.click.yozakura.YozakuraClickGui;
import gq.yozakura.engine.render.ui.VisualPalette;
import org.lwjgl.input.Keyboard;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;

public class ClickGUI extends Module {
	public enum GuiStyle {
		MATERIAL,
		YOZAKURA,
		SAKURA,
		WEB
	}

	public enum Palette {
		NIGHT_BLOOM,
		SAKURA,
		OCEAN,
		GRAPHITE,
		CUSTOM
	}

	private static final class LanguageMode extends Mode<ClientLanguage> {
		private LanguageMode() {
			super("Language", "Language", ClientLanguage.values(), ClientLanguage.ENGLISH);
		}

		@Override
		public void setValue(ClientLanguage value) {
			ClientLanguage resolved = value == null ? ClientLanguage.ENGLISH : value;
			super.setValue(resolved);
			Client.CHINESE = resolved.isChinese();
		}
	}

	public static final Mode<GuiStyle> guiStyle = new Mode<GuiStyle>("Style", "Style", GuiStyle.values(), GuiStyle.MATERIAL);
	public static final Mode<Palette> palette = new Mode<Palette>("Palette", "Palette", Palette.values(), Palette.NIGHT_BLOOM);
	public static final Mode<ClientLanguage> language = new LanguageMode();
	public static final Numbers<Double> canvasRed = color("UI Canvas [all GUIs] Red", "CanvasRed", 13.0);
	public static final Numbers<Double> canvasGreen = color("UI Canvas [all GUIs] Green", "CanvasGreen", 9.0);
	public static final Numbers<Double> canvasBlue = color("UI Canvas [all GUIs] Blue", "CanvasBlue", 20.0);
	public static final Numbers<Double> surfaceRed = color("UI Panels [all GUIs] Red", "SurfaceRed", 26.0);
	public static final Numbers<Double> surfaceGreen = color("UI Panels [all GUIs] Green", "SurfaceGreen", 16.0);
	public static final Numbers<Double> surfaceBlue = color("UI Panels [all GUIs] Blue", "SurfaceBlue", 30.0);
	public static final Numbers<Double> accentRed = color("UI Accent [GUIs + GlowESP + TargetESP] Red", "AccentRed", 233.0);
	public static final Numbers<Double> accentGreen = color("UI Accent [GUIs + GlowESP + TargetESP] Green", "AccentGreen", 139.0);
	public static final Numbers<Double> accentBlue = color("UI Accent [GUIs + GlowESP + TargetESP] Blue", "AccentBlue", 193.0);
	public static final Numbers<Double> accentAltRed = color("UI Alt Accent [GUIs + GlowESP + TargetESP] Red", "AccentAltRed", 114.0);
	public static final Numbers<Double> accentAltGreen = color("UI Alt Accent [GUIs + GlowESP + TargetESP] Green", "AccentAltGreen", 223.0);
	public static final Numbers<Double> accentAltBlue = color("UI Alt Accent [GUIs + GlowESP + TargetESP] Blue", "AccentAltBlue", 246.0);
	public static final Numbers<Double> dangerRed = color("Hurt / Low HP [ESP + Chams + TargetESP] Red", "DangerRed", 255.0);
	public static final Numbers<Double> dangerGreen = color("Hurt / Low HP [ESP + Chams + TargetESP] Green", "DangerGreen", 113.0);
	public static final Numbers<Double> dangerBlue = color("Hurt / Low HP [ESP + Chams + TargetESP] Blue", "DangerBlue", 140.0);
	public static final Numbers<Double> playerRed = color("Player [ESP + Chams] Red", "PlayerRed", 169.0);
	public static final Numbers<Double> playerGreen = color("Player [ESP + Chams] Green", "PlayerGreen", 75.0);
	public static final Numbers<Double> playerBlue = color("Player [ESP + Chams] Blue", "PlayerBlue", 112.0);
	public static final Numbers<Double> mobRed = color("Mob [ESP + Chams] Red", "MobRed", 114.0);
	public static final Numbers<Double> mobGreen = color("Mob [ESP + Chams] Green", "MobGreen", 223.0);
	public static final Numbers<Double> mobBlue = color("Mob [ESP + Chams] Blue", "MobBlue", 246.0);
	public static final Numbers<Double> animalRed = color("Animal [ESP + Chams] Red", "AnimalRed", 255.0);
	public static final Numbers<Double> animalGreen = color("Animal [ESP + Chams] Green", "AnimalGreen", 119.0);
	public static final Numbers<Double> animalBlue = color("Animal [ESP + Chams] Blue", "AnimalBlue", 146.0);
	public static final Numbers<Double> chestRed = color("Chest [StorageESP] Red", "ChestRed", 230.0);
	public static final Numbers<Double> chestGreen = color("Chest [StorageESP] Green", "ChestGreen", 166.0);
	public static final Numbers<Double> chestBlue = color("Chest [StorageESP] Blue", "ChestBlue", 107.0);
	public static final Numbers<Double> enderChestRed = color("Ender Chest [StorageESP] Red", "EnderChestRed", 159.0);
	public static final Numbers<Double> enderChestGreen = color("Ender Chest [StorageESP] Green", "EnderChestGreen", 140.0);
	public static final Numbers<Double> enderChestBlue = color("Ender Chest [StorageESP] Blue", "EnderChestBlue", 255.0);
	public static final Numbers<Double> windowX = new Numbers<Double>("Window X", "WindowX", -1.0, -1.0, 2000.0, 1.0);
	public static final Numbers<Double> windowY = new Numbers<Double>("Window Y", "WindowY", -1.0, -1.0, 1200.0, 1.0);
	public static final Numbers<Double> sakuraScale = new Numbers<Double>("Sakura Scale", "SakuraScale", 1.0, 0.70, 1.35, 0.01);
	public static final Numbers<Double> sideStatsOffsetX = new Numbers<Double>("Stats X", "StatsX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideStatsOffsetY = new Numbers<Double>("Stats Y", "StatsY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideSummaryOffsetX = new Numbers<Double>("Info X", "InfoX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideSummaryOffsetY = new Numbers<Double>("Info Y", "InfoY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideDesignOffsetX = new Numbers<Double>("Design X", "DesignX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideDesignOffsetY = new Numbers<Double>("Design Y", "DesignY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> moduleOffsetX = new Numbers<Double>("Module Panel X", "MListX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> moduleOffsetY = new Numbers<Double>("Module Panel Y", "MListY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> detailOffsetX = new Numbers<Double>("Detail Panel X", "DetX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> detailOffsetY = new Numbers<Double>("Detail Panel Y", "DetY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideOffsetX = new Numbers<Double>("Side Panel X", "SidePX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideOffsetY = new Numbers<Double>("Side Panel Y", "SidePY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> userPanelOffsetX = new Numbers<Double>("User Panel X", "UserPX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> userPanelOffsetY = new Numbers<Double>("User Panel Y", "UserPY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> clickGuiAlpha = new Numbers<Double>("Alpha", "Alpha", 1.0, 0.3, 1.0, 0.05);
	public static final Option<Boolean> glassBackground = new Option<Boolean>("Glass", "Glass", true);
	public static final Numbers<Double> webPort = new Numbers<Double>("Web Port", "WebPort", 18989.0, 1024.0, 65535.0, 1.0);

	public ClickGUI() {
		super("ClickGUI", Keyboard.KEY_RSHIFT, ModuleType.Render,"Open ClickGui");
		Chinese="点击GUI";
		setCustomPaletteVisibility();
		language.visibleWhen(() -> false);
		sakuraScale.visibleWhen(() -> guiStyle.getValue() == GuiStyle.SAKURA);
		this.addValues(guiStyle, palette, language,
				canvasRed, canvasGreen, canvasBlue, surfaceRed, surfaceGreen, surfaceBlue,
				accentRed, accentGreen, accentBlue, accentAltRed, accentAltGreen, accentAltBlue,
				dangerRed, dangerGreen, dangerBlue, playerRed, playerGreen, playerBlue,
				mobRed, mobGreen, mobBlue, animalRed, animalGreen, animalBlue,
				chestRed, chestGreen, chestBlue, enderChestRed, enderChestGreen, enderChestBlue,
				windowX, windowY, sakuraScale, sideStatsOffsetX, sideStatsOffsetY,
				sideSummaryOffsetX, sideSummaryOffsetY, sideDesignOffsetX, sideDesignOffsetY,
				moduleOffsetX, moduleOffsetY, detailOffsetX, detailOffsetY,
				sideOffsetX, sideOffsetY, userPanelOffsetX, userPanelOffsetY,
				clickGuiAlpha, glassBackground, webPort);
		// TODO Auto-generated constructor stub
	}

	public static ClientLanguage getLanguage() {
		ClientLanguage value = language.getValue();
		return value == null ? ClientLanguage.ENGLISH : value;
	}

	public static void setLanguage(ClientLanguage language) {
		ClickGUI.language.setValue(language == null ? ClientLanguage.ENGLISH : language);
	}

	public static String languageText(String english, String chinese) {
		return getLanguage().select(english, chinese);
	}

	public static VisualPalette currentPalette() {
		Palette value = palette.getValue();
		if (value == Palette.CUSTOM) {
			return VisualPalette.custom(VisualPalette.nightBloom(), rgb(canvasRed, canvasGreen, canvasBlue),
					rgb(surfaceRed, surfaceGreen, surfaceBlue), rgb(accentRed, accentGreen, accentBlue),
					rgb(accentAltRed, accentAltGreen, accentAltBlue), rgb(dangerRed, dangerGreen, dangerBlue),
					rgb(playerRed, playerGreen, playerBlue), rgb(mobRed, mobGreen, mobBlue),
					rgb(animalRed, animalGreen, animalBlue), rgb(chestRed, chestGreen, chestBlue),
					rgb(enderChestRed, enderChestGreen, enderChestBlue));
		}
		if (value == Palette.SAKURA) {
			return VisualPalette.sakura();
		}
		if (value == Palette.OCEAN) {
			return VisualPalette.ocean();
		}
		if (value == Palette.GRAPHITE) {
			return VisualPalette.graphite();
		}
		return VisualPalette.nightBloom();
	}

	public static boolean isLightPalette() {
		return palette.getValue() == Palette.SAKURA;
	}

	private static Numbers<Double> color(String displayName, String name, double value) {
		return new Numbers<Double>(displayName, name, value, 0.0, 255.0, 1.0);
	}

	private void setCustomPaletteVisibility() {
		Numbers<Double>[] values = new Numbers[]{
				canvasRed, canvasGreen, canvasBlue, surfaceRed, surfaceGreen, surfaceBlue,
				accentRed, accentGreen, accentBlue, accentAltRed, accentAltGreen, accentAltBlue,
				dangerRed, dangerGreen, dangerBlue, playerRed, playerGreen, playerBlue,
				mobRed, mobGreen, mobBlue, animalRed, animalGreen, animalBlue,
				chestRed, chestGreen, chestBlue, enderChestRed, enderChestGreen, enderChestBlue
		};
		for (Numbers<Double> value : values) {
			value.visibleWhen(() -> palette.getValue() == Palette.CUSTOM);
		}
	}

	private static int rgb(Numbers<Double> red, Numbers<Double> green, Numbers<Double> blue) {
		return 0xFF000000 | channel(red) << 16 | channel(green) << 8 | channel(blue);
	}

	private static int channel(Numbers<Double> value) {
		return Math.max(0, Math.min(255, value.getValue().intValue()));
	}

	public void toggle() {
		if (guiStyle.getValue() == GuiStyle.SAKURA) {
			mc.displayGuiScreen(new SakuraClickGui());
		} else if (guiStyle.getValue() == GuiStyle.YOZAKURA) {
			mc.displayGuiScreen(new YozakuraClickGui());
		} else if (guiStyle.getValue() == GuiStyle.WEB) {
			WebClickGuiService.open();
		} else {
			MaterialClickGui.warmResources();
			mc.displayGuiScreen(new MaterialClickGui());
		}
		this.setState(false);
	}

}
