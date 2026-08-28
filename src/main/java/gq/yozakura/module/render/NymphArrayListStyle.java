package gq.yozakura.module.render;

import java.awt.Color;

/**
 * Deterministic ArrayList styling rules ported from Nymphilila's OverlayModule.
 * Rendering stays in HUD so this class can be tested without a Minecraft or GL context.
 */
final class NymphArrayListStyle {
    static final int FONT_SIZE = 19;
    static final float ROW_HEIGHT = 11.0F;

    enum ColorMode {
        ASTOLFO,
        PULSE,
        RAINBOW,
        STATIC,
        SWITCH
    }

    private NymphArrayListStyle() {
    }

    static int colorAt(ColorMode mode, int primary, int secondary, int rowOffset, long nowMillis) {
        ColorMode resolved = mode == null ? ColorMode.PULSE : mode;
        switch (resolved) {
            case ASTOLFO:
                return astolfo(nowMillis, rowOffset);
            case RAINBOW:
                return rainbow(nowMillis, rowOffset);
            case SWITCH:
                return switchColor(primary, secondary, rowOffset, nowMillis);
            case STATIC:
                return opaque(primary);
            case PULSE:
            default:
                return pulse(primary, rowOffset, nowMillis);
        }
    }

    static float animatedTextX(float visibleX, float textWidth, float screenWidth,
                               float visibility, boolean rightSide) {
        float progress = clamp01(visibility);
        float hiddenX = rightSide ? Math.max(0.0F, screenWidth) : -Math.max(0.0F, textWidth);
        return hiddenX + (visibleX - hiddenX) * progress;
    }

    private static int pulse(int color, int rowOffset, long nowMillis) {
        float red = (color >>> 16 & 255) / 255.0F;
        float green = (color >>> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float delta = maximum - minimum;
        float saturation = maximum == 0.0F ? 0.0F : delta / maximum;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (maximum == red) {
            hue = (green - blue) / delta / 6.0F;
        } else if (maximum == green) {
            hue = (2.0F + (blue - red) / delta) / 6.0F;
        } else {
            hue = (4.0F + (red - green) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float phase = positiveModulo(nowMillis, 2000L) / 1000.0F - rowOffset / 8000.0F;
        float brightness = 0.5F + 0.5F * Math.abs(positiveModulo(phase, 2.0F) - 1.0F);
        return opaque(Color.HSBtoRGB(hue, saturation, brightness));
    }

    private static int rainbow(long nowMillis, int rowOffset) {
        float hue = positiveModulo(nowMillis + rowOffset, 4000L) / 4000.0F;
        return opaque(Color.HSBtoRGB(hue, 0.4F, 0.8F));
    }

    private static int astolfo(long nowMillis, int rowOffset) {
        float hue = positiveModulo(nowMillis + rowOffset, 3000L) / 3000.0F;
        if (hue > 0.5F) {
            hue = 1.0F - hue;
        }
        hue += 0.5F;
        return opaque(Color.HSBtoRGB(hue, 0.5F, 1.0F));
    }

    private static int switchColor(int first, int second, int rowOffset, long nowMillis) {
        long shifted = nowMillis * 2L + rowOffset * 75L;
        float phase = positiveModulo(shifted, 4000L) / 2000.0F;
        if (phase <= 1.0F) {
            return blend(first, second, phase);
        }
        return blend(second, first, phase - 1.0F);
    }

    private static int blend(int start, int end, float amount) {
        float value = clamp01(amount);
        int red = Math.round((start >>> 16 & 255) + ((end >>> 16 & 255) - (start >>> 16 & 255)) * value);
        int green = Math.round((start >>> 8 & 255) + ((end >>> 8 & 255) - (start >>> 8 & 255)) * value);
        int blue = Math.round((start & 255) + ((end & 255) - (start & 255)) * value);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0.0F ? result + divisor : result;
    }

    private static long positiveModulo(long value, long divisor) {
        long result = value % divisor;
        return result < 0L ? result + divisor : result;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
