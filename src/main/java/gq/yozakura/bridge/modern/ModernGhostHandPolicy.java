package gq.yozakura.bridge.modern;

final class ModernGhostHandPolicy {
    private ModernGhostHandPolicy() {
    }

    static boolean shouldSkip(boolean playerEntity, boolean bot, boolean teamOnly,
                              boolean sameTeam, boolean ignoreWeapons, boolean protectedWeapon) {
        return playerEntity
                && !bot
                && (!teamOnly || sameTeam)
                && (!ignoreWeapons || !protectedWeapon);
    }
}
