package gq.yozakura.event.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SneakInputMovementOverrideTest {
    @Test
    public void highestPriorityMovementOwnerWinsTheInputFrame() {
        SneakInputEvent event = new SneakInputEvent(1, 0.0F, 0.0F, false, false, false);

        event.requestMovement(1.0F, 0.0F, true, 1);
        event.requestMovement(-1.0F, -1.0F, false, 5);
        event.requestMovement(0.0F, 1.0F, true, 4);

        assertTrue(event.hasMovementOverride());
        assertEquals(-1.0F, event.getResolvedForward(), 0.0F);
        assertEquals(-1.0F, event.getResolvedStrafe(), 0.0F);
        assertFalse(event.isResolvedJump());
    }
}
