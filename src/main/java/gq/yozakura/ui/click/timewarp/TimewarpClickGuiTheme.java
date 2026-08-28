package gq.yozakura.ui.click.timewarp;

import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.module.render.ClickGUI;

/** Semantic Timewarp colors resolved from the live ClickGUI palette every frame. */
public final class TimewarpClickGuiTheme {
    private final VisualPalette palette;

    private TimewarpClickGuiTheme(VisualPalette palette) {
        this.palette = palette;
    }

    public static TimewarpClickGuiTheme current() {
        return new TimewarpClickGuiTheme(ClickGUI.currentPalette());
    }

    public int window() { return opaque(palette.getSurface()); }
    public int sidebar() { return opaque(palette.getSurfaceRaised()); }
    public int content() { return opaque(palette.getCanvas()); }
    public int card() { return opaque(palette.getSurfaceRaised()); }
    public int cardHover() { return opaque(palette.getSurfaceOverlay()); }
    public int control() { return opaque(palette.getSurfaceOverlay()); }
    public int text() { return opaque(palette.getTextPrimary()); }
    public int secondary() { return opaque(palette.getTextSecondary()); }
    public int muted() { return opaque(palette.getTextDisabled()); }
    public int accent() { return opaque(palette.getAccentPrimary()); }
    public int accentSoft() { return alpha(palette.getAccentSoft(), 220); }
    public int divider() { return opaque(palette.getBorderSubtle()); }
    public int danger() { return opaque(palette.getDanger()); }
    public int shadow(int alpha) { return alpha(palette.getShadow(), alpha); }

    public static int alpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    public static int blend(int from, int to, float progress) {
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int darken(int color, int amount) {
        int r = Math.max(0, ((color >>> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >>> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
