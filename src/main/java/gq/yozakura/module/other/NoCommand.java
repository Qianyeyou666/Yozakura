//TODO: NoCommand
package gq.yozakura.module.other;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class NoCommand extends Module {
    public NoCommand() {
        super("NoCommand", Keyboard.KEY_NONE, ModuleType.Other, "No command.");
        Chinese=("没有指令");
    }

}
