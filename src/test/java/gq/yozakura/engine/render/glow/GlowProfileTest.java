package gq.yozakura.engine.render.glow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GlowProfileTest {
    private static final float EPSILON = 0.000001F;

    @Test
    public void profilesExposeTheirVisualIntentRadius() {
        assertEquals(3.0F, GlowProfile.TEXT.getLogicalRadius(), EPSILON);
        assertEquals(4.5F, GlowProfile.ACCENT.getLogicalRadius(), EPSILON);
        assertEquals(6.0F, GlowProfile.SHADOW.getLogicalRadius(), EPSILON);
        assertEquals(8.0F, GlowProfile.PANEL.getLogicalRadius(), EPSILON);
    }

    @Test
    public void qualityControlsDownsampleScale() {
        assertEquals(0.40F, GlowProfile.Quality.LOW.getDownsample(), EPSILON);
        assertEquals(0.50F, GlowProfile.Quality.MEDIUM.getDownsample(), EPSILON);
        assertEquals(0.75F, GlowProfile.Quality.HIGH.getDownsample(), EPSILON);
    }

    @Test
    public void kernelRadiusAccountsForGuiScaleAndRenderQuality() {
        assertEquals(2, GlowProfile.TEXT.resolveKernelRadius(1.0F, GlowProfile.Quality.MEDIUM));
        assertEquals(6, GlowProfile.TEXT.resolveKernelRadius(4.0F, GlowProfile.Quality.MEDIUM));
        assertEquals(24, GlowProfile.PANEL.resolveKernelRadius(4.0F, GlowProfile.Quality.HIGH));
        assertTrue(GlowProfile.PANEL.resolveKernelRadius(4.0F, GlowProfile.Quality.HIGH)
                <= GaussianKernel.MAX_RADIUS);
    }

    @Test
    public void strengthClampsToUnitRangeWithoutLiftingFadeTail() {
        assertEquals(0.0F, GlowProfile.clampStrength(-0.5F), EPSILON);
        assertEquals(0.0F, GlowProfile.clampStrength(0.0F), EPSILON);
        assertEquals(0.001F, GlowProfile.clampStrength(0.001F), EPSILON);
        assertEquals(1.0F, GlowProfile.clampStrength(1.5F), EPSILON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteStrength() {
        GlowProfile.clampStrength(Float.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidGuiScale() {
        GlowProfile.TEXT.resolveKernelRadius(0.0F, GlowProfile.Quality.MEDIUM);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingQuality() {
        GlowProfile.TEXT.resolveKernelRadius(1.0F, null);
    }
}
