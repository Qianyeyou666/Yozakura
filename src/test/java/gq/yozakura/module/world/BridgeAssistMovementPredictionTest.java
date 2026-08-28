package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BridgeAssistMovementPredictionTest {
    @Test
    public void calculatesGroundInputAccelerationWithVanillaFriction() {
        assertEquals(0.1D,
                BridgeAssistMovementPrediction.calculateInputAcceleration(true, 0.1F, 0.02F, 0.6F),
                1.0E-6D);
    }

    @Test
    public void keepsTheVanillaAirAcceleration() {
        assertEquals(0.02D,
                BridgeAssistMovementPrediction.calculateInputAcceleration(false, 0.1F, 0.02F, 0.6F),
                1.0E-6D);
    }

    @Test
    public void reducesGroundAccelerationOnSlipperyBlocks() {
        assertEquals(0.02294962D,
                BridgeAssistMovementPrediction.calculateInputAcceleration(true, 0.1F, 0.02F, 0.98F),
                1.0E-6D);
    }

    @Test
    public void convertsTickAlignedDelayValuesWithoutAddingAnExtraTick() {
        assertEquals(0, BridgeAssistMovementPrediction.ticksFromMillis(0.0D));
        assertEquals(1, BridgeAssistMovementPrediction.ticksFromMillis(50.0D));
        assertEquals(2, BridgeAssistMovementPrediction.ticksFromMillis(100.0D));
        assertEquals(6, BridgeAssistMovementPrediction.ticksFromMillis(300.0D));
        assertEquals(2, BridgeAssistMovementPrediction.ticksFromMillis(55.0D));
    }

    @Test
    public void calculatesForwardAndDiagonalInputMotionWithoutASpeedBoost() {
        assertVector(0.0D, 0.1D,
                BridgeAssistMovementPrediction.calculateInputMotion(1.0F, 0.0F, 0.1D, 0.0F));
        assertVector(0.070710678D, 0.070710678D,
                BridgeAssistMovementPrediction.calculateInputMotion(1.0F, 1.0F, 0.1D, 0.0F));
    }

    @Test
    public void scalesPredictedInputToTheVanillaSneakSpeedWhileSneakIsHeld() {
        assertVector(0.0D, 0.03D,
                BridgeAssistMovementPrediction.calculateInputMotion(
                        1.0F, 0.0F, 0.1D, 0.0F, true));
        assertVector(0.0D, 0.1D,
                BridgeAssistMovementPrediction.calculateInputMotion(
                        1.0F, 0.0F, 0.1D, 0.0F, false));
    }

    @Test
    public void keepsSneakPredictionWhileTheModuleOwnsTheHold() {
        assertEquals(true, BridgeAssistMovementPrediction.shouldApplySneakMultiplier(
                false, true, false, false));
        assertEquals(true, BridgeAssistMovementPrediction.shouldApplySneakMultiplier(
                true, false, false, false));
    }

    @Test
    public void restoresFullSpeedPredictionOnALegitPlacementCommit() {
        assertEquals(false, BridgeAssistMovementPrediction.shouldApplySneakMultiplier(
                false, true, true, false));
        assertEquals(true, BridgeAssistMovementPrediction.shouldApplySneakMultiplier(
                false, true, true, true));
    }

    @Test
    public void leavesZeroInputStationary() {
        assertVector(0.0D, 0.0D,
                BridgeAssistMovementPrediction.calculateInputMotion(0.0F, 0.0F, 0.1D, 0.0F));
    }

    private static void assertVector(double expectedX, double expectedZ, double[] actual) {
        assertEquals(expectedX, actual[0], 1.0E-6D);
        assertEquals(expectedZ, actual[1], 1.0E-6D);
    }
}
