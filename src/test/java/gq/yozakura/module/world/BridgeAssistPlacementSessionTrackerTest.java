package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistPlacementSessionTrackerTest {
    @Test
    public void claimsAnAcceptedPlacementDuringItsFirstSneakInput() {
        BridgeAssistSneakController.PlacementSessionTracker tracker =
                new BridgeAssistSneakController.PlacementSessionTracker();

        tracker.onPlacementAccepted(101L);
        long inputFrame = tracker.beginInputFrame();
        assertTrue(tracker.hasPlacementForInputFrame(inputFrame));
        tracker.finishInputFrame(true, inputFrame);

        assertTrue(tracker.hasPendingPlacementForActiveSession());
        tracker.onPlacementCompleted(101L, true);
        assertTrue(tracker.consumePlacementCommit());
    }

    @Test
    public void preservesACompletionThatWinsTheRaceAgainstTheFirstSessionClaim() {
        BridgeAssistSneakController.PlacementSessionTracker tracker =
                new BridgeAssistSneakController.PlacementSessionTracker();

        tracker.onPlacementAccepted(102L);
        tracker.onPlacementCompleted(102L, true);
        long inputFrame = tracker.beginInputFrame();
        tracker.finishInputFrame(true, inputFrame);

        assertFalse(tracker.hasPendingPlacementForActiveSession());
        assertTrue(tracker.consumePlacementCommit());
    }

    @Test
    public void doesNotClaimAnUnrelatedPlacementInALaterSneakSession() {
        BridgeAssistSneakController.PlacementSessionTracker tracker =
                new BridgeAssistSneakController.PlacementSessionTracker();

        tracker.onPlacementAccepted(103L);
        long firstInput = tracker.beginInputFrame();
        tracker.finishInputFrame(false, firstInput);
        long secondInput = tracker.beginInputFrame();
        tracker.finishInputFrame(true, secondInput);

        assertFalse(tracker.hasPendingPlacementForActiveSession());
        assertFalse(tracker.consumePlacementCommit());
    }

    @Test
    public void ignoresAnOldWriteCompletionAfterReset() {
        BridgeAssistSneakController.PlacementSessionTracker tracker =
                new BridgeAssistSneakController.PlacementSessionTracker();

        tracker.onPlacementAccepted(104L);
        long firstInput = tracker.beginInputFrame();
        tracker.finishInputFrame(true, firstInput);
        tracker.reset();
        long secondInput = tracker.beginInputFrame();
        tracker.finishInputFrame(true, secondInput);
        tracker.onPlacementCompleted(104L, true);

        assertFalse(tracker.consumePlacementCommit());
    }
}
