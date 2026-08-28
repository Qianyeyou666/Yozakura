package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VapeAutoClickTimingStateTest {
    @Test
    public void normalRandomizationIsSeededAndStaysInsideTheConfiguredRange() {
        VapeAutoClickTimingState first = new VapeAutoClickTimingState(42L);
        VapeAutoClickTimingState second = new VapeAutoClickTimingState(42L);

        for (int click = 0; click < 100; click++) {
            long firstDelay = first.nextDelay(1_000L + click, 8.0D, 12.0D,
                    AutoClickRandomization.NORMAL);
            long secondDelay = second.nextDelay(1_000L + click, 8.0D, 12.0D,
                    AutoClickRandomization.NORMAL);

            assertEquals(firstDelay, secondDelay);
            assertTrue(firstDelay >= 83L);
            assertTrue(firstDelay <= 125L);
        }
    }

    @Test
    public void extraPlusRandomizationIsSeededAndProducesOnlyPositiveDelays() {
        VapeAutoClickTimingState first = new VapeAutoClickTimingState(7L);
        VapeAutoClickTimingState second = new VapeAutoClickTimingState(7L);
        boolean varied = false;
        long previous = -1L;

        for (int click = 0; click < 120; click++) {
            long now = 10_000L + click * 100L;
            long firstDelay = first.nextDelay(now, 8.0D, 12.0D,
                    AutoClickRandomization.EXTRA_PLUS);
            long secondDelay = second.nextDelay(now, 8.0D, 12.0D,
                    AutoClickRandomization.EXTRA_PLUS);

            assertEquals(firstDelay, secondDelay);
            assertTrue(firstDelay > 0L);
            varied |= previous >= 0L && previous != firstDelay;
            previous = firstDelay;
        }

        assertTrue(varied);
    }

    @Test
    public void extraRandomizationRetainsVapesStatefulLegacyPhases() {
        VapeAutoClickTimingState first = new VapeAutoClickTimingState(91L);
        VapeAutoClickTimingState second = new VapeAutoClickTimingState(91L);
        boolean varied = false;
        long previous = -1L;

        for (int click = 0; click < 200; click++) {
            long firstDelay = first.nextDelay(1_000L + click, 6.0D, 13.0D,
                    AutoClickRandomization.EXTRA);
            long secondDelay = second.nextDelay(1_000L + click, 6.0D, 13.0D,
                    AutoClickRandomization.EXTRA);

            assertEquals(firstDelay, secondDelay);
            assertTrue(firstDelay > 0L);
            varied |= previous >= 0L && previous != firstDelay;
            previous = firstDelay;
        }

        assertTrue(varied);
    }

    @Test
    public void blockBreakDelayStartsFromActivationAndHonorsTheToolWhitelist() {
        VapeBlockBreakPolicy policy = new VapeBlockBreakPolicy(5L);

        assertFalse(policy.shouldPause(100L, true, true, false, false,
                true, false, 100.0D, 100.0D));
        assertFalse(policy.shouldPause(199L, true, true, false, false,
                true, false, 100.0D, 100.0D));
        assertTrue(policy.shouldPause(200L, true, true, false, false,
                true, false, 100.0D, 100.0D));

        policy.reset();
        assertFalse(policy.shouldPause(300L, true, true, true, false,
                true, false, 0.0D, 0.0D));
        assertTrue(policy.shouldPause(300L, true, true, true, true,
                true, false, 0.0D, 0.0D));
    }

    @Test
    public void jitterBuildsMouseDeltasOverTicksAndCanBeReset() {
        VapeAutoClickJitter jitter = new VapeAutoClickJitter(27L);
        boolean producedDelta = false;

        jitter.generate();
        for (int tick = 0; tick < 20; tick++) {
            jitter.advance();
            producedDelta |= jitter.yawDelta() != 0 || jitter.pitchDelta() != 0;
        }

        assertTrue(producedDelta);
        jitter.reset();
        assertEquals(0, jitter.yawDelta());
        assertEquals(0, jitter.pitchDelta());
    }

    @Test
    public void activationPolicyMatchesVapeHoldAndTriggerModes() {
        assertFalse(AutoClickActivationPolicy.isActive(true, false));
        assertTrue(AutoClickActivationPolicy.isActive(true, true));
        assertTrue(AutoClickActivationPolicy.isActive(false, false));

        assertFalse(AutoClickActivationPolicy.hasValidTarget(true, false));
        assertTrue(AutoClickActivationPolicy.hasValidTarget(true, true));
        assertTrue(AutoClickActivationPolicy.hasValidTarget(false, false));
    }

    @Test
    public void vapeControllerUsesTheFiftyMillisecondActivationDelay() {
        AutoClickController controller = new AutoClickController(19L);

        assertFalse(controller.shouldClick(100L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(149L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertTrue(controller.shouldClick(150L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
    }

    @Test
    public void clickCycleUsesVapesFiveMillisecondAdjustmentAndShortDelayClamp() {
        assertEquals(78L, AutoClickController.adjustVapeDelay(83L));
        assertEquals(45L, AutoClickController.adjustVapeDelay(55L));
        assertEquals(51L, AutoClickController.adjustVapeDelay(56L));
    }
}
