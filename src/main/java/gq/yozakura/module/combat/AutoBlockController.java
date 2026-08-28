package gq.yozakura.module.combat;

/**
 * Pure state machine for one owned vanilla sword-use lifecycle.
 */
final class AutoBlockController {
    enum Action {
        NONE,
        PRESS,
        RELEASE,
        YIELD
    }

    private boolean blocking;
    private boolean deferNextPress;
    private long releaseAt;

    Action update(long now, boolean gameplayReady, boolean physicalUseDown,
                  boolean pointingAtEntity, double targetDistance, double maxDistance,
                  double chancePercent, long durationMillis, double randomPercent) {
        if (!gameplayReady) {
            return reset();
        }

        if (deferNextPress) {
            deferNextPress = false;
            return Action.NONE;
        }

        if (physicalUseDown) {
            if (blocking) {
                blocking = false;
                releaseAt = 0L;
                return Action.YIELD;
            }
            return Action.NONE;
        }

        if (blocking) {
            if (!pointingAtEntity || targetDistance > maxDistance || now >= releaseAt) {
                blocking = false;
                releaseAt = 0L;
                return Action.RELEASE;
            }
            return Action.NONE;
        }

        if (!pointingAtEntity || targetDistance > maxDistance) {
            return Action.NONE;
        }
        if (chancePercent <= 0.0D
                || chancePercent < 100.0D && randomPercent >= chancePercent) {
            return Action.NONE;
        }

        blocking = true;
        releaseAt = now + Math.max(0L, durationMillis);
        return Action.PRESS;
    }

    void pressFailed() {
        blocking = false;
        releaseAt = 0L;
    }

    Action releaseForAttack() {
        deferNextPress = true;
        if (!blocking) {
            releaseAt = 0L;
            return Action.NONE;
        }
        blocking = false;
        releaseAt = 0L;
        return Action.RELEASE;
    }

    Action reset() {
        deferNextPress = false;
        if (!blocking) {
            releaseAt = 0L;
            return Action.NONE;
        }
        blocking = false;
        releaseAt = 0L;
        return Action.RELEASE;
    }

    boolean isBlocking() {
        return blocking;
    }
}
