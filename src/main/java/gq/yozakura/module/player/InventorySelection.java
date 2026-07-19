package gq.yozakura.module.player;

final class InventorySelection {
    enum ArmorAction {
        NONE,
        EQUIP_BEST,
        UNEQUIP_CURRENT
    }

    private InventorySelection() {
    }

    static ArmorAction chooseArmorAction(int bestSlot, int armorSlot, boolean equipped) {
        if (bestSlot == -1 || bestSlot == armorSlot) {
            return ArmorAction.NONE;
        }
        return equipped ? ArmorAction.UNEQUIP_CURRENT : ArmorAction.EQUIP_BEST;
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
