package gq.yozakura.module.combat;

/** Pure threat decision used by Hypixel before it acquires the use binding. */
final class BlockHitHelperThreatPredictor {
    private static final double MIN_CLOSING_SPEED = 0.08D;

    private BlockHitHelperThreatPredictor() {
    }

    static boolean isThreat(boolean validOpponent, boolean visible, boolean armed, boolean swinging,
                            double distance, double maximumDistance, double facingDifference,
                            double maximumFacingDifference, double closingSpeed) {
        if (!validOpponent || !visible || distance > maximumDistance
                || facingDifference > maximumFacingDifference) {
            return false;
        }
        return swinging || armed && closingSpeed >= MIN_CLOSING_SPEED;
    }
}
