package gq.yozakura.module.render;

import gq.yozakura.core.Client;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
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
