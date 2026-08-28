package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Guards the reference Legit behavior against placement-packet coupling. */
public class BridgeAssistPlacementSessionTrackerTest {
    @Test
    public void acceptedPlacementDoesNotStartLegitSneakingOnSupportedGround() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(100, false, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.State.IDLE, machine.getState());
    }

    @Test
    public void completedPlacementDoesNotReleaseLegitSneakAtTheEdge() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(101, true, false, true)));
        assertEquals(BridgeAssistSneakStateMachine.State.EDGE_HELD, machine.getState());
    }

    @Test
    public void packetStateDoesNotExtendReleasePastTheConfiguredDelay() {
        BridgeAssistSneakStateMachine machine = new BridgeAssistSneakStateMachine();

        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(100, true, false, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                machine.update(frame(101, false, true, false)));
        assertEquals(BridgeAssistSneakStateMachine.Decision.KEEP,
                machine.update(frame(102, false, false, true)));
    }

    private static BridgeAssistSneakStateMachine.Frame frame(int tick, boolean edge,
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
                1,
                0
        );
    }
}
