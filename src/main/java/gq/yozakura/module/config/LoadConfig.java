package gq.yozakura.module.config;

import gq.yozakura.core.Client;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;

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
            Helper.sendMessage("Configs Loaded.");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            Helper.sendMessage("Config load failed. Check VapuLiteConfig.log.");
            state=false;
        }
        state=false;
    }
}
