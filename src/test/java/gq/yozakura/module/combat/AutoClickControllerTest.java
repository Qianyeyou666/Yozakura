package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickControllerTest {
    @Test
    public void rightClickAloneCannotArmOrFireTheClicker() {
        AutoClickController controller = new AutoClickController(1L);

        assertFalse(controller.shouldClick(100L, false, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(500L, false, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
    }

    @Test
    public void leftClickWaitsForTheScheduledDelay() {
        AutoClickController controller = new AutoClickController(2L);

        assertFalse(controller.shouldClick(100L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(149L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertTrue(controller.shouldClick(150L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(151L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
    }

    @Test
    public void releasingLeftClickClearsTheOldSchedule() {
        AutoClickController controller = new AutoClickController(3L);

        assertFalse(controller.shouldClick(100L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(150L, false, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(200L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertFalse(controller.shouldClick(249L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
        assertTrue(controller.shouldClick(250L, true, true, 8.0D, 12.0D,
                AutoClickRandomization.NORMAL));
    }
}
