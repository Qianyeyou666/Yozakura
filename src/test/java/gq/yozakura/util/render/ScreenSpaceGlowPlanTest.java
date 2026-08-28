package gq.yozakura.util.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScreenSpaceGlowPlanTest {
    @Test
    public void emptyBatchDoesNotScheduleMaskOrPostProcessPasses() {
        ScreenSpaceGlowPlan plan = ScreenSpaceGlowPlan.forBatch(0, 0,
                ScreenSpaceGlowPlan.Quality.MEDIUM);

        assertEquals(0, plan.getMaskPassCount());
        assertEquals(0, plan.getPostProcessPassCount());
    }

    @Test
    public void allCollectedTargetsShareOneMaskBatchAndFixedPostProcessPasses() {
        ScreenSpaceGlowPlan plan = ScreenSpaceGlowPlan.forBatch(17, 31,
                ScreenSpaceGlowPlan.Quality.HIGH);

        assertEquals(48, plan.getCollectedTargetCount());
        assertEquals(1, plan.getMaskPassCount());
        assertEquals(ScreenSpaceGlowPlan.FIXED_POST_PROCESS_PASS_COUNT,
                plan.getPostProcessPassCount());
    }

    @Test
    public void radiiStayInsideTheShaderKernelBudgetForEveryQuality() {
        for (ScreenSpaceGlowPlan.Quality quality : ScreenSpaceGlowPlan.Quality.values()) {
            ScreenSpaceGlowPlan plan = ScreenSpaceGlowPlan.forBatch(1, 0, quality);

            assertTrue(plan.getOutlineRadius() >= 1);
            assertTrue(plan.getOutlineRadius() <= ScreenSpaceGlowPlan.MAX_OUTLINE_RADIUS);
            assertTrue(plan.getOuterBlurRadius() >= plan.getCoreBlurRadius());
            assertTrue(plan.getOuterBlurRadius() <= ScreenSpaceGlowPlan.MAX_BLUR_RADIUS);
            assertTrue(plan.getCoreBlurRadius() >= 1);
        }
    }

    @Test
    public void compositeStrengthIsBounded() {
        assertEquals(0.0f, ScreenSpaceGlowPlan.clampStrength(-2.0f), 0.0f);
        assertEquals(1.0f, ScreenSpaceGlowPlan.clampStrength(5.0f), 0.0f);
        assertEquals(0.65f, ScreenSpaceGlowPlan.clampStrength(0.65f), 0.0f);
    }

    @Test
    public void mediumWorldGlowUsesHalfResolutionTargetsWithoutCollapsingSmallViewports() {
        ScreenSpaceGlowPlan plan = ScreenSpaceGlowPlan.forBatch(1, 0,
                ScreenSpaceGlowPlan.Quality.MEDIUM);

        assertEquals(0.5f, plan.getFramebufferScale(), 0.0f);
        assertEquals(960, plan.scaleDimension(1920));
        assertEquals(540, plan.scaleDimension(1080));
        assertEquals(1, plan.scaleDimension(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void compositeStrengthRejectsNonFiniteValues() {
        ScreenSpaceGlowPlan.clampStrength(Float.NaN);
    }
}
