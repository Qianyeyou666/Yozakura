package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickControllerTest {
    @Test
    public void rightClickAloneCannotArmOrFireTheClicker() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, false, true, 80L));
        assertFalse(controller.shouldClick(500L, false, true, 80L));
    }

    @Test
    public void leftClickWaitsForTheScheduledDelay() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, true, true, 80L));
        assertFalse(controller.shouldClick(179L, true, true, 80L));
        assertTrue(controller.shouldClick(180L, true, true, 80L));
        assertFalse(controller.shouldClick(181L, true, true, 80L));
    }

    @Test
    public void releasingLeftClickClearsTheOldSchedule() {
        AutoClickController controller = new AutoClickController();

        assertFalse(controller.shouldClick(100L, true, true, 80L));
        assertFalse(controller.shouldClick(150L, false, true, 80L));
        assertFalse(controller.shouldClick(180L, true, true, 80L));
        assertFalse(controller.shouldClick(259L, true, true, 80L));
        assertTrue(controller.shouldClick(260L, true, true, 80L));
    }
}
