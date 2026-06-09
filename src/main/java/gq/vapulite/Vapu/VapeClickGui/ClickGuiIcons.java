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
        String name = normalize(module.getName());

        if (name.contains("clickgui")) {
            return FontLoaders.ICON_SETTINGS;
        }
        if (name.contains("keyboard") || name.contains("hud") || name.contains("targethud")) {
            return FontLoaders.ICON_LIST;
        }
        if (name.contains("state") || name.contains("message")) {
            return FontLoaders.ICON_INFO;
        }
        if (name.contains("fullbright")) {
            return FontLoaders.ICON_SUN;
        }
        if (name.contains("storage")) {
            return FontLoaders.ICON_FOLDER;
        }
        if (name.contains("esp") || name.contains("render")) {
            return FontLoaders.ICON_EYE;
        }
        if (name.contains("health")) {
            return FontLoaders.ICON_HEARTBEAT;
        }

        if (name.contains("autotools")) {
            return FontLoaders.ICON_PICKAXE;
        }
        if (name.contains("save")) {
            return FontLoaders.ICON_SAVE;
        }
        if (name.contains("load")) {
            return FontLoaders.ICON_DOWNLOAD;
        }
        if (name.contains("copy") || name.contains("ign")) {
            return FontLoaders.ICON_EDIT;
        }
        if (name.contains("uninject")) {
            return FontLoaders.ICON_TRASH;
        }
        if (name.contains("chat")) {
            return FontLoaders.ICON_SCRIPT;
        }
        if (name.contains("timer")) {
            return FontLoaders.ICON_CLOCK;
        }
        if (name.contains("ltap") || name.contains("iq")) {
            return FontLoaders.ICON_USER;
        }

        if (name.contains("murder") || name.contains("server")) {
            return FontLoaders.ICON_WARNING;
        }
        if (name.contains("mlg") || name.contains("nofall")) {
            return FontLoaders.ICON_ARROW_DOWN;
        }
        if (name.contains("fastplace")) {
            return FontLoaders.ICON_CUBE;
        }

        if (name.contains("antibot")) {
            return FontLoaders.ICON_BUG;
        }
        if (name.contains("autoblock")) {
            return FontLoaders.ICON_SHIELD;
        }
        if (name.contains("hitbox")) {
            return FontLoaders.ICON_FOCUS;
        }
        if (name.contains("velocity")) {
            return FontLoaders.ICON_SHUFFLE;
        }
        if (name.contains("aim") || name.contains("bow")) {
            return FontLoaders.ICON_CROSSHAIR;
        }
        if (name.contains("aura") || name.contains("clicker") || name.contains("reach")
                || name.contains("crit")) {
            return FontLoaders.ICON_SWORDS;
        }
        if (name.contains("speed") || name.contains("sprint") || name.contains("fly") || name.contains("step")
                || name.contains("strafe") || name.contains("slow") || name.contains("invmove")
                || name.contains("spider")) {
            return FontLoaders.ICON_RUN;
        }
        if (name.contains("config") || name.contains("save") || name.contains("load")) {
            return FontLoaders.ICON_SETTINGS;
        }
        return forType(module.getCategory());
    }

    public static String forType(ModuleType type) {
        if (type == ModuleType.Combat) {
            return FontLoaders.ICON_SWORDS;
        }
        if (type == ModuleType.Movement) {
            return FontLoaders.ICON_RUN;
        }
        if (type == ModuleType.Render) {
            return FontLoaders.ICON_EYE;
        }
        if (type == ModuleType.Player) {
            return FontLoaders.ICON_USER;
        }
        if (type == ModuleType.World) {
            return FontLoaders.ICON_GLOBE;
        }
        if (type == ModuleType.Config) {
            return FontLoaders.ICON_SETTINGS;
        }
        return FontLoaders.ICON_LIST;
    }

    public static float visualOffsetX(String icon) {
        if (FontLoaders.ICON_BOMB.equals(icon)) {
            return -0.5f;
        }
        if (FontLoaders.ICON_MOVEMENT.equals(icon) || FontLoaders.ICON_SETTINGS.equals(icon)) {
            return 0.5f;
        }
        return 0.0f;
    }

    public static float visualOffsetY(String icon) {
        if (FontLoaders.ICON_INFO.equals(icon) || FontLoaders.ICON_CHECKMARK.equals(icon)
                || FontLoaders.ICON_XMARK.equals(icon)) {
            return -0.5f;
        }
        if (FontLoaders.ICON_BOMB.equals(icon) || FontLoaders.ICON_MOVEMENT.equals(icon)) {
            return 0.5f;
        }
        return 0.0f;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replace(" ", "").replace("-", "").replace("_", "").toLowerCase();
    }
}
