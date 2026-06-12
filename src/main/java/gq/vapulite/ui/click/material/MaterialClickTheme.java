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

    int cardFill(boolean active, float hover) {
        int base = active ? blend(SURFACE_VARIANT, PRIMARY_CONTAINER, 0.34f) : SURFACE_VARIANT;
        return withAlpha(blend(base, 0xFFFFFFFF, hover * 0.08f), (active ? 76.0f : 48.0f + hover * 20.0f) * alpha);
    }

    int cardBorder(boolean active, float hover) {
        return withAlpha(active ? PRIMARY : OUTLINE, (active ? 72.0f : 24.0f + hover * 38.0f) * alpha);
    }

    int softFill(float alpha) {
        return withAlpha(OUTLINE, alpha * this.alpha);
    }

    int text() {
        return withAlpha(TEXT, 255.0f * alpha);
    }

    int muted() {
        return withAlpha(MUTED, 210.0f * alpha);
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
