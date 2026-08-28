package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoolTargetHudNumberMotionTest {
    @Test
    public void healthNumberEasesTowardDamageAndPulsesWhenItsDisplayedDigitChanges() {
        CoolTargetHudNumberMotion motion = new CoolTargetHudNumberMotion();

        motion.snap(12, 20.0F);
        CoolTargetHudNumberMotion.Snapshot first = motion.update(12, 10.0F, 0.05F);
        assertTrue(first.getHealth() < 20.0F);
        assertTrue(first.getHealth() > 10.0F);
        assertTrue(first.getScale() > 1.0F);

        CoolTargetHudNumberMotion.Snapshot settled = first;
        for (int i = 0; i < 40; i++) {
            settled = motion.update(12, 10.0F, 0.05F);
        }
        assertEquals(10.0F, settled.getHealth(), 0.05F);
        assertEquals(1.0F, settled.getScale(), 0.001F);
    }
}
