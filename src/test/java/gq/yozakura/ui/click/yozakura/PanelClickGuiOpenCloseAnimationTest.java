package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelClickGuiOpenCloseAnimationTest {
    @Test
    public void openingAndClosingUseFixedMonotonicDurations() {
        PanelClickGuiOpenCloseAnimation animation = new PanelClickGuiOpenCloseAnimation(220L, 180L);
        animation.reset(false, 1_000_000_000L);

        assertEquals(0.0f, animation.progressAt(true, 1_000_000_000L), 0.0001f);
        assertEquals(0.5f, animation.progressAt(true, 1_110_000_000L), 0.0001f);
        assertEquals(1.0f, animation.progressAt(true, 1_220_000_000L), 0.0001f);
        assertTrue(animation.isOpen());

        assertEquals(1.0f, animation.progressAt(false, 1_220_000_000L), 0.0001f);
        assertEquals(0.5f, animation.progressAt(false, 1_310_000_000L), 0.0001f);
        assertEquals(0.0f, animation.progressAt(false, 1_400_000_000L), 0.0001f);
        assertTrue(animation.isClosed());
    }

    @Test
    public void reversingMidFlightContinuesFromTheCurrentProgress() {
        PanelClickGuiOpenCloseAnimation animation = new PanelClickGuiOpenCloseAnimation(220L, 180L);
        animation.reset(false, 0L);
        animation.progressAt(true, 0L);

        float halfwayOpen = animation.progressAt(true, 110_000_000L);
        float reversalFrame = animation.progressAt(false, 110_000_000L);
        float later = animation.progressAt(false, 155_000_000L);

        assertEquals(0.5f, halfwayOpen, 0.0001f);
        assertEquals(halfwayOpen, reversalFrame, 0.0001f);
        assertTrue(later < reversalFrame);
        assertFalse(animation.isClosed());
    }

    @Test
    public void elapsedTimeNotFrameCountControlsProgress() {
        PanelClickGuiOpenCloseAnimation oneFrame = new PanelClickGuiOpenCloseAnimation(220L, 180L);
        PanelClickGuiOpenCloseAnimation manyFrames = new PanelClickGuiOpenCloseAnimation(220L, 180L);
        oneFrame.reset(false, 0L);
        manyFrames.reset(false, 0L);
        oneFrame.progressAt(true, 0L);
        manyFrames.progressAt(true, 0L);

        float direct = oneFrame.progressAt(true, 132_000_000L);
        manyFrames.progressAt(true, 16_000_000L);
        manyFrames.progressAt(true, 33_000_000L);
        manyFrames.progressAt(true, 67_000_000L);
        float stepped = manyFrames.progressAt(true, 132_000_000L);

        assertEquals(direct, stepped, 0.0001f);
        assertEquals(0.6f, direct, 0.0001f);
    }

    @Test
    public void visualProgressUsesOneSymmetricCurveForBothDirections() {
        PanelClickGuiOpenCloseAnimation animation = new PanelClickGuiOpenCloseAnimation(220L, 180L);

        assertEquals(0.0f, animation.visualProgress(0.0f), 0.0001f);
        assertEquals(0.5f, animation.visualProgress(0.5f), 0.0001f);
        assertEquals(1.0f, animation.visualProgress(1.0f), 0.0001f);
        assertEquals(1.0f - animation.visualProgress(0.25f),
                animation.visualProgress(0.75f), 0.0001f);
    }
}
