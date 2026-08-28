package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoBlockControllerTest {
    @Test
    public void validTargetStartsBlockingWithoutPhysicalUseInput() {
        AutoBlockController controller = new AutoBlockController();

        AutoBlockController.Action action = controller.update(
                100L, true, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D);

        assertEquals(AutoBlockController.Action.PRESS, action);
        assertTrue(controller.isBlocking());
    }

    @Test
    public void crosshairMustPointAtAnEntityWithinDistance() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.NONE, controller.update(
                100L, true, false, false, 0.0D, 4.0D, 100.0D, 100L, 0.0D));
        assertEquals(AutoBlockController.Action.NONE, controller.update(
                101L, true, false, true, 4.01D, 4.0D, 100.0D, 100L, 0.0D));
        assertFalse(controller.isBlocking());
    }

    @Test
    public void chanceGateUsesPercentageSemantics() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.NONE, controller.update(
                100L, true, false, true, 2.0D, 4.0D, 0.0D, 100L, 0.0D));
        assertEquals(AutoBlockController.Action.NONE, controller.update(
                101L, true, false, true, 2.0D, 4.0D, 35.0D, 100L, 35.0D));
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                102L, true, false, true, 2.0D, 4.0D, 35.0D, 100L, 34.999D));
        assertTrue(controller.isBlocking());
    }

    @Test
    public void successfulTriggerHoldsUntilConfiguredReleaseTime() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                100L, true, false, true, 2.0D, 4.0D, 100.0D, 75L, 0.0D));
        assertEquals(AutoBlockController.Action.NONE, controller.update(
                174L, true, false, true, 2.0D, 4.0D, 100.0D, 75L, 0.0D));
        assertTrue(controller.isBlocking());
        assertEquals(AutoBlockController.Action.RELEASE, controller.update(
                175L, true, false, true, 2.0D, 4.0D, 100.0D, 75L, 0.0D));
        assertFalse(controller.isBlocking());
    }

    @Test
    public void losingTheTargetImmediatelyReleasesOwnedUse() {
        AutoBlockController controller = new AutoBlockController();
        controller.update(100L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D);

        assertEquals(AutoBlockController.Action.RELEASE, controller.update(
                110L, true, false, false, 10.0D, 4.0D, 100.0D, 200L, 0.0D));
        assertFalse(controller.isBlocking());
    }

    @Test
    public void manualUseOwnerPreventsStartAndReleasesOwnedUse() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.NONE, controller.update(
                100L, true, true, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
        controller.update(101L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D);
        assertEquals(AutoBlockController.Action.YIELD, controller.update(
                102L, true, true, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
    }

    @Test
    public void invalidGameplayReleasesAnActiveBlock() {
        AutoBlockController controller = new AutoBlockController();
        controller.update(100L, true, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D);

        assertEquals(AutoBlockController.Action.RELEASE, controller.update(
                110L, false, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D));
        assertFalse(controller.isBlocking());
    }

    @Test
    public void attackReleasesOwnedUseAndDefersTheNextPress() {
        AutoBlockController controller = new AutoBlockController();
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                100L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));

        assertEquals(AutoBlockController.Action.RELEASE, controller.releaseForAttack());
        assertFalse(controller.isBlocking());
        assertEquals("The first update after an attack must leave vanilla an unblocked attack window",
                AutoBlockController.Action.NONE, controller.update(
                        101L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
        assertFalse(controller.isBlocking());
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                102L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
        assertTrue(controller.isBlocking());
    }

    @Test
    public void attackWindowCanBeOpenedBeforeTheControllerStartsBlocking() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.NONE, controller.releaseForAttack());
        assertEquals(AutoBlockController.Action.NONE, controller.update(
                100L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                101L, true, false, true, 2.0D, 4.0D, 100.0D, 200L, 0.0D));
    }

    @Test
    public void resetReleasesOnlyWhenTheControllerOwnsTheUseKey() {
        AutoBlockController controller = new AutoBlockController();

        assertEquals(AutoBlockController.Action.NONE, controller.reset());
        controller.update(100L, true, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D);
        assertEquals(AutoBlockController.Action.RELEASE, controller.reset());
        assertEquals(AutoBlockController.Action.NONE, controller.reset());
    }

    @Test
    public void failedVanillaUseDoesNotLeaveFalseBlockingOwnership() {
        AutoBlockController controller = new AutoBlockController();
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                100L, true, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D));

        controller.pressFailed();

        assertFalse(controller.isBlocking());
        assertEquals(AutoBlockController.Action.PRESS, controller.update(
                101L, true, false, true, 2.0D, 4.0D, 100.0D, 100L, 0.0D));
    }
}
