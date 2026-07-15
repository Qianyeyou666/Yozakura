package gq.yozakura.module.combat;

final class VelocityController {
    private int attackWindowTicks;
    private boolean pendingAttackSlowdown;

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

    synchronized boolean isAttackWindowActive() {
        return attackWindowTicks > 0;
    }

    synchronized boolean hasPendingAttackSlowdown() {
        return pendingAttackSlowdown;
    }

    synchronized void reset() {
        attackWindowTicks = 0;
        pendingAttackSlowdown = false;
    }

    static double scale(double value, int retainedPercent) {
        int safePercent = Math.max(0, Math.min(100, retainedPercent));
        return value * safePercent / 100.0D;
    }
}
