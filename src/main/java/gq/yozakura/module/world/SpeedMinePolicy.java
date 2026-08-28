package gq.yozakura.module.world;

/** Pure normalization and progress rules for SpeedMine. */
final class SpeedMinePolicy {
    private static final double MIN_SPEED = 1.0D;
    private static final double MAX_SPEED = 5.0D;
    private static final float DEFAULT_FINISH_THRESHOLD = 0.70F;
    private static final float MIN_FINISH_THRESHOLD = 0.50F;
    private static final float MAX_FINISH_THRESHOLD = 0.95F;

    private SpeedMinePolicy() {
    }

    static float extraDamage(boolean hittingBlock, float relativeHardness, Object speedValue) {
        if (!hittingBlock || relativeHardness <= 0.0F || relativeHardness >= 1.0F) {
            return 0.0F;
        }
        return relativeHardness * (float) (normalizeSpeed(speedValue) - 1.0D);
    }

    static boolean shouldFinish(boolean hittingBlock, float damage, float threshold, float relativeHardness) {
        return hittingBlock
                && relativeHardness > 0.0F
                && relativeHardness < 1.0F
                && damage >= threshold;
    }

    static double normalizeSpeed(Object value) {
        if (!(value instanceof Number)) {
            return MIN_SPEED;
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return MIN_SPEED;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, number));
    }

    static float normalizeFinishThreshold(Object value) {
        if (!(value instanceof Number)) {
            return DEFAULT_FINISH_THRESHOLD;
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return DEFAULT_FINISH_THRESHOLD;
        }
        return (float) Math.max(MIN_FINISH_THRESHOLD, Math.min(MAX_FINISH_THRESHOLD, number));
    }
}
