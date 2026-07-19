package gq.yozakura.module.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChestAccessTrackerTest {
    @Test
    public void serverMenuWithoutPhysicalChestInteractionIsRejected() {
        ChestAccessTracker tracker = new ChestAccessTracker(1500L);

        assertFalse(tracker.authorizeWindow(4, 1000L));
    }

    @Test
    public void recentPhysicalChestInteractionAuthorizesOnlyTheOpenedWindow() {
        ChestAccessTracker tracker = new ChestAccessTracker(1500L);
        tracker.recordPhysicalInteraction(1000L);

        assertTrue(tracker.authorizeWindow(7, 1200L));
        assertTrue(tracker.isAuthorizedWindow(7));
        assertFalse(tracker.authorizeWindow(8, 1300L));
    }

    @Test
    public void expiredPhysicalChestInteractionCannotAuthorizeAMenu() {
        ChestAccessTracker tracker = new ChestAccessTracker(1500L);
        tracker.recordPhysicalInteraction(1000L);

        assertFalse(tracker.authorizeWindow(7, 2501L));
    }
}
