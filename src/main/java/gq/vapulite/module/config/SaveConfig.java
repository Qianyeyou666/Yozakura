package gq.vapulite.module.config;

import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.Helper;

import java.io.IOException;

import static org.lwjgl.input.Keyboard.KEY_N;

public class SaveConfig extends Module {
    public SaveConfig() {
        super("SaveConfig", KEY_N, ModuleType.Config,"Save your module setting(config)");
        Chinese="保存配置";
        NoToggle=true;
    }

    public void enable() {
        try {
            Client.SaveConfig();
        } catch (IOException e) {
            e.printStackTrace();
            state=false;
        }
        Helper.sendMessage("Configs Saved.");
        state=false;
    }
}
