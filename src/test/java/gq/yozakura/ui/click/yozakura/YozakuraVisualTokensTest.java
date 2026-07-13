package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.VisualPalette;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YozakuraVisualTokensTest {
    @Test
    public void nightBloomKeepsSurfacesLowChromeAndSeparatesFocusFromHover() {
        VisualPalette visual = VisualPalette.nightBloom();
        YozakuraVisualTokens tokens = YozakuraVisualTokens.nightBloom();

        assertEquals(withAlpha(visual.getCanvas(), 164), tokens.backdrop);
        assertEquals(withAlpha(visual.getSurface(), 232), tokens.topBar);
        assertEquals(withAlpha(visual.getSurface(), 222), tokens.card);
        assertEquals(withAlpha(visual.getSurfaceRaised(), 232), tokens.cardHover);
        assertEquals(withAlpha(visual.getSurfaceOverlay(), 238), tokens.cardOpen);
        assertEquals(visual.getTextPrimary(), tokens.text);
        assertEquals(visual.getTextSecondary(), tokens.muted);
        assertEquals(visual.getTextDisabled(), tokens.faint);
        assertEquals(visual.getAccentPrimary(), tokens.accent);
        assertEquals(visual.getDanger(), tokens.danger);
        assertEquals(withAlpha(visual.getCanvas(), 154), tokens.glassFill);
        assertEquals(withAlpha(visual.getSurface(), 122), tokens.glassFillSoft);
        assertEquals(withAlpha(visual.getBorderSubtle(), 58), tokens.glassBorder);
        assertEquals(visual.getBorderFocus(), tokens.focusBorder);
        assertEquals(withAlpha(visual.getInfo(), 190), tokens.navHover);
        assertEquals(withAlpha(visual.getSurfaceOverlay(), 218), tokens.selectedFill);
        assertEquals(visual.getGlowPrimary(), tokens.switchGlow);
        assertEquals(withAlpha(visual.getInfo(), 178), tokens.valueTrack);
        assertEquals(withAlpha(visual.getAccentPrimary(), 230), tokens.valueFill);
        assertEquals(withAlpha(visual.getSurfaceOverlay(), 200), tokens.modeExpandedFill);
        assertEquals(withAlpha(visual.getAccentSoft(), 160), tokens.modeSelected);
        assertEquals(withAlpha(visual.getInfo(), 140), tokens.modeHover);
        assertEquals(withAlpha(visual.getSurfaceRaised(), 240), tokens.dropdown);
        assertEquals(withAlpha(visual.getShadow(), 200), tokens.dropdownShadow);
        assertEquals(withAlpha(visual.getShadow(), 210), tokens.shadow);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
