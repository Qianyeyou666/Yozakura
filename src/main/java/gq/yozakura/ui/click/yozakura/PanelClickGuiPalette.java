package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.module.render.ClickGUI;

/** Semantic colors used by Panel so every ClickGUI palette affects the full surface. */
public final class PanelClickGuiPalette {
    private PanelClickGuiPalette() {
    }

    public static int canvas() { return alpha(ClickGUI.currentPalette().getCanvas(), 238); }
    public static int surface() { return alpha(ClickGUI.currentPalette().getSurface(), 240); }
    public static int raised() { return alpha(ClickGUI.currentPalette().getSurfaceRaised(), 244); }
    public static int overlay() { return alpha(ClickGUI.currentPalette().getSurfaceOverlay(), 248); }
    public static int textPrimary() { return ClickGUI.currentPalette().getTextPrimary(); }
    public static int textSecondary() { return ClickGUI.currentPalette().getTextSecondary(); }
    public static int textMuted() { return ClickGUI.currentPalette().getTextDisabled(); }
    public static int selected() { return alpha(ClickGUI.currentPalette().getAccentSoft(), 236); }
    public static int selectedText() { return ClickGUI.currentPalette().getAccentPrimary(); }
    public static int border() { return alpha(ClickGUI.currentPalette().getBorderSubtle(), 96); }
    public static int accent() { return ClickGUI.currentPalette().getAccentPrimary(); }
    public static int shadow(int alpha) { return alpha(ClickGUI.currentPalette().getShadow(), alpha); }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
