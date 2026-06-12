package gq.vapulite.module.render;

import org.lwjgl.input.Keyboard;
import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;

public class StateMessage extends Module {
    public StateMessage() {
        super("NoStateMessage", Keyboard.KEY_NONE, ModuleType.Render,"Not Show Modules State info");
        Chinese="无开关信息";
    }

    public void enable() {
        Client.MessageON = true;
    }

    public void disable(){
        Client.MessageON = false;
    }
}
