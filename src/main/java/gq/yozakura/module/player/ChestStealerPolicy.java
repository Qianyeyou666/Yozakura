package gq.yozakura.module.player;

import java.util.Locale;

final class ChestStealerPolicy {
    private ChestStealerPolicy() {
    }

    static int slotAt(int step, int start, int slotCount) {
        if (slotCount <= 0) {
            return -1;
        }
        int normalizedStart = ((start % slotCount) + slotCount) % slotCount;
        int normalizedStep = ((step % slotCount) + slotCount) % slotCount;
        return (normalizedStart + normalizedStep) % slotCount;
    }

    static long nextDelay(long baseDelay, long jitter, double randomUnit) {
        long safeBase = Math.max(0L, baseDelay);
        long safeJitter = Math.max(0L, jitter);
        long minimum = Math.max(0L, safeBase - safeJitter);
        long maximum = safeBase > Long.MAX_VALUE - safeJitter ? Long.MAX_VALUE : safeBase + safeJitter;
        double safeRandom = Math.max(0.0D, Math.min(0.999999999D, randomUnit));
        return minimum + (long) Math.floor((maximum - minimum + 1.0D) * safeRandom);
    }

    static boolean isStandardChestTitle(String title) {
        if (title == null) {
            return false;
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        return "chest".equals(normalized)
                || "large chest".equals(normalized)
                || "container.chest".equals(normalized)
                || "container.chestdouble".equals(normalized)
                || "箱子".equals(normalized)
                || "大型箱子".equals(normalized);
    }

    static boolean canTransfer(boolean hasEmptySlot, boolean hasCompatiblePartialStack) {
        return hasEmptySlot || hasCompatiblePartialStack;
    }

    static boolean shouldTakeEquipment(float candidateScore, float ownedScore, boolean hasOwnedItem) {
        return !hasOwnedItem || candidateScore > ownedScore + 0.0001F;
    }

    static boolean shouldTakePotion(boolean hasUsefulEffect, boolean hasHarmfulEffect) {
        return hasUsefulEffect && !hasHarmfulEffect;
    }
}
