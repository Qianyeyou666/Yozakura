package gq.vapulite.Vapu.modules.Config;

import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.Helper;

import static org.lwjgl.input.Keyboard.KEY_NONE;

public class Uninject extends Module {
    public Uninject() {
        super("Uninject", KEY_NONE, ModuleType.Config,"Uninject "+Client.name);
        Chinese="卸载";
        NoToggle=true;
    }

    public void enable() {
        if (this.mc.thePlayer != null) {
            this.mc.thePlayer.closeScreen();
        }
        Helper.sendMessage("Uninject is disabled in this build to keep ClickGUI available.");
        state=false;
    }
}
