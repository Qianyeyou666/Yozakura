package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.module.render.HUD;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Value;

/** Collapses each persisted custom-palette RGB triplet into one swatch row. */
public final class PanelPaletteColorControl {
    public static final float SWATCH_WIDTH = 28.0f;
    public static final float SWATCH_HEIGHT = 16.0f;
    public static final float SWATCH_TRAILING_INSET = 5.0f;

    public enum Group {
        CANVAS("UI Canvas", "CanvasRed", "CanvasGreen", "CanvasBlue"),
        SURFACE("UI Panels", "SurfaceRed", "SurfaceGreen", "SurfaceBlue"),
        ACCENT("UI Accent", "AccentRed", "AccentGreen", "AccentBlue"),
        ACCENT_ALT("UI Alt Accent", "AccentAltRed", "AccentAltGreen", "AccentAltBlue"),
        DANGER("Hurt / Low HP", "DangerRed", "DangerGreen", "DangerBlue"),
        PLAYER("Player", "PlayerRed", "PlayerGreen", "PlayerBlue"),
        MOB("Mob", "MobRed", "MobGreen", "MobBlue"),
        ANIMAL("Animal", "AnimalRed", "AnimalGreen", "AnimalBlue"),
        CHEST("Chest", "ChestRed", "ChestGreen", "ChestBlue"),
        ENDER_CHEST("Ender Chest", "EnderChestRed", "EnderChestGreen", "EnderChestBlue"),
        HUD_PRIMARY("HUD Primary", "NymphPrimaryRed", "NymphPrimaryGreen", "NymphPrimaryBlue"),
        HUD_SECONDARY("HUD Secondary", "NymphSecondaryRed", "NymphSecondaryGreen", "NymphSecondaryBlue"),
        HOTBAR_SELECTION("HotBar Selection", "SelectionRed", "SelectionGreen", "SelectionBlue"),
        DAMAGE_NUMBERS("Damage Numbers", "DamageRed", "DamageGreen", "DamageBlue");

        private final String label;
        private final String redName;
        private final String greenName;
        private final String blueName;

        Group(String label, String redName, String greenName, String blueName) {
            this.label = label;
            this.redName = redName;
            this.greenName = greenName;
            this.blueName = blueName;
        }

        public String label() { return label; }
        public Numbers<Double> red() { return channelValue(0); }
        public Numbers<Double> green() { return channelValue(1); }
        public Numbers<Double> blue() { return channelValue(2); }

        public int color() {
            return 0xFF000000 | channel(red()) << 16 | channel(green()) << 8 | channel(blue());
        }

        public void setChannel(int index, double value) {
            Numbers<Double> target = channelValue(index);
            if (target == null) {
                return;
            }
            target.setNumberValue(Math.max(0.0, Math.min(255.0, Math.round(value))));
            if (!isHudPalette()) {
                ClickGUI.palette.setValue(ClickGUI.Palette.CUSTOM);
            }
        }

        public boolean isHudPalette() {
            return this == HUD_PRIMARY || this == HUD_SECONDARY || this == HOTBAR_SELECTION;
        }

        private Numbers<Double> channelValue(int index) {
            switch (this) {
                case CANVAS:
                    return index == 0 ? ClickGUI.canvasRed : (index == 1 ? ClickGUI.canvasGreen : ClickGUI.canvasBlue);
                case SURFACE:
                    return index == 0 ? ClickGUI.surfaceRed : (index == 1 ? ClickGUI.surfaceGreen : ClickGUI.surfaceBlue);
                case ACCENT:
                    return index == 0 ? ClickGUI.accentRed : (index == 1 ? ClickGUI.accentGreen : ClickGUI.accentBlue);
                case ACCENT_ALT:
                    return index == 0 ? ClickGUI.accentAltRed : (index == 1 ? ClickGUI.accentAltGreen : ClickGUI.accentAltBlue);
                case DANGER:
                    return index == 0 ? ClickGUI.dangerRed : (index == 1 ? ClickGUI.dangerGreen : ClickGUI.dangerBlue);
                case PLAYER:
                    return index == 0 ? ClickGUI.playerRed : (index == 1 ? ClickGUI.playerGreen : ClickGUI.playerBlue);
                case MOB:
                    return index == 0 ? ClickGUI.mobRed : (index == 1 ? ClickGUI.mobGreen : ClickGUI.mobBlue);
                case ANIMAL:
                    return index == 0 ? ClickGUI.animalRed : (index == 1 ? ClickGUI.animalGreen : ClickGUI.animalBlue);
                case CHEST:
                    return index == 0 ? ClickGUI.chestRed : (index == 1 ? ClickGUI.chestGreen : ClickGUI.chestBlue);
                case ENDER_CHEST:
                    return index == 0 ? ClickGUI.enderChestRed
                            : (index == 1 ? ClickGUI.enderChestGreen : ClickGUI.enderChestBlue);
                case HUD_PRIMARY:
                    return HUD.nymphPaletteChannel(true, index);
                case HUD_SECONDARY:
                    return HUD.nymphPaletteChannel(false, index);
                case HOTBAR_SELECTION:
                    return moduleChannel("HotBar", index == 0 ? redName : (index == 1 ? greenName : blueName));
                case DAMAGE_NUMBERS:
                    return moduleChannel("DamageNumbers", index == 0 ? redName
                            : (index == 1 ? greenName : blueName));
                default:
                    return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static Numbers<Double> moduleChannel(String moduleName, String valueName) {
            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                return null;
            }
            for (Value<?> value : module.getValues()) {
                if (value instanceof Numbers && valueName.equals(value.getName())) {
                    return (Numbers<Double>) value;
                }
            }
            return null;
        }
    }

    private PanelPaletteColorControl() {
    }

    public static Group groupFor(Value<?> value) {
        return value == null ? null : groupForName(value.getName());
    }

    public static Group groupForName(String name) {
        if (name == null) {
            return null;
        }
        for (Group group : Group.values()) {
            if (group.redName.equals(name) || group.greenName.equals(name) || group.blueName.equals(name)) {
                return group;
            }
        }
        return null;
    }

    public static boolean isLeader(Value<?> value) {
        if (value == null) {
            return false;
        }
        Group group = groupForName(value.getName());
        return group != null && group.redName.equals(value.getName());
    }

    public static boolean isLeaderName(String name) {
        Group group = groupForName(name);
        return group != null && group.redName.equals(name);
    }

    public static PanelClickGuiLayout.Rect swatchBounds(PanelClickGuiLayout.Rect row) {
        return new PanelClickGuiLayout.Rect(
                row.right() - SWATCH_TRAILING_INSET - SWATCH_WIDTH,
                row.y() + (row.height() - SWATCH_HEIGHT) * 0.5f,
                SWATCH_WIDTH,
                SWATCH_HEIGHT);
    }

    private static int channel(Numbers<Double> value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(255, value.getValue().intValue()));
    }
}
