package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BridgeAssistSneakStateMachineTest {
    @Test
    public void legitProbeCanSneakAtTheEdgeWithoutDirectionalInput() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(new BridgeAssistSneakStateMachine.Frame(
                        100, true, false, false, false, false, true,
                        true, false, false, false, 2, 0)));
        assertEquals(BridgeAssistSneakStateMachine.State.EDGE_HELD, machine.getState());
    }

    @Test
    public void doesNotForceLegitSneakForAnAirborneEdgeProbe() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(new BridgeAssistSneakStateMachine.Frame(
                        100, true, true, false, false, false, false,
                        true, true, false, false, 2, 0)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    @Test
    public void keepsSneakingUntilTheConfiguredReleaseTick() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(101, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(102, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(103, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    @Test
    public void cancelsTheReleaseDelayWhenThePlayerReachesAnotherEdge() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        machine.update(frame(100, true, false, false));
        machine.update(frame(101, false, false, false));

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(102, true, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(103, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(104, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(105, false, false, false)));
    }

    @Test
    public void placementCompletionDoesNotChangeTheReferenceReleaseDelay() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(101, false, true, true)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(102, false, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(103, false, true, false)));
    }

    @Test
    public void resetClearsAllTransientStateAndReturnsControlToVanilla() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();
        machine.update(frame(100, true, false, false));

        machine.reset();

        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(101, false, false, false)));
    }

    @Test
    public void doesNotLayerTheUnsneakDelayAfterAJumpOnlyHold() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(jumpFrame(100, true, true)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(jumpFrame(101, false, false)));
    }

    @Test
    public void acceptedPlacementDoesNotExtendTheReferenceReleaseDelay() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(packetFrame(100, true, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(packetFrame(101, false, true, false)));
    }

    @Test
    public void committedPlacementDoesNotReleaseSneakWhileLegitProbeStillSeesTheEdge() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, false, false)));

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(101, true, false, true)));
        assertEquals(BridgeAssistSneakStateMachine.State.EDGE_HELD, machine.getState());
    }

    @Test
    public void acceptedPlacementDoesNotStartSneakingBeforeTheLegitProbeReachesTheEdge() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(packetFrame(100, false, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    @Test
    public void returnsControlWhenAPendingPlacementOutlivesMovement() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(packetFrame(100, true, false, false)));

        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(pendingFrame(101, false, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    @Test
    public void returnsControlWhenTheRequiredSneakKeyIsReleasedDuringAPendingPlacement() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, true, false)));

        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(pendingFrame(101, true, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    private static BridgeAssistSneakStateMachine.Frame frame(int tick, boolean edge,
                                                              boolean physicalSneak, boolean placementCommitted) {
        return new BridgeAssistSneakStateMachine.Frame(
                tick,
                true,
                true,
                physicalSneak,
                physicalSneak,
                false,
                true,
                edge,
                false,
                false,
                placementCommitted,
                2,
                0
        );
    }

    private static BridgeAssistSneakStateMachine.Frame jumpFrame(int tick, boolean jump, boolean onGround) {
        return new BridgeAssistSneakStateMachine.Frame(
                tick,
                true,
                true,
                false,
                false,
                jump,
                onGround,
                false,
                false,
                false,
                false,
                1,
                1
        );
    }

    private static BridgeAssistSneakStateMachine.Frame packetFrame(int tick, boolean edge,
                                                                     boolean placementPending,
                                                                     boolean placementCommitted) {
        return new BridgeAssistSneakStateMachine.Frame(
                tick,
                true,
                true,
                false,
                false,
                false,
                true,
                edge,
                false,
                placementPending,
                placementCommitted,
                0,
                0
        );
    }

    private static BridgeAssistSneakStateMachine.Frame pendingFrame(int tick, boolean moving,
                                                                      boolean requirePhysicalSneak,
                                                                      boolean physicalSneak) {
        return new BridgeAssistSneakStateMachine.Frame(
                tick,
                true,
                moving,
                physicalSneak,
                requirePhysicalSneak,
                false,
                true,
                false,
                false,
                true,
                false,
                0,
                0
        );
    }
}
