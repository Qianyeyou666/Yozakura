package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickControllerTest {
    @Test
    public void rightClickAloneCannotArmOrFireTheClicker() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, false, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(500L, false, true, 8.0D, 12.0D, false));
    }

    @Test
    public void leftClickWaitsForTheScheduledDelay() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, true, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(199L, true, true, 8.0D, 12.0D, false));
        assertTrue(controller.shouldClick(200L, true, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(201L, true, true, 8.0D, 12.0D, false));
    }

    @Test
    public void releasingLeftClickClearsTheOldSchedule() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, true, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(150L, false, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(200L, true, true, 8.0D, 12.0D, false));
        assertFalse(controller.shouldClick(299L, true, true, 8.0D, 12.0D, false));
        assertTrue(controller.shouldClick(300L, true, true, 8.0D, 12.0D, false));
    }

    @Test
    public void smoothRhythmStaysInsideTheConfiguredCpsRange() {
        assertEquals(112L, AutoClickController.calculateDelay(8.0D, 12.0D, true, 0));
        assertEquals(84L, AutoClickController.calculateDelay(8.0D, 12.0D, true, 3));
        assertEquals(125L, AutoClickController.calculateDelay(8.0D, 12.0D, true, 7));
    }

    @Test
    public void steadyRhythmUsesTheExactMidpointWithoutRandomBursts() {
        assertEquals(100L, AutoClickController.calculateDelay(8.0D, 12.0D, false, 0));
        assertEquals(100L, AutoClickController.calculateDelay(8.0D, 12.0D, false, 99));
        assertEquals(50L, AutoClickController.calculateDelay(30.0D, 40.0D, false, 0));
    }
}
