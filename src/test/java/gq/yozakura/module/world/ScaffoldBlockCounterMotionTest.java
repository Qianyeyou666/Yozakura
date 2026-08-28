package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldBlockCounterMotionTest {
    @Test
    public void counterUsesTheTargetHudRetentionTimingForEnterAndExit() {
        ScaffoldBlockCounterMotion motion = new ScaffoldBlockCounterMotion();
        motion.setVisible(true, 0L);
        assertTrue(motion.snapshot(250L).getOpacity() > 0.98F);
        assertTrue(motion.snapshot(250L).getScale() > 0.99F);

        motion.setVisible(false, 500L);
        assertTrue(motion.snapshot(700L).isRetained());
        assertFalse(motion.snapshot(1000L).isRetained());
    }
}
