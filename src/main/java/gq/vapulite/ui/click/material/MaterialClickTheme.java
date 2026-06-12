package gq.vapulite.ui.click.material;

/**
 * MaterialClickGui 的颜色与透明度工具。
 *
 * <p>这里固定参考 HTML 的 MD3 紫色调色板，同时保留运行时 alpha，
 * 让 ClickGUI 模块里的透明度配置可以继续生效。</p>
 */
final class MaterialClickTheme {
    static final int PRIMARY = 0xFFD0BCFF;
    static final int ON_PRIMARY = 0xFF381E72;
    static final int PRIMARY_CONTAINER = 0xFF4F378B;
    static final int ON_PRIMARY_CONTAINER = 0xFFEADDFF;
    static final int TEXT = 0xFFE6E1E5;
    static final int MUTED = 0xFFCAC4D0;
    static final int SURFACE = 0xFF141218;
    static final int SURFACE_VARIANT = 0xFF49454F;
    static final int CARD_SURFACE = 0xFF101010;
    static final int CARD_HOVER = 0xFF181818;
    static final int ACTIVE_CARD = 0xFF46317C;
    static final int BACKDROP = 0xFF0B080F;
    static final int OUTLINE = 0xFFFFFFFF;

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
        if (active) {
            return withAlpha(blend(ACTIVE_CARD, PRIMARY, hover * 0.08f), (118.0f + hover * 16.0f) * alpha);
        }
        if (hover > 0.0f) {
            return withAlpha(CARD_HOVER, 112.0f * alpha);
        }
        return withAlpha(CARD_SURFACE, 104.0f * alpha);
    }

    int cardBorder(boolean active, float hover) {
        if (active) {
            return withAlpha(PRIMARY, 78.0f * alpha);
        }
        return withAlpha(hover > 0.0f ? PRIMARY : OUTLINE, (hover > 0.0f ? 32.0f : 12.0f) * alpha);
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
