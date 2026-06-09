package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ClickUi.ClickUi;
import gq.vapulite.Vapu.VapeClickGui.VapeClickGui;
import org.lwjgl.input.Keyboard;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Numbers;

public class ClickGUI extends Module {
	public static final Numbers<Double> windowX = new Numbers<Double>("Window X", "WindowX", -1.0, -1.0, 2000.0, 1.0);
	public static final Numbers<Double> windowY = new Numbers<Double>("Window Y", "WindowY", -1.0, -1.0, 1200.0, 1.0);

	public ClickGUI() {
		super("ClickGUI", Keyboard.KEY_RSHIFT, ModuleType.Render,"Open ClickGui");
		Chinese="点击GUI";
		this.addValues(windowX, windowY);
		// TODO Auto-generated constructor stub
	}
	
	public void toggle() {
		mc.displayGuiScreen(new VapeClickGui());
		this.setState(false);
	}

}
