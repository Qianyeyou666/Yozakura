package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.ModuleType;

/** Keeps Epsilon's four visible rails while preserving VapuLite-only module categories. */
public final class EpsilonPanelCategories {
    private static final ModuleType[] VISIBLE = {
            ModuleType.Combat,
            ModuleType.Player,
            ModuleType.Movement,
            ModuleType.Render
    };

    private EpsilonPanelCategories() {
    }

    public static ModuleType[] visibleCategories() {
        return VISIBLE.clone();
    }

    public static ModuleType visibleCategory(ModuleType source) {
        if (source == ModuleType.World) {
            return ModuleType.Player;
        }
        if (source == ModuleType.Other || source == ModuleType.Config) {
            return ModuleType.Render;
        }
        return source == null ? ModuleType.Render : source;
    }

    public static boolean belongsTo(ModuleType source, ModuleType visible) {
        return visibleCategory(source) == visible;
    }
}
