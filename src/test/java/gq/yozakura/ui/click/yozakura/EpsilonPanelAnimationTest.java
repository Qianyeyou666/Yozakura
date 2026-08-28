package gq.yozakura.ui.click.yozakura;

import gq.yozakura.util.animation.AnimationUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpsilonPanelAnimationTest {
    @Test
    public void pinnedDurationsRemainExplicit() {
        assertEquals(220L, EpsilonPanelAnimation.RAIL_HEADER_TITLE_MS);
        assertEquals(260L, EpsilonPanelAnimation.RAIL_HEADER_SUBTITLE_MS);
        assertEquals(220L, EpsilonPanelAnimation.RAIL_HEADER_DIVIDER_MS);
        assertEquals(120L, EpsilonPanelAnimation.MODULE_HOVER_MS);
        assertEquals(160L, EpsilonPanelAnimation.MODULE_SELECTION_MS);
        assertEquals(620L, EpsilonPanelAnimation.TOGGLE_MS);
        assertEquals(180L, EpsilonPanelAnimation.SEGMENT_SELECTION_MS);
        assertEquals(150L, EpsilonPanelAnimation.KEYBIND_FOCUS_MS);
        assertEquals(120L, EpsilonPanelAnimation.SEARCH_HOVER_MS);
        assertEquals(120L, EpsilonPanelAnimation.SEARCH_FOCUS_MS);
        assertEquals(120L, EpsilonPanelAnimation.SETTING_HOVER_MS);
        assertEquals(150L, EpsilonPanelAnimation.SLIDER_HOVER_MS);
        assertEquals(120L, EpsilonPanelAnimation.SLIDER_PRESS_MS);
        assertEquals(140L, EpsilonPanelAnimation.POPUP_OPEN_MS);
    }

    @Test
    public void durationAnimationFinishesExactlyAndReversesWithoutJumping() {
        EpsilonPanelAnimation.Value value = new EpsilonPanelAnimation.Value(0.0f);
        assertEquals(0.0f, value.valueAt(0.0f, 0L, 160L, AnimationUtil.Ease.OUT_CUBIC), 0.001f);
        assertEquals(0.0f, value.valueAt(1.0f, 1000L, 160L, AnimationUtil.Ease.OUT_CUBIC), 0.001f);
        float moving = value.valueAt(1.0f, 1080L, 160L, AnimationUtil.Ease.OUT_CUBIC);
        assertEquals(0.875f, moving, 0.001f);
        assertEquals(moving, value.valueAt(0.0f, 1080L, 160L, AnimationUtil.Ease.OUT_CUBIC), 0.001f);
        assertEquals(0.0f, value.valueAt(0.0f, 1240L, 160L, AnimationUtil.Ease.OUT_CUBIC), 0.001f);
    }

    @Test
    public void elasticEaseOvershootsBeforeSettling() {
        assertTrue(AnimationUtil.ease(0.2f, AnimationUtil.Ease.OUT_ELASTIC) > 1.0f);
        assertEquals(1.0f, AnimationUtil.ease(1.0f, AnimationUtil.Ease.OUT_ELASTIC), 0.001f);
    }
}
