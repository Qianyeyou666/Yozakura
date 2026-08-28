package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NymphTargetHudMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void openingUsesTheSourceSixthAndEighthPowerCurves() {
        NymphTargetHudMotion motion = new NymphTargetHudMotion();
        motion.setVisible(true, 1000L);

        NymphTargetHudMotion.Snapshot halfway = motion.snapshot(1250L);
        assertEquals(0.984375F, halfway.getOpacity(), EPSILON);
        assertEquals(0.99609375F, halfway.getScale(), EPSILON);
        assertTrue(halfway.isRetained());
    }

    @Test
    public void closingRetainsContentUntilTheSourceCurveCompletes() {
        NymphTargetHudMotion motion = new NymphTargetHudMotion();
        motion.setVisible(true, 0L);
        motion.snapshot(500L);
        motion.setVisible(false, 500L);

        NymphTargetHudMotion.Snapshot halfway = motion.snapshot(750L);
        assertEquals(0.015625F, halfway.getOpacity(), EPSILON);
        assertEquals(0.00390625F, halfway.getScale(), EPSILON);
        assertTrue(halfway.isRetained());

        assertFalse(motion.snapshot(1000L).isRetained());
    }

    @Test
    public void healthResponseIsFrameRateIndependent() {
        NymphTargetHudMotion at60 = new NymphTargetHudMotion();
        NymphTargetHudMotion at240 = new NymphTargetHudMotion();
        at60.snapHealth(1.0F);
        at240.snapHealth(1.0F);

        advanceHealth(at60, 0.25F, 0.5F, 60);
        advanceHealth(at240, 0.25F, 0.5F, 240);

        assertEquals(at60.getHealth(), at240.getHealth(), 0.001F);
        assertTrue(at60.getHealth() > 0.25F);
        assertTrue(at60.getHealth() < 0.45F);
    }

    private static void advanceHealth(NymphTargetHudMotion motion, float target,
                                      float seconds, int framesPerSecond) {
        int frames = Math.round(seconds * framesPerSecond);
        for (int frame = 0; frame < frames; frame++) {
            motion.updateHealth(target, 1.0F / framesPerSecond);
        }
    }
}
