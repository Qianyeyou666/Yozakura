package gq.yozakura.module.render;

import gq.yozakura.engine.render.ui.VisualPalette;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NightBloomWidgetMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void keyFeedbackUsesAContinuousOneHundredAndFiveMillisecondPressAndRelease() {
        NightBloomKeyFeedback feedback = new NightBloomKeyFeedback();
        feedback.setPressed(true);

        float earlyPress = feedback.update(0.020F);
        feedback.update(0.050F);
        float completedPress = feedback.update(0.035F);

        assertTrue(earlyPress > 0.0F);
        assertTrue(earlyPress < 1.0F);
        assertEquals(1.0F, completedPress, EPSILON);

        feedback.setPressed(false);
        float earlyRelease = feedback.update(0.020F);
        feedback.update(0.050F);
        float completedRelease = feedback.update(0.035F);

        assertTrue(earlyRelease > 0.0F);
        assertTrue(earlyRelease < 1.0F);
        assertEquals(0.0F, completedRelease, EPSILON);
    }

    @Test
    public void healthDamageTrailRemainsBehindDamageButDoesNotTrailHealing() {
        NightBloomHealthMotion motion = new NightBloomHealthMotion();
        NightBloomHealthMotion.Snapshot initial = motion.update(0.80F, 0.0F);
        NightBloomHealthMotion.Snapshot damaged = motion.update(0.40F, 0.050F);

        assertEquals(0.80F, initial.getHealth(), EPSILON);
        assertEquals(0.80F, initial.getDamageTrail(), EPSILON);
        assertTrue(damaged.getDamageTrail() > damaged.getHealth());

        NightBloomHealthMotion.Snapshot settled = damaged;
        for (int frame = 0; frame < 12; frame++) {
            settled = motion.update(0.40F, 0.050F);
        }
        assertTrue(settled.getDamageTrail() < 0.45F);

        NightBloomHealthMotion.Snapshot healing = motion.update(0.80F, 0.050F);
        assertEquals(healing.getHealth(), healing.getDamageTrail(), EPSILON);
    }

    @Test
    public void healthColorUsesNightBloomHealthTokens() {
        VisualPalette palette = VisualPalette.nightBloom();

        assertEquals(palette.getHealthLow(), NightBloomHealthMotion.colorFor(0.20F, palette));
        assertEquals(palette.getHealthMid(), NightBloomHealthMotion.colorFor(0.50F, palette));
        assertEquals(palette.getHealthHigh(), NightBloomHealthMotion.colorFor(0.85F, palette));
    }

    @Test
    public void moduleRowsEnterExitAndRetargetTheirVerticalPositionWithoutJumping() {
        NightBloomModuleRowMotion motion = new NightBloomModuleRowMotion();
        motion.setTargetY(12.0F);
        motion.setVisible(true);

        NightBloomModuleRowMotion.Snapshot entering = motion.update(0.080F);
        assertTrue(entering.getVisibility() > 0.0F);
        assertTrue(entering.getVisibility() < 1.0F);
        assertEquals(12.0F, entering.getY(), EPSILON);

        motion.update(0.050F);
        motion.update(0.050F);
        motion.update(0.020F);
        assertEquals(1.0F, motion.getVisibility(), EPSILON);

        motion.setTargetY(58.0F);
        NightBloomModuleRowMotion.Snapshot reordering = motion.update(0.100F);
        assertTrue(reordering.getY() > 12.0F);
        assertTrue(reordering.getY() < 58.0F);

        motion.setVisible(false);
        NightBloomModuleRowMotion.Snapshot leaving = motion.update(0.080F);
        assertTrue(leaving.getVisibility() > 0.0F);
        assertTrue(leaving.getVisibility() < 1.0F);

        motion.update(0.050F);
        motion.update(0.050F);
        motion.update(0.060F);
        assertTrue(motion.isFinishedExit());
    }
}
