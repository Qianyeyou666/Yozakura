package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.font.FontLoaders;

public final class ClickGuiIcons {
    private ClickGuiIcons() {
    }

    public static String forModule(Module module) {
        if (module == null) {
            return FontLoaders.ICON_LIST;
        }
        String name = module.getName() == null ? "" : module.getName().replace(" ", "").toLowerCase();
        if (name.contains("click") || name.contains("hud") || name.contains("display")) {
            return FontLoaders.ICON_SETTINGS;
        }
        if (name.contains("aura") || name.contains("assist") || name.contains("crit") || name.contains("velocity")
                || name.contains("reach") || name.contains("bot")) {
            return FontLoaders.ICON_BOMB;
        }
        if (name.contains("speed") || name.contains("sprint") || name.contains("fly") || name.contains("step")
                || name.contains("strafe")) {
            return FontLoaders.ICON_MOVEMENT;
        }
        if (name.contains("esp") || name.contains("render") || name.contains("name") || name.contains("visual")
                || name.contains("health")) {
            return FontLoaders.ICON_EYE;
        }
        if (name.contains("config") || name.contains("save") || name.contains("load")) {
            return FontLoaders.ICON_SETTINGS;
        }
        return forType(module.getCategory());
    }

    public static String forType(ModuleType type) {
        if (type == ModuleType.Combat) {
            return FontLoaders.ICON_BOMB;
        }
        if (type == ModuleType.Movement) {
            return FontLoaders.ICON_MOVEMENT;
        }
        if (type == ModuleType.Render) {
            return FontLoaders.ICON_EYE;
        }
        if (type == ModuleType.Player) {
            return FontLoaders.ICON_PERSON;
        }
        if (type == ModuleType.World) {
            return FontLoaders.ICON_INFO;
        }
        if (type == ModuleType.Config) {
            return FontLoaders.ICON_SETTINGS;
        }
        return FontLoaders.ICON_LIST;
    }
}
