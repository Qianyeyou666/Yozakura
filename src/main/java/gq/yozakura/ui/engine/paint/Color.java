package gq.yozakura.ui.engine.paint;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * RGBA 颜色值对象：归一化 float 分量（0..1）+ packed ARGB int。
 *
 * <p>不可变。所有分量经钳制到 [0,1]。
 *
 * <p>解析支持：
 * <ul>
 *   <li>{@code #rgb} / {@code #rgba}（短十六进制，每位复制为两位）</li>
 *   <li>{@code #rrggbb} / {@code #rrggbbaa}（长十六进制）</li>
 *   <li>{@code rgb(r, g, b)}（0..255 整数）</li>
 *   <li>{@code rgba(r, g, b, a)}（a 为 0..1 float 或 0%..100%）</li>
 *   <li>命名色（red/white/black/transparent 等）</li>
 * </ul>
 *
 * <p>不静默降级：解析失败抛出 IllegalArgumentException，调用方必须处理。
 */
public final class Color {
    private final float r;
    private final float g;
    private final float b;
    private final float a;

    private Color(float r, float g, float b, float a) {
        this.r = clamp01(r);
        this.g = clamp01(g);
        this.b = clamp01(b);
        this.a = clamp01(a);
    }

    public static Color fromRgba(float r, float g, float b, float a) {
        return new Color(r, g, b, a);
    }

    /** 从 packed ARGB int 构造（高位 alpha，低位 blue）。 */
    public static Color fromPackedArgb(int packed) {
        float a = ((packed >> 24) & 0xFF) / 255f;
        float r = ((packed >> 16) & 0xFF) / 255f;
        float g = ((packed >> 8) & 0xFF) / 255f;
        float b = (packed & 0xFF) / 255f;
        return new Color(r, g, b, a);
    }

    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }
    public float a() { return a; }

    /** Packed ARGB int（高位 alpha，低位 blue），与 Minecraft / GL_UNSIGNED_BYTE 顺序一致。 */
    public int packedArgb() {
        int ai = Math.round(a * 255f) & 0xFF;
        int ri = Math.round(r * 255f) & 0xFF;
        int gi = Math.round(g * 255f) & 0xFF;
        int bi = Math.round(b * 255f) & 0xFF;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /** 乘法 alpha 预乘：返回新 Color，分量已乘以 alpha（用于预乘 alpha 管线）。 */
    public Color premultiplied() {
        return new Color(r * a, g * a, b * a, a);
    }

    /**
     * 解析 CSS 颜色字符串。
     *
     * @throws IllegalArgumentException 输入为 null/空/无法识别（不静默降级为黑色）
     */
    public static Color parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("color must not be null or empty");
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("color must not be empty after trim");
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) {
            return parseHex(s);
        }
        if (lower.startsWith("rgb(") && lower.endsWith(")")) {
            return parseRgbFunctional(s.substring(4, s.length() - 1), 1f);
        }
        if (lower.startsWith("rgba(") && lower.endsWith(")")) {
            return parseRgbaFunctional(s.substring(5, s.length() - 1));
        }
        Color named = NAMED_COLORS.get(lower);
        if (named != null) {
            return named;
        }
        throw new IllegalArgumentException("unsupported color syntax: " + raw);
    }

    private static Color parseHex(String s) {
        // s 形如 "#xxxx"
        String hex = s.substring(1);
        if (!isValidHex(hex)) {
            throw new IllegalArgumentException("invalid hex color: " + s);
        }
        switch (hex.length()) {
            case 3: {
                int r = hexDigit(hex, 0) * 17;
                int g = hexDigit(hex, 1) * 17;
                int b = hexDigit(hex, 2) * 17;
                return fromRgba(r / 255f, g / 255f, b / 255f, 1f);
            }
            case 4: {
                int r = hexDigit(hex, 0) * 17;
                int g = hexDigit(hex, 1) * 17;
                int b = hexDigit(hex, 2) * 17;
                int a = hexDigit(hex, 3) * 17;
                return fromRgba(r / 255f, g / 255f, b / 255f, a / 255f);
            }
            case 6: {
                int r = (hexDigit(hex, 0) << 4) | hexDigit(hex, 1);
                int g = (hexDigit(hex, 2) << 4) | hexDigit(hex, 3);
                int b = (hexDigit(hex, 4) << 4) | hexDigit(hex, 5);
                return fromRgba(r / 255f, g / 255f, b / 255f, 1f);
            }
            case 8: {
                int r = (hexDigit(hex, 0) << 4) | hexDigit(hex, 1);
                int g = (hexDigit(hex, 2) << 4) | hexDigit(hex, 3);
                int b = (hexDigit(hex, 4) << 4) | hexDigit(hex, 5);
                int a = (hexDigit(hex, 6) << 4) | hexDigit(hex, 7);
                return fromRgba(r / 255f, g / 255f, b / 255f, a / 255f);
            }
            default:
                throw new IllegalArgumentException("invalid hex color length: " + s);
        }
    }

    private static boolean isValidHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static int hexDigit(String s, int i) {
        char c = s.charAt(i);
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("invalid hex digit: " + c);
    }

    private static Color parseRgbFunctional(String body, float defaultAlpha) {
        // 形如 "255, 128, 0"
        String[] parts = body.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("rgb() expects 3 components: " + body);
        }
        int r = parseChannel8(parts[0]);
        int g = parseChannel8(parts[1]);
        int b = parseChannel8(parts[2]);
        return fromRgba(r / 255f, g / 255f, b / 255f, defaultAlpha);
    }

    private static Color parseRgbaFunctional(String body) {
        // 形如 "255, 0, 0, 0.5" 或 "255, 0, 0, 50%"
        String[] parts = body.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("rgba() expects 4 components: " + body);
        }
        int r = parseChannel8(parts[0]);
        int g = parseChannel8(parts[1]);
        int b = parseChannel8(parts[2]);
        float a = parseAlpha(parts[3]);
        return fromRgba(r / 255f, g / 255f, b / 255f, a);
    }

    private static int parseChannel8(String s) {
        String trimmed = s.trim();
        try {
            int v = Integer.parseInt(trimmed);
            if (v < 0 || v > 255) {
                throw new IllegalArgumentException("channel out of range 0..255: " + s);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid channel value: " + s, e);
        }
    }

    private static float parseAlpha(String s) {
        String trimmed = s.trim();
        if (trimmed.endsWith("%")) {
            try {
                float pct = Float.parseFloat(trimmed.substring(0, trimmed.length() - 1).trim());
                return pct / 100f;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid alpha percentage: " + s, e);
            }
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid alpha value: " + s, e);
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Color)) return false;
        Color c = (Color) o;
        // 用整数比较避免浮点误差
        return packedArgb() == c.packedArgb();
    }

    @Override
    public int hashCode() {
        return packedArgb();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Color(r=%.3f,g=%.3f,b=%.3f,a=%.3f)", r, g, b, a);
    }

    // ---- 命名色表 ----

    private static final Map<String, Color> NAMED_COLORS = new HashMap<String, Color>();
    static {
        NAMED_COLORS.put("transparent", fromRgba(0, 0, 0, 0));
        NAMED_COLORS.put("black", fromRgba(0, 0, 0, 1));
        NAMED_COLORS.put("white", fromRgba(1, 1, 1, 1));
        NAMED_COLORS.put("red", fromRgba(1, 0, 0, 1));
        NAMED_COLORS.put("green", fromRgba(0, 128f / 255f, 0, 1));
        NAMED_COLORS.put("lime", fromRgba(0, 1, 0, 1));
        NAMED_COLORS.put("blue", fromRgba(0, 0, 1, 1));
        NAMED_COLORS.put("yellow", fromRgba(1, 1, 0, 1));
        NAMED_COLORS.put("cyan", fromRgba(0, 1, 1, 1));
        NAMED_COLORS.put("aqua", fromRgba(0, 1, 1, 1));
        NAMED_COLORS.put("magenta", fromRgba(1, 0, 1, 1));
        NAMED_COLORS.put("gray", fromRgba(0.5f, 0.5f, 0.5f, 1));
        NAMED_COLORS.put("grey", fromRgba(0.5f, 0.5f, 0.5f, 1));
        NAMED_COLORS.put("silver", fromRgba(192f / 255f, 192f / 255f, 192f / 255f, 1));
        NAMED_COLORS.put("maroon", fromRgba(128f / 255f, 0, 0, 1));
        NAMED_COLORS.put("olive", fromRgba(128f / 255f, 128f / 255f, 0, 1));
        NAMED_COLORS.put("navy", fromRgba(0, 0, 128f / 255f, 1));
        NAMED_COLORS.put("teal", fromRgba(0, 128f / 255f, 128f / 255f, 1));
        NAMED_COLORS.put("purple", fromRgba(128f / 255f, 0, 128f / 255f, 1));
        NAMED_COLORS.put("orange", fromRgba(1, 165f / 255f, 0, 1));
        NAMED_COLORS.put("pink", fromRgba(1, 192f / 255f, 203f / 255f, 1));
        NAMED_COLORS.put("brown", fromRgba(165f / 255f, 42f / 255f, 42f / 255f, 1));
    }
}
