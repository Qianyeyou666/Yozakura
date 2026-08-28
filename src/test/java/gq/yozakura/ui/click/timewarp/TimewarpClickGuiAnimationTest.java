package gq.yozakura.ui.click.timewarp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimewarpClickGuiAnimationTest {
    @Test
    public void transitionUsesMonotonicTimeAndCanReverseContinuously() {
        TimewarpClickGuiAnimation animation = new TimewarpClickGuiAnimation(200L, 160L);
        animation.reset(false, 0L);
        animation.progressAt(true, 0L);

        float halfwayOpen = animation.progressAt(true, 100_000_000L);
        float reverseStart = animation.progressAt(false, 100_000_000L);
        float halfwayClosed = animation.progressAt(false, 140_000_000L);

        assertEquals(0.5f, halfwayOpen, 0.001f);
        assertEquals(halfwayOpen, reverseStart, 0.001f);
        assertTrue(halfwayClosed < reverseStart);
        assertTrue(halfwayClosed > 0.0f);
    }

    @Test
    public void staggerKeepsRowsOrderedAndEventuallyCompletes() {
        float first = TimewarpClickGuiAnimation.stagger(0.50f, 0, 6);
        float third = TimewarpClickGuiAnimation.stagger(0.50f, 2, 6);
        float finalRow = TimewarpClickGuiAnimation.stagger(1.0f, 5, 6);

        assertTrue(first > third);
        assertEquals(1.0f, finalRow, 0.001f);
    }

    @Test
    public void easedProgressStaysBounded() {
        assertEquals(0.0f, TimewarpClickGuiAnimation.easeOutCubic(-1.0f), 0.001f);
        assertEquals(1.0f, TimewarpClickGuiAnimation.easeOutCubic(2.0f), 0.001f);
        assertTrue(TimewarpClickGuiAnimation.easeOutCubic(0.5f) > 0.5f);
    }
}
