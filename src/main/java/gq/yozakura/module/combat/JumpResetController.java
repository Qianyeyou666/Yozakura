package gq.yozakura.module.combat;

final class JumpResetController {
    enum JumpAction {
        NONE,
        PRESS,
        RELEASE
    }

    private static final int JUMP_TICKS = 2;
    private static final int FORWARD_TICKS = 3;

    private int ticksSinceVelocity = -1;
    private int chanceAccumulator;
    private boolean hurtConfirmed;
    private boolean jumpPressed;

    void observePlayerHurt() {
        hurtConfirmed = true;
    }

    boolean acceptVelocity(boolean requireHurtConfirmation) {
        return acceptVelocity(requireHurtConfirmation, 100);
    }

    boolean acceptVelocity(boolean requireHurtConfirmation, int chance) {
        if (requireHurtConfirmation && !hurtConfirmed) {
            return false;
        }
        hurtConfirmed = false;
        if (!shouldTrigger(chance)) {
            return false;
        }
        ticksSinceVelocity = 0;
        return true;
    }

    JumpAction advance(boolean onGround) {
        if (ticksSinceVelocity < 0) {
            return JumpAction.NONE;
        }

        ticksSinceVelocity++;
        if (ticksSinceVelocity <= JUMP_TICKS) {
            if (onGround) {
                jumpPressed = true;
                return JumpAction.PRESS;
            }
            return JumpAction.NONE;
        }
        if (ticksSinceVelocity == FORWARD_TICKS + 1) {
            return closeWindow();
        }
        return JumpAction.NONE;
    }

    boolean shouldForceForward() {
        return ticksSinceVelocity > 0 && ticksSinceVelocity <= FORWARD_TICKS;
    }

    JumpAction cancel() {
        return closeWindow();
    }

    void reset() {
        clearWindow();
        chanceAccumulator = 0;
        hurtConfirmed = false;
    }

    private boolean shouldTrigger(int chance) {
        int safeChance = Math.max(0, Math.min(100, chance));
        if (safeChance == 0) {
            return false;
        }
        if (safeChance == 100) {
            return true;
        }
        chanceAccumulator += safeChance;
        if (chanceAccumulator < 100) {
            return false;
        }
        chanceAccumulator -= 100;
        return true;
    }

    private JumpAction closeWindow() {
        boolean shouldRelease = jumpPressed;
        clearWindow();
        return shouldRelease ? JumpAction.RELEASE : JumpAction.NONE;
    }

    private void clearWindow() {
        ticksSinceVelocity = -1;
        jumpPressed = false;
    }
}
