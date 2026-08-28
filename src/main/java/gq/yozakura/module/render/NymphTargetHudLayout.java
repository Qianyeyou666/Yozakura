package gq.yozakura.module.render;

/** Pure geometry and color thresholds from Nymphilila's Strife TargetHUD. */
final class NymphTargetHudLayout {
    static final float MINIMUM_WIDTH = 130.0F;
    static final float HEIGHT = 37.0F;
    static final float AVATAR_SIZE = 33.0F;
    static final float AVATAR_ADVANCE = 32.0F;
    static final float ITEM_STRIDE = 16.0F;
    static final float RADIUS = 8.0F;

    private NymphTargetHudLayout() {
    }

    static float width(float measuredNameWidth) {
        return Math.max(MINIMUM_WIDTH, Math.max(0.0F, measuredNameWidth));
    }

    static float healthBarWidth(float width) {
        return Math.max(0.0F, width - AVATAR_ADVANCE - 5.0F);
    }

    static int healthColor(float ratio) {
        float value = clamp01(ratio);
        int color = 0xFF00FF00;
        if (value < 0.5F) {
            color = 0xFFF0FF00;
        }
        if (value < 1.0F / 3.0F) {
            color = 0xFFFFC800;
        }
        if (value < 0.25F) {
            color = 0xFFFF0000;
        }
        return color;
    }

    static float itemX(float contentX, int slot, boolean armor) {
        return contentX + (armor ? 2.5F : 5.0F) + Math.max(0, slot) * ITEM_STRIDE;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
