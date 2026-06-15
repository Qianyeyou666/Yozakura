package gq.vapulite.module.render;

import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import org.lwjgl.input.Keyboard;

public class ChineseMode extends Module {
    public ChineseMode() {
        super("中文", Keyboard.KEY_NONE, ModuleType.Combat,"");
        Chinese="ChineseMode";
    }


    @Override
    public void enable(){
        Client.CHINESE = true;
        this.mc.thePlayer.closeScreen();
    }

    @Override
    public void disable(){
        Client.CHINESE = false;
        this.mc.thePlayer.closeScreen();
    }

}
