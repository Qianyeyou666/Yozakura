package gq.yozakura.bridge.modern;

final class ModernHitSelectState {
    private int selectedEntityId = -1;
    private int lastHurtTime = -1;
    private long nextAllowedAt;
    private long lastAttackAt;

    boolean shouldAttack(long now, int entityId, int targetHurtTime, int playerHurtTime,
                         String mode, int maxHurtTime, int tradeWindow, long postAttackDelay,
                         boolean chanceAccepted, long nextDelay) {
        if (entityId != selectedEntityId) {
            selectedEntityId = entityId;
            lastHurtTime = targetHurtTime;
            nextAllowedAt = now + nextDelay;
        } else if (targetHurtTime > lastHurtTime) {
            nextAllowedAt = now + nextDelay;
            lastHurtTime = targetHurtTime;
        } else {
            lastHurtTime = targetHurtTime;
        }

        if (now - lastAttackAt < postAttackDelay || !chanceAccepted) {
            return false;
        }

        boolean trade = playerHurtTime > 0 && playerHurtTime <= tradeWindow;
        if ("Trade".equalsIgnoreCase(mode) && trade) {
            return now >= nextAllowedAt;
        }
        if ("Smart".equalsIgnoreCase(mode) && trade && targetHurtTime <= maxHurtTime + 2) {
            return now >= nextAllowedAt;
        }
        return targetHurtTime <= maxHurtTime && now >= nextAllowedAt;
    }

    void onAttack(long now, int entityId, int targetHurtTime, long nextDelay) {
        selectedEntityId = entityId;
        lastHurtTime = targetHurtTime;
        lastAttackAt = now;
        nextAllowedAt = now + nextDelay;
    }

    void reset() {
        selectedEntityId = -1;
        lastHurtTime = -1;
        nextAllowedAt = 0L;
        lastAttackAt = 0L;
    }
}
