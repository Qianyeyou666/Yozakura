package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistBridgeModeStateMachineTest {
    @Test
    public void godBridgeReleasesItsRotationPathWhenActivationInputIsReleased() {
        BridgeAssistBridgeModeStateMachine machine = new BridgeAssistBridgeModeStateMachine();

        machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(99, 0.0F, true, true, true));
        machine.recordManualPlacement();
        assertTrue(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(100, 0.0F, true, true, true)).isActive());

        assertFalse(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(101, 0.0F, true, false, false)).isActive());
        assertFalse(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(102, 0.0F, true, true, true)).isActive());
    }

    @Test
    public void godBridgeRequiresBackwardAndUseInputWhileArming() {
        BridgeAssistBridgeModeStateMachine machine = new BridgeAssistBridgeModeStateMachine();

        machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(99, 0.0F, true, false, true));
        machine.recordManualPlacement();
        assertFalse(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(100, 0.0F, true, false, true)).isActive());

        machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(101, 0.0F, true, true, false));
        machine.recordManualPlacement();
        assertFalse(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(102, 0.0F, true, true, false)).isActive());
    }

    @Test
    public void godBridgeArmsAfterAManualPlacementAndSettlesItsPitch() {
        BridgeAssistBridgeModeStateMachine machine = new BridgeAssistBridgeModeStateMachine();
        machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(99, 0.0F, true, true, true));
        machine.recordManualPlacement();

        BridgeAssistBridgeModeStateMachine.Plan opening = machine.update(
                BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(100, 0.0F, true, true, true));
        assertTrue(opening.isActive());
        assertEquals(45.0F, opening.getSetupYaw(), 0.0F);
        assertEquals(81.0F, opening.getSetupPitch(), 0.0F);

        BridgeAssistBridgeModeStateMachine.Plan settled = machine.update(
                BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(110, 0.0F, true, true, true));
        assertEquals(83.0F, settled.getSetupPitch(), 0.0F);
    }

    @Test
    public void tellyRuntimeIsOwnedOutsideTheGenericStateMachine() {
        BridgeAssistBridgeModeStateMachine machine = new BridgeAssistBridgeModeStateMachine();
        machine.recordManualPlacement(BridgeAssistBridgeModeStateMachine.Mode.TellyBridge);

        BridgeAssistBridgeModeStateMachine.Plan plan = machine.update(
                BridgeAssistBridgeModeStateMachine.Mode.TellyBridge,
                frame(200, 45.0F, false, true, true));

        assertFalse(plan.isActive());
        assertEquals(0, machine.getAirborneTicks());
    }

    @Test
    public void resetDropsGodBridgeActivation() {
        BridgeAssistBridgeModeStateMachine machine = new BridgeAssistBridgeModeStateMachine();
        machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(300, 0.0F, true, true, true));
        machine.recordManualPlacement();
        machine.reset();

        assertFalse(machine.update(BridgeAssistBridgeModeStateMachine.Mode.GodBridge,
                frame(301, 0.0F, true, false, false)).isActive());
        assertEquals(0, machine.getAirborneTicks());
    }

    private static BridgeAssistBridgeModeStateMachine.Frame frame(int tick, float yaw,
                                                                   boolean onGround,
                                                                   boolean backward,
                                                                   boolean useItem) {
        return new BridgeAssistBridgeModeStateMachine.Frame(
                tick, yaw, onGround, backward, useItem);
    }
}
