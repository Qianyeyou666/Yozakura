package gq.yozakura.module.config;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.ui.click.ConfigProfileScreen;
import org.lwjgl.input.Keyboard;

public final class ConfigProfiles extends Module {
    public ConfigProfiles() {
        super("cfgmanager", Keyboard.KEY_NONE, ModuleType.Config, "Manage shareable .yzk config profiles");
        Chinese = "配置档案";
        NoToggle = true;
    }

    @Override
    public void enable() {
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                mc.displayGuiScreen(new ConfigProfileScreen(mc.currentScreen));
            }
        });
        state = false;
    }
}
