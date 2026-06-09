package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.font.FontLoaders;

enum GuiTab {
    COMBAT("Combat", FontLoaders.ICON_SWORDS, ModuleType.Combat),
    MOVEMENT("Movement", FontLoaders.ICON_RUN, ModuleType.Movement),
    VISUAL("Visual", FontLoaders.ICON_EYE, ModuleType.Render),
    UTILITY("Utility", FontLoaders.ICON_SETTINGS, ModuleType.Config),
    WORLD("World", FontLoaders.ICON_GLOBE, ModuleType.World),
    MISC("Misc", FontLoaders.ICON_LIST, ModuleType.Other),
    PLAYER("Profiles", FontLoaders.ICON_USER, ModuleType.Player);

    final String title;
    final String icon;
    private final ModuleType[] types;

    GuiTab(String title, String icon, ModuleType... types) {
        this.title = title;
        this.icon = icon;
        this.types = types;
    }

    boolean contains(ModuleType type) {
        for (ModuleType moduleType : types) {
            if (moduleType == type) {
                return true;
            }
        }
        return false;
    }
}
