package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelClickGuiRailAnimationTest {
    @Test
    public void expansionUsesEpsilonDurationAndCubicEase() {
        PanelClickGuiRailAnimation animation = new PanelClickGuiRailAnimation(42.0f);

        assertEquals(42.0f, animation.valueAt(false, 0L), 0.001f);
        assertEquals(42.0f, animation.valueAt(true, 1000L), 0.001f);
        assertEquals(110.25f, animation.valueAt(true, 1120L), 0.001f);
        assertEquals(120.0f, animation.valueAt(true, 1240L), 0.001f);
    }

    @Test
    public void reversingStartsFromCurrentAnimatedWidthWithoutJumping() {
        PanelClickGuiRailAnimation animation = new PanelClickGuiRailAnimation(42.0f);

        animation.valueAt(false, 0L);
        animation.valueAt(true, 1000L);
        float halfwayOpen = animation.valueAt(true, 1120L);
        float reversalStart = animation.valueAt(false, 1120L);
        float halfwayClosed = animation.valueAt(false, 1240L);

        assertEquals(110.25f, halfwayOpen, 0.001f);
        assertEquals(halfwayOpen, reversalStart, 0.001f);
        assertEquals(50.53125f, halfwayClosed, 0.001f);
        assertEquals(42.0f, animation.valueAt(false, 1360L), 0.001f);
    }
}
