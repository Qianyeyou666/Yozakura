package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NymphArrayListRowMotionTest {
    @Test
    public void disabledRowStaysRetainedUntilItsSourceDecelerateExitCompletes() {
        NymphArrayListRowMotion motion = new NymphArrayListRowMotion();
        motion.setVisible(true, 0L);
        assertTrue(motion.snapshot(500L).isRetained());

        motion.setVisible(false, 500L);
        NymphArrayListRowMotion.Snapshot exiting = motion.snapshot(650L);
        assertTrue(exiting.isRetained());
        assertTrue(exiting.getProgress() > 0.0F);
        assertTrue(exiting.getProgress() < 1.0F);
        assertFalse(motion.snapshot(1000L).isRetained());
    }

    @Test
    public void reversingAnExitKeepsTheRowVisibleWithoutJumpingToZero() {
        NymphArrayListRowMotion motion = new NymphArrayListRowMotion();
        motion.setVisible(true, 0L);
        motion.snapshot(500L);
        motion.setVisible(false, 500L);
        float exitingProgress = motion.snapshot(650L).getProgress();

        motion.setVisible(true, 650L);
        float resumedProgress = motion.snapshot(650L).getProgress();

        assertTrue(resumedProgress >= exitingProgress - 0.001F);
        assertTrue(motion.snapshot(1150L).getProgress() > 0.99F);
    }
}
