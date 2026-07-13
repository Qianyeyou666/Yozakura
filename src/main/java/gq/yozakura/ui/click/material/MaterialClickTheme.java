package gq.yozakura.ui.click.material;

import gq.yozakura.util.animation.AnimationUtil;
import gq.yozakura.engine.render.ui.VisualPalette;

/**
 * MaterialClickGui 的颜色与透明度工具。
 *
 * <p>这里固定参考 HTML 的 MD3 紫色调色板，同时保留运行时 alpha，
 * 让 ClickGUI 模块里的透明度配置可以继续生效。</p>
 */
final class MaterialClickTheme {
    static int PRIMARY = 0xFFD0BCFF;
    static int ON_PRIMARY = 0xFF381E72;
    static int PRIMARY_CONTAINER = 0xFF4F378B;
    static int ON_PRIMARY_CONTAINER = 0xFFEADDFF;
    static int TEXT = 0xFFE6E1E5;
    static int MUTED = 0xFFCAC4D0;
    static int SURFACE = 0xFF141218;
    static int SURFACE_VARIANT = 0xFF49454F;
    static int CARD_SURFACE = 0xFF101010;
    static int CARD_HOVER = 0xFF181818;
    static int ACTIVE_CARD = 0xFF46317C;
    static int BACKDROP = 0xFF0B080F;
    static int OUTLINE = 0xFFFFFFFF;

    static void applyPalette(VisualPalette palette) {
        if (palette == null) {
            return;
        }
        PRIMARY = palette.getAccentPrimary();
        ON_PRIMARY = palette.getSurface();
        PRIMARY_CONTAINER = palette.getSurfaceOverlay();
        ON_PRIMARY_CONTAINER = palette.getTextPrimary();
        TEXT = palette.getTextPrimary();
        MUTED = palette.getTextSecondary();
        SURFACE = palette.getSurface();
        SURFACE_VARIANT = palette.getSurfaceRaised();
        CARD_SURFACE = palette.getSurface();
        CARD_HOVER = palette.getSurfaceRaised();
        ACTIVE_CARD = palette.getSurfaceOverlay();
        BACKDROP = palette.getCanvas();
        OUTLINE = palette.getBorderSubtle();
    }

    private float alpha = 1.0f;

    void setAlpha(float alpha) {
        this.alpha = clamp(alpha, 0.0f, 1.0f);
    }

    float alpha() {
        return alpha;
    }

    int windowFill() {
        return withAlpha(SURFACE, 166.0f * alpha);
    }

    int windowScrim() {
        return withAlpha(0xFF000000, 84.0f * alpha);
    }

    int cardFill(boolean active, float hover) {
        return cardFill(active ? 1.0f : 0.0f, hover);
    }

    int cardFill(float active, float hover) {
        float h = clamp(hover, 0.0f, 1.0f);
        float a = clamp(active, 0.0f, 1.0f);
        int inactiveFill = blend(CARD_SURFACE, CARD_HOVER, h);
        int activeFill = blend(ACTIVE_CARD, PRIMARY, h * 0.08f);
        float inactiveAlpha = 104.0f + h * 8.0f;
        float activeAlpha = 118.0f + h * 16.0f;
        return withAlpha(blend(inactiveFill, activeFill, a), AnimationUtil.lerp(inactiveAlpha, activeAlpha, a) * alpha);
    }

    int cardBorder(boolean active, float hover) {
        return cardBorder(active ? 1.0f : 0.0f, hover);
    }

    int cardBorder(float active, float hover) {
        float h = clamp(hover, 0.0f, 1.0f);
        float a = clamp(active, 0.0f, 1.0f);
        int inactiveBorder = blend(OUTLINE, PRIMARY, h);
        float inactiveAlpha = 12.0f + h * 20.0f;
        return withAlpha(blend(inactiveBorder, PRIMARY, a), AnimationUtil.lerp(inactiveAlpha, 78.0f, a) * alpha);
    }

    int keybindFill() {
        return withAlpha(OUTLINE, 22.0f * alpha);
    }

    int softFill(float alpha) {
        return withAlpha(OUTLINE, alpha * this.alpha);
    }

    int text() {
        return withAlpha(TEXT, 255.0f * alpha);
    }

    int muted() {
        return withAlpha(MUTED, 255.0f * alpha);
    }

    int faint() {
        return withAlpha(MUTED, 122.0f * alpha);
    }

    int withAlpha(int color, float alpha) {
        int a = Math.round(clamp(alpha, 0.0f, 255.0f));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    int blend(int from, int to, float progress) {
        float p = clamp(progress, 0.0f, 1.0f);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * p);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * p);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * p);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
