package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.VapeClickGui.VapeClickGui;
import org.lwjgl.input.Keyboard;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Numbers;

public class ClickGUI extends Module {
	public static final Numbers<Double> windowX = new Numbers<Double>("Window X", "WindowX", -1.0, -1.0, 2000.0, 1.0);
	public static final Numbers<Double> windowY = new Numbers<Double>("Window Y", "WindowY", -1.0, -1.0, 1200.0, 1.0);
	public static final Numbers<Double> sideStatsOffsetX = new Numbers<Double>("Stats X", "StatsX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideStatsOffsetY = new Numbers<Double>("Stats Y", "StatsY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideSummaryOffsetX = new Numbers<Double>("Info X", "InfoX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideSummaryOffsetY = new Numbers<Double>("Info Y", "InfoY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideDesignOffsetX = new Numbers<Double>("Design X", "DesignX", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> sideDesignOffsetY = new Numbers<Double>("Design Y", "DesignY", 0.0, -600.0, 600.0, 1.0);
	public static final Numbers<Double> clickGuiAlpha = new Numbers<Double>("Alpha", "Alpha", 1.0, 0.3, 1.0, 0.05);

	public ClickGUI() {
		super("ClickGUI", Keyboard.KEY_RSHIFT, ModuleType.Render,"Open ClickGui");
		Chinese="点击GUI";
		this.addValues(windowX, windowY, sideStatsOffsetX, sideStatsOffsetY,
				sideSummaryOffsetX, sideSummaryOffsetY, sideDesignOffsetX, sideDesignOffsetY, clickGuiAlpha);
		// TODO Auto-generated constructor stub
	}
	
	public void toggle() {
		mc.displayGuiScreen(new VapeClickGui());
		this.setState(false);
	}

}
