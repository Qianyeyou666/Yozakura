package gq.yozakura.util.animation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UiMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void clockClampsElapsedTimeToTheUiBudget() {
        UiClock clock = new UiClock();

        assertEquals(0.0F, clock.tick(1_000_000_000L), EPSILON);
        assertEquals(0.02F, clock.tick(1_020_000_000L), EPSILON);
        assertEquals(0.05F, clock.tick(1_220_000_000L), EPSILON);
        assertEquals(0.0F, clock.tick(1_210_000_000L), EPSILON);
    }

    @Test
    public void tweenUsesOutCubicWithoutRestartingForTheSameTarget() {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(100.0F);

        float firstFrame = value.updateTween(0.025F, 0.20F);
        value.setTarget(100.0F);
        float secondFrame = value.updateTween(0.025F, 0.20F);

        assertEquals(100.0F * (1.0F - 0.875F * 0.875F * 0.875F), firstFrame, EPSILON);
        assertEquals(100.0F * (1.0F - 0.75F * 0.75F * 0.75F), secondFrame, EPSILON);
        assertTrue(secondFrame > firstFrame);
    }

    @Test
    public void tweenClampsDirectDeltaTimeAsWellAsTheClock() {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(1.0F);

        float current = value.updateTween(1.0F, 0.20F);

        assertEquals(1.0F - 0.75F * 0.75F * 0.75F, current, EPSILON);
    }

    @Test
    public void tweenProducesTheSamePositionAtSixtyAndTwoHundredFortyFps() {
        assertEquals(simulateTween(60), simulateTween(240), EPSILON);
    }

    @Test
    public void criticallyDampedSpringConvergesWithoutOvershooting() {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(1.0F);

        for (int frame = 0; frame < 72; frame++) {
            value.updateSpring(1.0F / 240.0F, 0.18F);
            assertTrue(value.get() >= 0.0F);
            assertTrue(value.get() <= 1.0F);
        }

        assertTrue(value.get() > 0.95F);
        assertEquals(simulateSpring(60), simulateSpring(240), 0.001F);
    }

    @Test
    public void springRetargetingDoesNotCarryMomentumPastTheNewTarget() {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(1.0F);
        value.updateSpring(0.03F, 0.18F);
        value.setTarget(0.50F);

        for (int frame = 0; frame < 120; frame++) {
            value.updateSpring(1.0F / 240.0F, 0.18F);
            assertTrue(value.get() <= 0.50F + EPSILON);
        }
    }

    @Test
    public void reducedAndOffModesReduceOrEliminateMotion() {
        MotionValue full = new MotionValue(0.0F, MotionValue.Mode.FULL);
        full.setTarget(1.0F);
        float fullValue = full.updateTween(0.05F, 0.40F);

        MotionValue reduced = new MotionValue(0.0F, MotionValue.Mode.REDUCED);
        reduced.setTarget(1.0F);
        float reducedValue = reduced.updateTween(0.05F, 0.40F);

        MotionValue off = new MotionValue(0.0F, MotionValue.Mode.OFF);
        off.setTarget(1.0F);
        float offValue = off.updateTween(0.05F, 0.40F);

        assertTrue(reducedValue > fullValue);
        assertEquals(1.0F, offValue, EPSILON);
    }

    private static float simulateTween(int framesPerSecond) {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(1.0F);
        for (int frame = 0; frame < framesPerSecond / 4; frame++) {
            value.updateTween(1.0F / framesPerSecond, 0.20F);
        }
        return value.get();
    }

    private static float simulateSpring(int framesPerSecond) {
        MotionValue value = new MotionValue(0.0F);
        value.setTarget(1.0F);
        for (int frame = 0; frame < framesPerSecond / 3; frame++) {
            value.updateSpring(1.0F / framesPerSecond, 0.18F);
        }
        return value.get();
    }
}
