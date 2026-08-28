package gq.yozakura.module.render;

/** Compact Nymph-derived geometry for the Cool TargetHUD variant. */
final class CoolTargetHudLayout {
    static final float MINIMUM_WIDTH = 148.0F;
    static final float HEIGHT = 42.0F;
    static final float AVATAR_SIZE = 34.0F;
    static final float AVATAR_ADVANCE = 39.0F;
    static final float RADIUS = 8.0F;
    static final float HEALTH_BAR_HEIGHT = 5.0F;
    static final float HEALTH_NUMBER_RESERVE = 25.0F;

    private CoolTargetHudLayout() {
    }

    static float width(float nameWidth) {
        return Math.max(MINIMUM_WIDTH, AVATAR_ADVANCE + 12.0F + Math.max(0.0F, nameWidth));
    }

    static float healthBarWidth(float width) {
        return Math.max(0.0F, width - AVATAR_ADVANCE - HEALTH_NUMBER_RESERVE - 13.0F);
    }
}
