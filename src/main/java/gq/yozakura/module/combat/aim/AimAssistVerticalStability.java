package gq.yozakura.module.combat.aim;

/**
 * Keeps pitch stable while the local player is airborne from a recent hit.
 * The state is advanced once per game tick, while rendering can query it many
 * times without consuming the landing recovery window too quickly.
 */
public final class AimAssistVerticalStability {
    private static final int LANDING_RECOVERY_TICKS = 3;

    private int previousHurtTime;
    private int landingRecoveryTicks;
    private boolean airborneAfterHit;
    private boolean wasGrounded = true;

    public void update(boolean grounded, int hurtTime) {
        int safeHurtTime = Math.max(0, hurtTime);
        if (safeHurtTime > previousHurtTime) {
            airborneAfterHit = !grounded;
        }

        if (!grounded) {
            if (safeHurtTime > 0) {
                airborneAfterHit = true;
            }
            landingRecoveryTicks = 0;
        } else if (!wasGrounded && airborneAfterHit) {
            landingRecoveryTicks = LANDING_RECOVERY_TICKS;
            airborneAfterHit = false;
        } else if (landingRecoveryTicks > 0) {
            landingRecoveryTicks--;
        } else if (safeHurtTime == 0) {
            airborneAfterHit = false;
        }

        previousHurtTime = safeHurtTime;
        wasGrounded = grounded;
    }

    public boolean shouldHoldPitch(boolean trackedPitchHitsBox) {
        return trackedPitchHitsBox && (airborneAfterHit || landingRecoveryTicks > 0);
    }

    public boolean isActive() {
        return airborneAfterHit || landingRecoveryTicks > 0;
    }

    public void reset() {
        previousHurtTime = 0;
        landingRecoveryTicks = 0;
        airborneAfterHit = false;
        wasGrounded = true;
    }
}
