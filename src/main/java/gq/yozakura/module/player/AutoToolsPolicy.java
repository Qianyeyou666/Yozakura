package gq.yozakura.module.player;

/** Pure hotbar scoring and ownership rules for AutoTools. */
final class AutoToolsPolicy {
    private static final float MIN_EFFECTIVE_STRENGTH = 1.0F;

    private AutoToolsPolicy() {
    }

    static float score(float strength, int efficiencyLevel, boolean lowDurability) {
        if (lowDurability || strength <= MIN_EFFECTIVE_STRENGTH) {
            return strength;
        }
        int level = Math.max(0, efficiencyLevel);
        return strength + (level > 0 ? level * level + 1.0F : 0.0F);
    }

    static float combatScore(float attackDamage, int sharpnessLevel) {
        int level = Math.max(0, sharpnessLevel);
        return attackDamage + level * 1.25F;
    }

    static int bestSlot(int currentSlot, float[] scores, boolean[] usable) {
        if (scores == null || usable == null || scores.length == 0 || scores.length != usable.length) {
            return currentSlot;
        }
        int bestSlot = validSlot(currentSlot, scores.length) && usable[currentSlot] ? currentSlot : -1;
        float bestScore = bestSlot >= 0 ? scores[bestSlot] : Float.NEGATIVE_INFINITY;
        for (int slot = 0; slot < scores.length; slot++) {
            if (!usable[slot] || scores[slot] <= bestScore) {
                continue;
            }
            bestSlot = slot;
            bestScore = scores[slot];
        }
        return bestSlot >= 0 ? bestSlot : currentSlot;
    }

    static boolean isManualOverride(boolean ownsSlot, int currentSlot, int selectedSlot) {
        return ownsSlot && currentSlot != selectedSlot;
    }

    static boolean shouldRestore(boolean restoreEnabled, boolean ownsSlot, int currentSlot,
                                 int selectedSlot, int originalSlot) {
        return restoreEnabled
                && ownsSlot
                && currentSlot == selectedSlot
                && originalSlot >= 0
                && originalSlot <= 8
                && originalSlot != selectedSlot;
    }

    private static boolean validSlot(int slot, int length) {
        return slot >= 0 && slot < length;
    }
}
