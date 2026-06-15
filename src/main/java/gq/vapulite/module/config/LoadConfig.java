package gq.vapulite.module.config;

import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.Helper;

import java.io.IOException;

import static org.lwjgl.input.Keyboard.KEY_X;

public class LoadConfig extends Module {
    public LoadConfig() {
        super("LoadConfig", KEY_X, ModuleType.Config,"Load your configs");
        Chinese="加载配置";
        NoToggle=true;
    }

    public void enable() {
        try {
            Client.LoadConfig();
        } catch (IOException e) {
            e.printStackTrace();
            state=false;
        }
        Helper.sendMessage("Configs Loaded.");
        state=false;
    }
}
