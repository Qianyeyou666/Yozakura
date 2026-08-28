package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Locks the remaining motion wiring to the pinned Epsilon Panel implementation. */
public class EpsilonPanelMotionContractTest {
    @Test
    public void panelUsesFixedDurationHoverAndFocusAnimations() throws IOException {
        String source = source("YozakuraPanelClickGui.java");

        assertTrue(source.contains("EpsilonPanelAnimation.RAIL_MENU_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.RAIL_CONTENT_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.RAIL_SELECTION_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.RAIL_HOVER_POSITION_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.SEARCH_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.SEARCH_FOCUS_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.KEYBIND_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.KEYBIND_FOCUS_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.SEGMENT_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.SETTING_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.TOGGLE_HOVER_MS"));
    }

    @Test
    public void valueControlsUseEpsilonSliderAndPopupMotion() throws IOException {
        String source = source("ClickGuiValueRenderer.java");

        assertTrue(source.contains("EpsilonPanelAnimation.SLIDER_HOVER_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.SLIDER_PRESS_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.POPUP_OPEN_MS"));
        assertTrue(source.contains("EpsilonPanelAnimation.TOGGLE_HOVER_MS"));
        assertFalse(source.contains("pulseStart"));
        assertFalse(source.contains("staggerT"));
        assertFalse(source.contains("dd-opt-hover:"));
    }

    private static String source(String name) throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/" + name)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
