package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppleTargetHudMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void acquireAnimatesInWithSpringMotionAndKeepsHealthFromZero() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();

        motion.acquire(7, 0.72F);
        advance(motion, 0.72F, false, 0.18F, 120);

        assertEquals(7, motion.getCurrentTargetId());
        assertTrue(motion.getVisibility() > 0.9F);
        assertEquals(0.72F, motion.getHealth(), EPSILON);
        assertEquals(0.72F, motion.getDamageTrail(), EPSILON);
        assertTrue(motion.getPanelScale() > 0.96F);
        assertTrue(motion.getPanelScale() < 1.0F);
        assertTrue(motion.getPanelYOffset() > 0.0F);
    }

    @Test
    public void releaseRetainsTargetUntilExitSpringFinishes() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(11, 0.80F);
        advance(motion, 0.80F, false, 0.18F, 120);

        motion.release();
        advance(motion, 0.80F, false, 0.07F, 120);

        assertEquals(11, motion.getCurrentTargetId());
        assertTrue(motion.hasRetainedTarget());
        assertTrue(motion.getVisibility() > 0.0F);
        assertTrue(motion.getVisibility() < 1.0F);

        advance(motion, 0.80F, false, 0.07F, 120);

        assertFalse(motion.hasRetainedTarget());
        assertEquals(AppleTargetHudMotion.NO_TARGET, motion.getCurrentTargetId());
    }

    @Test
    public void targetSwitchCrossfadesWithoutDroppingPanelVisibility() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(3, 0.90F);
        advance(motion, 0.90F, false, 0.18F, 144);

        motion.acquire(4, 0.35F);
        advance(motion, 0.35F, false, 0.07F, 144);

        assertEquals(4, motion.getCurrentTargetId());
        assertEquals(3, motion.getPreviousTargetId());
        assertEquals(1.0F, motion.getVisibility(), 0.02F);
        assertTrue(motion.getCurrentContentAlpha() > 0.0F);
        assertTrue(motion.getCurrentContentAlpha() < 1.0F);
        assertTrue(motion.getPreviousContentAlpha() > 0.0F);
        assertTrue(motion.getPreviousContentAlpha() < 1.0F);

        advance(motion, 0.35F, false, 0.07F, 144);

        assertEquals(AppleTargetHudMotion.NO_TARGET, motion.getPreviousTargetId());
        assertEquals(1.0F, motion.getCurrentContentAlpha(), 0.01F);
        assertEquals(0.0F, motion.getPreviousContentAlpha(), EPSILON);
    }

    @Test
    public void reacquiringDuringExitReversesFromCurrentVisibility() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(9, 0.65F);
        advance(motion, 0.65F, false, 0.18F, 120);
        motion.release();
        advance(motion, 0.65F, false, 0.07F, 120);

        float halfwayOut = motion.getVisibility();
        motion.acquire(9, 0.65F);
        advance(motion, 0.65F, false, 0.06F, 120);

        assertTrue(motion.getVisibility() > halfwayOut);
        assertTrue(motion.getVisibility() < 1.0F);
        assertEquals(9, motion.getCurrentTargetId());
        assertEquals(AppleTargetHudMotion.NO_TARGET, motion.getPreviousTargetId());
    }

    @Test
    public void healthAndDamageTrailRemainContinuousAcrossTargetSwitch() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(1, 0.90F);
        advance(motion, 0.90F, false, 0.18F, 120);

        motion.acquire(2, 0.25F);

        assertEquals(0.90F, motion.getHealth(), EPSILON);
        assertEquals(0.90F, motion.getDamageTrail(), EPSILON);

        advance(motion, 0.25F, false, 0.12F, 120);

        assertTrue(motion.getHealth() < 0.55F);
        assertTrue(motion.getHealth() > 0.25F);
        assertTrue(motion.getDamageTrail() > motion.getHealth());
    }

    @Test
    public void hurtFeedbackFadesWithoutOvershootingPastOne() {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(5, 0.75F);
        advance(motion, 0.75F, false, 0.18F, 120);

        motion.update(1.0F / 120.0F, 0.75F, true);
        float peak = motion.getHurt();
        assertTrue(peak > 0.0F);
        assertTrue(peak <= 1.0F);

        advance(motion, 0.75F, false, 0.14F, 120);

        assertTrue(motion.getHurt() < peak);
        assertTrue(motion.getHurt() >= 0.0F);
    }

    @Test
    public void motionIsNearlyIdenticalAtSixtyAndTwoHundredFortyFps() {
        AppleTargetHudMotion at60 = simulate(60);
        AppleTargetHudMotion at240 = simulate(240);

        assertEquals(at60.getVisibility(), at240.getVisibility(), 0.01F);
        assertEquals(at60.getCurrentContentAlpha(), at240.getCurrentContentAlpha(), 0.01F);
        assertEquals(at60.getHealth(), at240.getHealth(), 0.01F);
        assertEquals(at60.getDamageTrail(), at240.getDamageTrail(), 0.01F);
    }

    private static AppleTargetHudMotion simulate(int framesPerSecond) {
        AppleTargetHudMotion motion = new AppleTargetHudMotion();
        motion.acquire(21, 0.82F);
        advance(motion, 0.82F, false, 0.18F, framesPerSecond);
        motion.acquire(22, 0.31F);
        advance(motion, 0.31F, false, 0.09F, framesPerSecond);
        return motion;
    }

    private static void advance(AppleTargetHudMotion motion, float health, boolean hurt,
                                float seconds, int framesPerSecond) {
        int frames = Math.max(1, Math.round(seconds * framesPerSecond));
        float deltaSeconds = seconds / frames;
        for (int frame = 0; frame < frames; frame++) {
            motion.update(deltaSeconds, health, hurt);
        }
    }
}
