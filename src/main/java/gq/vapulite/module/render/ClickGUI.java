package gq.vapulite.module.render;

import gq.vapulite.ui.click.material.MaterialClickGui;
import gq.vapulite.ui.click.sakura.SakuraClickGui;
import gq.vapulite.ui.click.vape.VapeClickGui;
import org.lwjgl.input.Keyboard;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;

public class ClickGUI extends Module {
	public enum GuiStyle {
		MATERIAL,
		VAPE,
		SAKURA
	}

	public static final Mode<GuiStyle> guiStyle = new Mode<GuiStyle>("Style", "Style", GuiStyle.values(), GuiStyle.MATERIAL);
	public static final Numbers<Double> windowX = new Numbers<Double>("Window X", "WindowX", -1.0, -1.0, 2000.0, 1.0);
	public static final Numbers<Double> windowY = new Numbers<Double>("Window Y", "WindowY", -1.0, -1.0, 1200.0, 1.0);
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

	public ClickGUI() {
		super("ClickGUI", Keyboard.KEY_RSHIFT, ModuleType.Render,"Open ClickGui");
		Chinese="点击GUI";
		this.addValues(guiStyle, windowX, windowY, sideStatsOffsetX, sideStatsOffsetY,
				sideSummaryOffsetX, sideSummaryOffsetY, sideDesignOffsetX, sideDesignOffsetY,
				moduleOffsetX, moduleOffsetY, detailOffsetX, detailOffsetY,
				sideOffsetX, sideOffsetY, userPanelOffsetX, userPanelOffsetY,
				clickGuiAlpha, glassBackground);
		// TODO Auto-generated constructor stub
	}

	public void toggle() {
		if (guiStyle.getValue() == GuiStyle.SAKURA) {
			mc.displayGuiScreen(new SakuraClickGui());
		} else if (guiStyle.getValue() == GuiStyle.VAPE) {
			mc.displayGuiScreen(new VapeClickGui());
		} else {
			MaterialClickGui.warmResources();
			mc.displayGuiScreen(new MaterialClickGui());
		}
		this.setState(false);
	}

}
