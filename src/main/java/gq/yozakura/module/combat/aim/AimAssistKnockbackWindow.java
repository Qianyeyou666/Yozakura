package gq.yozakura.module.combat.aim;

/** Tracks combat-caused airtime and the short landing recovery window. */
public final class AimAssistKnockbackWindow {
    private static final int LANDING_RECOVERY_TICKS = 3;
    private static final double VERTICAL_MOTION_EPSILON = 1.0E-4D;

    private boolean localDamageArmed;
    private boolean targetDamageArmed;
    private boolean airborneCombat;
    private int landingRecoveryTicks;

    public void update(boolean localGrounded, int localHurtTime,
                       boolean targetGrounded, int targetHurtTime) {
        update(localGrounded, localHurtTime, 0.0D,
                targetGrounded, targetHurtTime, 0.0D);
    }

    public void update(boolean localGrounded, int localHurtTime, double localVerticalMotion,
                       boolean targetGrounded, int targetHurtTime, double targetVerticalMotion) {
        if (localHurtTime > 0) {
            localDamageArmed = true;
        }
        if (targetHurtTime > 0) {
            targetDamageArmed = true;
        }
        if (Math.abs(targetVerticalMotion) > VERTICAL_MOTION_EPSILON) {
            targetDamageArmed = true;
        }
        boolean localAirborne = !localGrounded || localHurtTime > 0
                && Math.abs(localVerticalMotion) > VERTICAL_MOTION_EPSILON;
        boolean targetAirborne = !targetGrounded || targetHurtTime > 0
                && Math.abs(targetVerticalMotion) > VERTICAL_MOTION_EPSILON;
        boolean damagedAirborne = localAirborne && localDamageArmed
                || targetAirborne && targetDamageArmed;
        if (damagedAirborne) {
            airborneCombat = true;
            landingRecoveryTicks = 0;
            return;
        }
        if (airborneCombat) {
            airborneCombat = false;
            landingRecoveryTicks = LANDING_RECOVERY_TICKS;
            localDamageArmed = false;
            targetDamageArmed = false;
            return;
        }
        if (landingRecoveryTicks > 0) {
            landingRecoveryTicks--;
        }
        if (localHurtTime <= 0 && localGrounded) {
            localDamageArmed = false;
        }
        if (targetHurtTime <= 0 && targetGrounded) {
            targetDamageArmed = false;
        }
    }

    public boolean isActive() {
        return airborneCombat || landingRecoveryTicks > 0;
    }

    public void reset() {
        localDamageArmed = false;
        targetDamageArmed = false;
        airborneCombat = false;
        landingRecoveryTicks = 0;
    }
}
