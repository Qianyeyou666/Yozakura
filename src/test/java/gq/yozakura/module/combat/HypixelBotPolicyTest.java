package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HypixelBotPolicyTest {
    @Test
    public void rejectsHypixelPlayersMissingFromTheTabList() {
        assertTrue(snapshot(true, false, false, "Enemy", "Enemy").isBot());
    }

    @Test
    public void keepsOrdinaryTabListedHypixelPlayers() {
        assertFalse(snapshot(true, true, false, "Enemy", "§aEnemy").isBot());
    }

    @Test
    public void rejectsNpcMarkersAndSuspiciousInvisibleRedPlayers() {
        assertTrue(snapshot(true, true, false, "Shopkeeper", "§e[NPC] Shopkeeper").isBot());
        assertTrue(snapshot(true, true, true, "Watchdog", "§cWatchdog").isBot());
    }

    @Test
    public void doesNotTreatEveryInvisiblePlayerAsABotOutsideHypixel() {
        assertFalse(snapshot(false, true, true, "Enemy", "Enemy").isBot());
    }

    private static HypixelBotPolicy.Snapshot snapshot(boolean hypixel, boolean tabListed,
                                                        boolean invisible, String profileName,
                                                        String displayName) {
        return new HypixelBotPolicy.Snapshot(hypixel, false, profileName, displayName,
                invisible, tabListed, 50, "team", "§a", 20.0F, 10, false, 100);
    }
}
