package gq.yozakura.module.combat.aim;

/** Decides when vertical assistance should yield to a pitch that already hits. */
public final class AimAssistVerticalPolicy {
    private static final double MOTION_EPSILON = 0.001D;

    private AimAssistVerticalPolicy() {
    }

    public static boolean shouldHoldPitch(boolean playerGrounded, boolean targetMoving,
                                           boolean trackedYawHitsBox) {
        return trackedYawHitsBox && isPitchHoldEligible(playerGrounded, targetMoving);
    }

    public static boolean isPitchHoldEligible(boolean playerGrounded, boolean targetMoving) {
        return playerGrounded || targetMoving;
    }

    public static boolean isMoving(double deltaX, double deltaY, double deltaZ) {
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > MOTION_EPSILON * MOTION_EPSILON;
    }
}
