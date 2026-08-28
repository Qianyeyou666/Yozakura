package gq.yozakura.module.combat;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class KillAuraConfirmedRotationTrackerTest {
    @Test
    public void firstClaimCannotEnableAttackBeforeTheBoundaryIsConfirmed() {
        KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();

        tracker.claim(179.5F, 12.0F);
        tracker.drain(true, true);
        assertNull(tracker.getConfirmed());

        tracker.acceptBoundary(true, true, -180.5F, 12.0F);
        tracker.drain(true, true);
        assertNotNull(tracker.getConfirmed());
        assertEquals(-180.5F, tracker.getConfirmed().yaw, 0.0001F);
        assertEquals(12.0F, tracker.getConfirmed().pitch, 0.0001F);
    }

    @Test
    public void mismatchedBoundaryCannotBorrowAnotherModuleRotation() {
        KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();

        tracker.claim(30.0F, 8.0F);
        tracker.acceptBoundary(true, true, 31.0F, 8.0F);
        tracker.drain(true, true);

        assertNull(tracker.getConfirmed());
    }

    @Test
    public void unrotatedBoundaryClearsAPreviouslyConfirmedRotation() {
        KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();

        tracker.claim(30.0F, 8.0F);
        tracker.acceptBoundary(true, true, 30.0F, 8.0F);
        tracker.drain(true, true);
        assertNotNull(tracker.getConfirmed());

        tracker.acceptBoundary(true, false, 0.0F, 0.0F);
        tracker.drain(true, true);
        assertNull(tracker.getConfirmed());
    }

    @Test
    public void noneAndLegitModesDiscardSilentConfirmationState() {
        KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();

        tracker.claim(30.0F, 8.0F);
        tracker.acceptBoundary(true, true, 30.0F, 8.0F);
        tracker.drain(true, true);
        assertNotNull(tracker.getConfirmed());

        tracker.drain(true, false);
        assertNull(tracker.getConfirmed());

        tracker.claim(30.0F, 8.0F);
        tracker.acceptBoundary(true, true, 30.0F, 8.0F);
        tracker.drain(true, true);
        tracker.drain(false, true);
        assertNull(tracker.getConfirmed());
    }

    @Test
    public void acceptedBoundaryCallbackCanTransferAcrossThreads() throws Exception {
        final KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();
        final CountDownLatch callbackDone = new CountDownLatch(1);

        tracker.claim(45.0F, -10.0F);
        Thread callback = new Thread(new Runnable() {
            @Override
            public void run() {
                tracker.acceptBoundary(true, true, 45.0F, -10.0F);
                callbackDone.countDown();
            }
        });
        callback.start();

        if (!callbackDone.await(2L, TimeUnit.SECONDS)) {
            throw new AssertionError("boundary callback did not complete");
        }
        tracker.drain(true, true);
        assertNotNull(tracker.getConfirmed());
    }

    @Test
    public void aNewClaimInvalidatesAnOlderConfirmationUntilItsBoundarySucceeds() {
        KillAuraConfirmedRotationTracker tracker = new KillAuraConfirmedRotationTracker();

        tracker.claim(30.0F, 8.0F);
        tracker.acceptBoundary(true, true, 30.0F, 8.0F);
        tracker.drain(true, true);
        assertNotNull(tracker.getConfirmed());

        tracker.claim(31.0F, 8.5F);

        assertNull(tracker.getConfirmed());
    }
}
