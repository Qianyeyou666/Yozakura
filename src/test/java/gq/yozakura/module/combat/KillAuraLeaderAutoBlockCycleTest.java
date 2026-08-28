package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KillAuraLeaderAutoBlockCycleTest {
    @Test
    public void leaderLegitAttacksAndBlocksThenReleasesUntilTheNextAttackWindow() {
        KillAuraLeaderAutoBlockCycle cycle = new KillAuraLeaderAutoBlockCycle();

        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.WAIT,
                cycle.nextLegit(true, false, 120L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.ATTACK_AND_BLOCK,
                cycle.nextLegit(true, false, 0L, true));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.RELEASE_AND_WAIT,
                cycle.nextLegit(true, true, 100L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.WAIT,
                cycle.nextLegit(true, false, 80L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.WAIT,
                cycle.nextLegit(true, false, 50L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.ATTACK_AND_BLOCK,
                cycle.nextLegit(true, false, 0L, true));
    }

    @Test
    public void leaderHypixelWithoutNoSlowUsesItsThreePhaseBlinkCycle() {
        KillAuraLeaderAutoBlockCycle cycle = new KillAuraLeaderAutoBlockCycle();

        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, true));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.WAIT,
                cycle.nextHypixel(true, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.BLINK_RELEASE_AND_ATTACK,
                cycle.nextHypixel(true, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, true));
    }

    @Test
    public void leaderHypixelLagBlocksThenReleasesAndWaitsForTheAttackWindow() {
        KillAuraLeaderAutoBlockCycle cycle = new KillAuraLeaderAutoBlockCycle();

        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.ATTACK_AND_BLOCK,
                cycle.nextHypixelLag(true, false, 0L, true));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.RELEASE_AND_SUPPRESS_ATTACK,
                cycle.nextHypixelLag(true, true, 120L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.FLUSH_AND_WAIT,
                cycle.nextHypixelLag(true, false, 80L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.FLUSH_AND_WAIT,
                cycle.nextHypixelLag(true, false, 50L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.ATTACK_AND_BLOCK,
                cycle.nextHypixelLag(true, false, 0L, true));
    }

    @Test
    public void failedInitialAttacksDoNotAdvanceReferenceCycles() {
        KillAuraLeaderAutoBlockCycle cycle = new KillAuraLeaderAutoBlockCycle();

        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.ATTACK_AND_BLOCK,
                cycle.nextLegit(true, false, 0L, true));
        cycle.onLegitInitialAttackResult(false);
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.ATTACK_AND_BLOCK,
                cycle.nextLegit(true, false, 0L, true));

        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, true));
        cycle.onHypixelInitialAttackResult(false);
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, true));
    }

    @Test
    public void losingTheTargetResetsBothReferenceCycles() {
        KillAuraLeaderAutoBlockCycle cycle = new KillAuraLeaderAutoBlockCycle();
        cycle.nextLegit(true, false, 0L, true);
        cycle.nextHypixel(true, true);

        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.RESET,
                cycle.nextLegit(false, false, 0L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.RESET,
                cycle.nextHypixel(false, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.LegitStep.ATTACK_AND_BLOCK,
                cycle.nextLegit(true, false, 0L, true));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelStep.FLUSH_ATTACK_AND_BLOCK,
                cycle.nextHypixel(true, true));

        cycle.nextHypixelLag(true, false, 0L, true);
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.RESET,
                cycle.nextHypixelLag(false, false, 0L, false));
        assertEquals(KillAuraLeaderAutoBlockCycle.HypixelLagStep.ATTACK_AND_BLOCK,
                cycle.nextHypixelLag(true, false, 0L, true));
    }
}
