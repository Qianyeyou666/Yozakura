package gq.yozakura.module.combat;

final class VelocityController {
    private int attackWindowTicks;
    private boolean pendingAttackSlowdown;
    private int reductionChanceAccumulator;

    synchronized void armAttackWindow(int timeoutTicks) {
        attackWindowTicks = Math.max(1, Math.min(6, timeoutTicks));
        pendingAttackSlowdown = false;
    }

    synchronized boolean acceptRealAttack(boolean targetAvailable, boolean sprinting, boolean onlySprinting) {
        if (!isAttackWindowActive() || !targetAvailable || onlySprinting && !sprinting) {
            return false;
        }
        attackWindowTicks = 0;
        pendingAttackSlowdown = true;
        return true;
    }

    synchronized boolean consumePendingAttackSlowdown() {
        if (!pendingAttackSlowdown) {
            return false;
        }
        pendingAttackSlowdown = false;
        return true;
    }

    synchronized void tick() {
        if (attackWindowTicks > 0) {
            attackWindowTicks--;
            return;
        }
        pendingAttackSlowdown = false;
    }

    synchronized boolean shouldApplyReduction(int chance) {
        int safeChance = Math.max(0, Math.min(100, chance));
        if (safeChance == 0) {
            return false;
        }
        if (safeChance == 100) {
            return true;
        }
        reductionChanceAccumulator += safeChance;
        if (reductionChanceAccumulator < 100) {
            return false;
        }
        reductionChanceAccumulator -= 100;
        return true;
    }

    synchronized boolean isAttackWindowActive() {
        return attackWindowTicks > 0;
    }

    synchronized boolean hasPendingAttackSlowdown() {
        return pendingAttackSlowdown;
    }

    synchronized void reset() {
        attackWindowTicks = 0;
        pendingAttackSlowdown = false;
        reductionChanceAccumulator = 0;
    }

    static double scale(double value, int retainedPercent) {
        int safePercent = Math.max(0, Math.min(100, retainedPercent));
        return value * safePercent / 100.0D;
    }

    static int scalePacketMotion(int value, int retainedPercent) {
        return (int) Math.round(scale(value, retainedPercent));
    }
}
