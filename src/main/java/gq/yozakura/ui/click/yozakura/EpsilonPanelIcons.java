package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.ModuleType;

/** IconChars used by Epsilon Panel at commit 874e4b98849fa38853d152f2da461fd80c851c9d. */
public final class EpsilonPanelIcons {
    public static final String SWORDS = "\uf889";
    public static final String PERSON = "\uf0d3";
    public static final String DIRECTIONS_RUN = "\ue566";
    public static final String BRUSH = "\ue3ae";
    public static final String SETTINGS = "\ue8b8";
    public static final String CONFIG = "\ue2c7";
    public static final String SEARCH = "\ue8b6";
    public static final String CHECK = "\ue5ca";
    public static final String CHEVRON_LEFT = "\ue5cb";
    public static final String CHEVRON_RIGHT = "\ue5cc";
    public static final String EXPAND_LESS = "\ue5ce";
    public static final String EXPAND_MORE = "\ue5cf";

    private EpsilonPanelIcons() {
    }

    public static String category(ModuleType type) {
        ModuleType visible = EpsilonPanelCategories.visibleCategory(type);
        if (visible == ModuleType.Combat) {
            return SWORDS;
        }
        if (visible == ModuleType.Player) {
            return PERSON;
        }
        if (visible == ModuleType.Movement) {
            return DIRECTIONS_RUN;
        }
        return BRUSH;
    }
}
