package gq.yozakura.ui.click.engine;

import java.util.Locale;

/** Immutable RGB/HSV conversion used by retained color-picker controls. */
public final class HsvColor {
    private final float hue;
    private final float saturation;
    private final float value;
    private final int red;
    private final int green;
    private final int blue;

    private HsvColor(float hue, float saturation, float value) {
        this.hue = wrap(hue);
        this.saturation = clamp(saturation);
        this.value = clamp(value);
        float chroma = this.value * this.saturation;
        float section = this.hue * 6.0F;
        float x = chroma * (1.0F - Math.abs(section % 2.0F - 1.0F));
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        int region = Math.min(5, (int) section);
        if (region == 0) { r = chroma; g = x; }
        else if (region == 1) { r = x; g = chroma; }
        else if (region == 2) { g = chroma; b = x; }
        else if (region == 3) { g = x; b = chroma; }
        else if (region == 4) { r = x; b = chroma; }
        else { r = chroma; b = x; }
        float match = this.value - chroma;
        this.red = Math.round((r + match) * 255.0F);
        this.green = Math.round((g + match) * 255.0F);
        this.blue = Math.round((b + match) * 255.0F);
    }

    public static HsvColor of(float hue, float saturation, float value) {
        return new HsvColor(hue, saturation, value);
    }

    /** x is saturation and y runs top-to-bottom from bright to black. */
    public static HsvColor fromPicker(float x, float y, float hue) {
        return new HsvColor(hue, clamp(x), 1.0F - clamp(y));
    }

    public static HsvColor fromRgb(int red, int green, int blue) {
        float r = channel(red);
        float g = channel(green);
        float b = channel(blue);
        float maximum = Math.max(r, Math.max(g, b));
        float minimum = Math.min(r, Math.min(g, b));
        float delta = maximum - minimum;
        float hue = 0.0F;
        if (delta > 0.000001F) {
            if (maximum == r) hue = ((g - b) / delta) % 6.0F;
            else if (maximum == g) hue = (b - r) / delta + 2.0F;
            else hue = (r - g) / delta + 4.0F;
            hue /= 6.0F;
            if (hue < 0.0F) hue += 1.0F;
        }
        float saturation = maximum <= 0.000001F ? 0.0F : delta / maximum;
        return new HsvColor(hue, saturation, maximum);
    }

    public float hue() { return hue; }
    public float saturation() { return saturation; }
    public float value() { return value; }
    public int red() { return red; }
    public int green() { return green; }
    public int blue() { return blue; }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    private static float channel(int value) {
        return Math.max(0, Math.min(255, value)) / 255.0F;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float wrap(float value) {
        float wrapped = value % 1.0F;
        return wrapped < 0.0F ? wrapped + 1.0F : wrapped;
    }
}
