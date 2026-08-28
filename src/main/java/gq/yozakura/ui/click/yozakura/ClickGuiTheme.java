package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.render.ClickGUI;

/**
 * Nether v2.1 design tokens for the new ClickGUI.
 *
 * <p>Colors are packed as ARGB ints (alpha in the most-significant byte).
 * Surface elevations follow the layered depth system: bg &lt; sidebar &lt; card &lt; settings.
 * Accent values are pulled from the existing {@link ClickGUI#currentPalette()} so the
 * palette/custom-color system continues to drive the accent across the GUI.
 */
public final class ClickGuiTheme {
    public static final String BRAND_NAME = "Nether";
    public static final String BRAND_VERSION = "v2.1";
    public static final int DESIGN_ACCENT = 0xFF8B5CF6;
    // ===== Surfaces (layered elevation) =====
    // Epsilon Panel dark palette (MD3Theme), kept as ARGB for the LWJGL path.
    public static final int BG          = 0xFF0F0D13;
    public static final int SIDEBAR     = 0xF01D1B20;
    public static final int CARD        = 0xF0211F26;
    public static final int CARD_HOVER  = 0xF02B2930;
    public static final int SELECTED    = 0xEC4A4458;
    public static final int SETTINGS    = 0xF02A2830;

    // ===== Foreground =====
    public static final int FG          = 0xFFE6E0E9;
    public static final int FG_2        = 0xFFCAC4D0;
    public static final int FG_3        = 0xFF938F99;
    public static final int FG_4        = 0xFF5F5B63;

    // ===== Borders =====
    public static final int BORDER      = 0x10FFFFFF;
    public static final int BORDER_2    = 0x1AFFFFFF;

    // ===== Status =====
    public static final int GREEN       = 0xFF34D399;
    public static final int RED         = 0xFFF87171;

    // ===== Radii =====
    public static final float R_XS      = 7f;
    public static final float R_SM      = 9f;
    public static final float R_MD      = 13f;
    public static final float R_LG      = 17f;
    public static final float R_XL      = 17f;
    public static final float R_CAPSULE = 9f;

    // ===== Layout constants =====
    public static final int WINDOW_W    = 960;
    public static final int WINDOW_H    = 640;
    public static final int SIDEBAR_W   = 220;
    public static final int TITLEBAR_H  = 54;
    public static final int HEADER_H    = 56;
    public static final int CARD_H      = 56;
    public static final int CARD_PAD_X  = 16;

    // ===== Motion (linear approach speed per frame at 60fps baseline) =====
    public static final float SPRING_SPEED       = 0.22f;
    public static final float SPRING_BOUNCE_SPEED= 0.18f;
    public static final float EASE_OUT_SPEED     = 0.20f;
    public static final float EASE_QUICK_SPEED   = 0.30f;

    private ClickGuiTheme() {
    }

    // ===== Accent helpers (delegate to ClickGUI palette) =====

    public static int accent() {
        if (ClickGUI.palette.getValue() != ClickGUI.Palette.CUSTOM) {
            return DESIGN_ACCENT;
        }
        return ClickGUI.currentPalette().getAccentPrimary();
    }

    public static int accentHover() {
        return lighten(accent(), 0.12f);
    }

    public static int accentPressed() {
        return darken(accent(), 0.12f);
    }

    public static int accentSoft() {
        return withAlpha(accent(), 0x66);
    }

    public static int accentDim() {
        return withAlpha(accent(), 0x1F);
    }

    public static int accentGlow() {
        return withAlpha(accent(), 0x40);
    }

    /** ARGB accent with the supplied alpha (0..255). */
    public static int accentAlpha(int alpha) {
        return (alpha << 24) | (accent() & 0x00FFFFFF);
    }

    /** Linear gradient end color (slightly hue-shifted). */
    public static int accentGradientEnd() {
        return shiftHue(accent(), 0.08f);
    }

    // ===== Color utilities =====

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static int alphaOf(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int lighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.max(0, Math.round(r * (1f - amount)));
        g = Math.max(0, Math.round(g * (1f - amount)));
        b = Math.max(0, Math.round(b * (1f - amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int blend(int a, int b, float t) {
        float p = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * p) << 24)
                | (Math.round(ar + (br - ar) * p) << 16)
                | (Math.round(ag + (bg - ag) * p) << 8)
                | Math.round(ab + (bb - ab) * p);
    }

    /** Shifts the hue of an ARGB color by {@code amount} (0..1, fraction of full hue wheel). */
    public static int shiftHue(int color, float amount) {
        float[] hsv = rgbToHsv(color);
        hsv[0] = (hsv[0] + amount) % 1f;
        if (hsv[0] < 0f) {
            hsv[0] += 1f;
        }
        return hsvToArgb(hsv[0], hsv[1], hsv[2], alphaOf(color));
    }

    public static float[] rgbToHsv(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == rf) {
                h = ((gf - bf) / d) % 6f;
            } else if (max == gf) {
                h = (bf - rf) / d + 2f;
            } else {
                h = (rf - gf) / d + 4f;
            }
            h *= 60f;
            if (h < 0f) {
                h += 360f;
            }
        }
        float s = max == 0f ? 0f : d / max;
        return new float[]{h / 360f, s, max};
    }

    public static int hsvToArgb(float h, float s, float v, int alpha) {
        float r, g, b;
        int i = (int) Math.floor(h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = Math.round(r * 255f);
        int gi = Math.round(g * 255f);
        int bi = Math.round(b * 255f);
        return (alpha << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public static String toHex(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        StringBuilder sb = new StringBuilder(7);
        sb.append('#');
        appendHex(sb, r);
        appendHex(sb, g);
        appendHex(sb, b);
        return sb.toString().toUpperCase(java.util.Locale.ROOT);
    }

    public static int fromHex(String hex) {
        if (hex == null) {
            return accent();
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) {
            return accent();
        }
        try {
            int rgb = Integer.parseInt(s, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException ex) {
            return accent();
        }
    }

    private static void appendHex(StringBuilder sb, int value) {
        if (value < 16) {
            sb.append('0');
        }
        sb.append(Integer.toHexString(value));
    }

    /** Preset swatches shown in the color picker. */
    public static final int[] PRESET_COLORS = {
            0xFF8B5CF6, 0xFF06B6D4, 0xFFF43F5E, 0xFF10B981,
            0xFFF59E0B, 0xFF0EA5E9, 0xFFEC4899, 0xFFF97316
    };
}
