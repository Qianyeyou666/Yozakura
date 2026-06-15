package gq.yozakura.module.config;

import gq.yozakura.core.Client;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;

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
            Helper.sendMessage("Configs Saved.");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            Helper.sendMessage("Config save failed. Check YozakuraConfig.log.");
            state=false;
        }
        state=false;
    }
}
