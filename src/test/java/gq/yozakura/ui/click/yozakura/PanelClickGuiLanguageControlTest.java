package gq.yozakura.ui.click.yozakura;

import gq.yozakura.core.ClientLanguage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelClickGuiLanguageControlTest {
    @Test
    public void settingsLanguageCardUsesSharedPanelGeometry() {
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 40.0f, 164.0f, 324.0f);
        PanelClickGuiLayout.Rect card = PanelClickGuiLanguageControl.cardBounds(panel);
        PanelClickGuiLayout.Rect segments = PanelClickGuiLanguageControl.segmentBounds(panel);

        assertEquals(103.0f, card.x(), 0.001f);
        assertEquals(74.0f, card.y(), 0.001f);
        assertEquals(158.0f, card.width(), 0.001f);
        assertEquals(34.0f, card.height(), 0.001f);
        assertEquals(146.0f, segments.x(), 0.001f);
        assertEquals(82.0f, segments.y(), 0.001f);
        assertEquals(110.0f, segments.width(), 0.001f);
        assertEquals(18.0f, segments.height(), 0.001f);
        assertEquals(112.0f,
                PanelClickGuiLanguageControl.settingsContentTop(panel), 0.001f);
        assertEquals(card.bottom() + PanelClickGuiLanguageControl.CONTENT_GAP,
                PanelClickGuiLanguageControl.settingsContentTop(panel), 0.001f);
    }

    @Test
    public void segmentMidpointSelectsTheRealClientLanguage() {
        PanelClickGuiLayout.Rect segments = new PanelClickGuiLayout.Rect(20.0f, 10.0f, 110.0f, 18.0f);
        assertEquals(ClientLanguage.ENGLISH,
                PanelClickGuiLanguageControl.languageAt(segments, 74.99f));
        assertEquals(ClientLanguage.CHINESE,
                PanelClickGuiLanguageControl.languageAt(segments, 75.0f));
    }
}
