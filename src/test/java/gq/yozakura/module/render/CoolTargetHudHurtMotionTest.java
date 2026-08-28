package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoolTargetHudHurtMotionTest {
    @Test
    public void impactEnvelopeStartsStrongAndSettlesWithinOneShortPulse() {
        CoolTargetHudHurtMotion motion = new CoolTargetHudHurtMotion();

        motion.trigger(100L);
        assertEquals(1.0F, motion.snapshot(100L).getIntensity(), 0.0F);
        assertTrue(motion.snapshot(160L).getIntensity() > 0.60F);
        assertEquals(0.0F, motion.snapshot(280L).getIntensity(), 0.0F);
    }
}
