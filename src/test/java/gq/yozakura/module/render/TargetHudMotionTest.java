package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetHudMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void acquireAnimatesInWithoutStartingHealthAtZero() {
        TargetHudMotion motion = new TargetHudMotion();

        motion.acquire(7, 0.72F);
        advance(motion, 0.72F, 0.09F, 120);

        assertEquals(7, motion.getCurrentTargetId());
        assertEquals(0.5F, motion.getVisibility(), 0.02F);
        assertEquals(0.72F, motion.getHealth(), EPSILON);
        assertEquals(0.72F, motion.getDamageTrail(), EPSILON);
        assertTrue(motion.getPanelScale() > 0.96F);
        assertTrue(motion.getPanelScale() < 1.0F);
        assertTrue(motion.getPanelYOffset() > 0.0F);
    }

    @Test
    public void releaseRetainsTargetUntilExitAnimationFinishes() {
        TargetHudMotion motion = new TargetHudMotion();
        motion.acquire(11, 0.80F);
        advance(motion, 0.80F, 0.18F, 120);

        motion.release();
        advance(motion, 0.80F, 0.07F, 120);

        assertEquals(11, motion.getCurrentTargetId());
        assertEquals(0.5F, motion.getVisibility(), 0.02F);

        advance(motion, 0.80F, 0.07F, 120);

        assertFalse(motion.hasRetainedTarget());
        assertEquals(TargetHudMotion.NO_TARGET, motion.getCurrentTargetId());
        assertEquals(0.0F, motion.getVisibility(), EPSILON);
    }

    @Test
    public void targetSwitchCrossfadesWithoutDroppingPanelVisibility() {
        TargetHudMotion motion = new TargetHudMotion();
        motion.acquire(3, 0.90F);
        advance(motion, 0.90F, 0.18F, 144);

        motion.acquire(4, 0.35F);
        advance(motion, 0.35F, 0.07F, 144);

        assertEquals(4, motion.getCurrentTargetId());
        assertEquals(3, motion.getPreviousTargetId());
        assertEquals(1.0F, motion.getVisibility(), EPSILON);
        assertEquals(0.5F, motion.getCurrentContentAlpha(), 0.03F);
        assertEquals(0.5F, motion.getPreviousContentAlpha(), 0.03F);

        advance(motion, 0.35F, 0.07F, 144);

        assertEquals(TargetHudMotion.NO_TARGET, motion.getPreviousTargetId());
        assertEquals(1.0F, motion.getCurrentContentAlpha(), EPSILON);
        assertEquals(0.0F, motion.getPreviousContentAlpha(), EPSILON);
    }

    @Test
    public void reacquiringDuringExitReversesFromTheCurrentVisibility() {
        TargetHudMotion motion = new TargetHudMotion();
        motion.acquire(9, 0.65F);
        advance(motion, 0.65F, 0.18F, 120);
        motion.release();
        advance(motion, 0.65F, 0.07F, 120);

        float halfwayOut = motion.getVisibility();
        motion.acquire(9, 0.65F);
        motion.update(1.0F / 120.0F, 0.65F);

        assertTrue(motion.getVisibility() > halfwayOut);
        assertTrue(motion.getVisibility() < 1.0F);
        assertEquals(9, motion.getCurrentTargetId());
        assertEquals(TargetHudMotion.NO_TARGET, motion.getPreviousTargetId());
    }

    @Test
    public void healthAndDamageTrailRemainContinuousAcrossTargetSwitch() {
        TargetHudMotion motion = new TargetHudMotion();
        motion.acquire(1, 0.90F);
        advance(motion, 0.90F, 0.18F, 120);

        motion.acquire(2, 0.25F);

        assertEquals(0.90F, motion.getHealth(), EPSILON);
        assertEquals(0.90F, motion.getDamageTrail(), EPSILON);

        advance(motion, 0.25F, 0.12F, 120);

        assertTrue(motion.getHealth() < 0.55F);
        assertTrue(motion.getHealth() > 0.25F);
        assertTrue(motion.getDamageTrail() > motion.getHealth());
    }

    @Test
    public void motionIsNearlyIdenticalAtSixtyAndTwoHundredFortyFps() {
        TargetHudMotion at60 = simulate(60);
        TargetHudMotion at240 = simulate(240);

        assertEquals(at60.getVisibility(), at240.getVisibility(), 0.002F);
        assertEquals(at60.getCurrentContentAlpha(), at240.getCurrentContentAlpha(), 0.002F);
        assertEquals(at60.getHealth(), at240.getHealth(), 0.002F);
        assertEquals(at60.getDamageTrail(), at240.getDamageTrail(), 0.002F);
    }

    private static TargetHudMotion simulate(int framesPerSecond) {
        TargetHudMotion motion = new TargetHudMotion();
        motion.acquire(21, 0.82F);
        advance(motion, 0.82F, 0.18F, framesPerSecond);
        motion.acquire(22, 0.31F);
        advance(motion, 0.31F, 0.09F, framesPerSecond);
        return motion;
    }

    private static void advance(TargetHudMotion motion, float health, float seconds, int framesPerSecond) {
        int frames = Math.max(1, Math.round(seconds * framesPerSecond));
        float deltaSeconds = seconds / frames;
        for (int frame = 0; frame < frames; frame++) {
            motion.update(deltaSeconds, health);
        }
    }
}
