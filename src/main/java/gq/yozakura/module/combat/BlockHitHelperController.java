package gq.yozakura.module.combat;

/**
 * Pure tick state for automatic Helper blocking and Leader-Lite attack timing.
 * Input binding changes are deliberately kept outside this class.
 */
final class BlockHitHelperController {
    private static final Action IDLE = new Action(false, false, false, false);
    private static final Action WARM_UP = new Action(false, false, false, true);
    private static final Action HOLD = new Action(true, false, false, true);

    private boolean active;
    private boolean helping;
    private boolean firstAttackWarmUpPending;
    private int elapsedTicks;

    Action tick(boolean attackDown, boolean activationAllowed, boolean blockingEstablished,
                int configuredStopTicks) {
        if (!activationAllowed || !attackDown) {
            reset();
            return IDLE;
        }
        if (!active) {
            active = true;
            if (attackDown && firstAttackWarmUpPending) {
                firstAttackWarmUpPending = false;
                return WARM_UP;
            }
        }

        int stopTicks = Math.max(1, Math.min(5, configuredStopTicks));
        if (!helping) {
            if (!blockingEstablished) {
                return HOLD;
            }
            helping = true;
            elapsedTicks = 1;
            return new Action(false, true, false, true);
        }

        elapsedTicks++;
        boolean pressAttack = elapsedTicks == 2;
        if (elapsedTicks > stopTicks) {
            helping = false;
            elapsedTicks = 0;
            return new Action(true, false, pressAttack, true);
        }
        return new Action(false, true, pressAttack, true);
    }

    boolean isActive() {
        return active;
    }

    boolean isHelping() {
        return helping;
    }

    void armFirstAttackWarmUp() {
        firstAttackWarmUpPending = true;
    }

    boolean reset() {
        boolean wasActive = active || helping;
        active = false;
        helping = false;
        elapsedTicks = 0;
        return wasActive;
    }

    static final class Action {
        private final boolean holdUse;
        private final boolean suppressUse;
        private final boolean pressAttack;
        private final boolean forceBlockPose;

        private Action(boolean holdUse, boolean suppressUse, boolean pressAttack, boolean forceBlockPose) {
            this.holdUse = holdUse;
            this.suppressUse = suppressUse;
            this.pressAttack = pressAttack;
            this.forceBlockPose = forceBlockPose;
        }

        boolean shouldHoldUse() {
            return holdUse;
        }

        boolean shouldSuppressUse() {
            return suppressUse;
        }

        boolean shouldPressAttack() {
            return pressAttack;
        }

        boolean shouldForceBlockPose() {
            return forceBlockPose;
        }
    }
}
