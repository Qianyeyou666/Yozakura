package gq.yozakura.module.player;

final class InventorySelection {
    enum ArmorAction {
        NONE,
        EQUIP_BEST,
        UNEQUIP_CURRENT
    }

    private InventorySelection() {
    }

    static ArmorAction chooseArmorAction(int bestSlot, int armorSlot, boolean equipped,
                                         boolean hasInventorySpace) {
        if (bestSlot == -1 || bestSlot == armorSlot) {
            return ArmorAction.NONE;
        }
        if (equipped) {
            return hasInventorySpace ? ArmorAction.UNEQUIP_CURRENT : ArmorAction.NONE;
        }
        return ArmorAction.EQUIP_BEST;
    }

    static float toolScore(float baseEfficiency, int efficiencyLevel) {
        if (baseEfficiency <= 1.0F) {
            return baseEfficiency;
        }
        int level = Math.max(0, efficiencyLevel);
        return baseEfficiency + (level > 0 ? level * level + 1.0F : 0.0F);
    }

    static boolean shouldKeepBlock(boolean placeable) {
        return placeable;
    }

    static boolean isBetterCandidate(float candidateScore, int candidateDurability, int candidateSlot,
                                     float bestScore, int bestDurability, int bestSlot, int preferredSlot) {
        if (bestSlot == -1) {
            return true;
        }
        int scoreComparison = Float.compare(candidateScore, bestScore);
        if (scoreComparison != 0) {
            return scoreComparison > 0;
        }
        if (candidateDurability != bestDurability) {
            return candidateDurability > bestDurability;
        }
        if (candidateSlot == preferredSlot || bestSlot == preferredSlot) {
            return candidateSlot == preferredSlot;
        }
        return candidateSlot < bestSlot;
    }
}
