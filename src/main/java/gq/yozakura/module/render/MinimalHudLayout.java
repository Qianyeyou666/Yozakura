package gq.yozakura.module.render;

final class MinimalHudLayout {
    static final int TEXT_COLOR = 0xFFFFFFFF;
    static final int MUTED_COLOR = 0xFFD0D0D0;
    static final int BACKGROUND_COLOR = 0x78000000;
    static final float ROW_HEIGHT = 10.0F;
    static final float TEXT_GLOW_STRENGTH = 0.12F;

    private MinimalHudLayout() {
    }

    static float pixel(float value) {
        return Math.round(value);
    }

    static float contentWidth(int textWidth) {
        return Math.max(6.0F, textWidth + 6.0F);
    }

    static float listHeight(int rows) {
        return Math.max(1, rows) * ROW_HEIGHT + 2.0F;
    }

    static boolean usesUnicodeFallback(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 0xFF) {
                return true;
            }
        }
        return false;
    }
}
