package gq.yozakura.bridge.modern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernAimMathTest {
    @Test
    public void bowVelocityUsesLegacyChargeCurve() {
        assertEquals(0.0D, ModernAimMath.bowVelocity(0), 0.000001D);
        assertEquals(5.0D / 12.0D, ModernAimMath.bowVelocity(10), 0.000001D);
        assertEquals(1.0D, ModernAimMath.bowVelocity(20), 0.000001D);
        assertEquals(1.0D, ModernAimMath.bowVelocity(40), 0.000001D);
    }

    @Test
    public void lowArcSolutionUsesLegacyGravity() {
        ModernAimMath.BallisticSolution solution = ModernAimMath.solveLowArc(
                0.0D, 0.0D, 20.0D, 1.0D, 0.006D);

        assertTrue(solution.isReachable());
        assertEquals(0.0D, solution.getYaw(), 0.0001D);
        assertEquals(-3.449D, solution.getPitch(), 0.01D);
    }

    @Test
    public void unreachableOrUnchargedShotsDoNotRotate() {
        assertFalse(ModernAimMath.solveLowArc(
                0.0D, 20.0D, 100.0D, 0.2D, 0.006D).isReachable());
        assertFalse(ModernAimMath.solveLowArc(
                0.0D, 0.0D, 5.0D, 0.0D, 0.006D).isReachable());
    }

    @Test
    public void predictionMatchesLegacyAxisRules() {
        ModernAimMath.PredictedPoint point = ModernAimMath.predict(
                10.0D, 5.0D, -3.0D,
                8.0D, 4.0D, -5.0D,
                1.62D, 1.5D);

        assertEquals(13.0D, point.getX(), 0.000001D);
        assertEquals(7.27D, point.getY(), 0.000001D);
        assertEquals(0.0D, point.getZ(), 0.000001D);
    }
}
