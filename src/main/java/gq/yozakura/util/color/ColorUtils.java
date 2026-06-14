package gq.yozakura.util.color;

import java.awt.*;

public class ColorUtils {
    public static int rainbow(int delay){
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 20);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360f), 0.8f, 0.7f).getRGB();
    }

    public static int rainbow(int delay, int offset, int index) {
        int safeOffset = Math.max(1, offset);
        double rainbowState = Math.ceil(System.currentTimeMillis() + delay * index) / safeOffset;
        rainbowState %= 360.0;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 0.65f, 1.0f).getRGB();
    }

    public static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clamp(alpha, 0, 255) << 24);
    }

    public static int withAlpha(int color, float alpha) {
        return applyAlpha(color, Math.round(clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    public static int interpolate(int startColor, int endColor, float ratio) {
        float clamped = clamp(ratio, 0.0f, 1.0f);
        int a = (int) ((startColor >> 24 & 255) + ((endColor >> 24 & 255) - (startColor >> 24 & 255)) * clamped);
        int r = (int) ((startColor >> 16 & 255) + ((endColor >> 16 & 255) - (startColor >> 16 & 255)) * clamped);
        int g = (int) ((startColor >> 8 & 255) + ((endColor >> 8 & 255) - (startColor >> 8 & 255)) * clamped);
        int b = (int) ((startColor & 255) + ((endColor & 255) - (startColor & 255)) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int darken(int color, float factor) {
        float clamped = clamp(factor, 0.0f, 1.0f);
        int a = color >> 24 & 255;
        int r = Math.round((color >> 16 & 255) * (1.0f - clamped));
        int g = Math.round((color >> 8 & 255) * (1.0f - clamped));
        int b = Math.round((color & 255) * (1.0f - clamped));
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int lighten(int color, float factor) {
        float clamped = clamp(factor, 0.0f, 1.0f);
        int a = color >> 24 & 255;
        int r = (int) ((color >> 16 & 255) + (255 - (color >> 16 & 255)) * clamped);
        int g = (int) ((color >> 8 & 255) + (255 - (color >> 8 & 255)) * clamped);
        int b = (int) ((color & 255) + (255 - (color & 255)) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
