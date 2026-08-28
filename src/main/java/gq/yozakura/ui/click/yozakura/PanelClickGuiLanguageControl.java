package gq.yozakura.ui.click.yozakura;

import gq.yozakura.core.ClientLanguage;

/** Shared draw and hit-test geometry for the Panel client-language selector. */
public final class PanelClickGuiLanguageControl {
    public static final float CARD_HEIGHT = 34.0f;
    public static final float SEGMENT_WIDTH = 110.0f;
    public static final float SEGMENT_HEIGHT = 18.0f;
    public static final float TRAILING_INSET = 5.0f;
    public static final float CONTENT_GAP = 4.0f;

    private PanelClickGuiLanguageControl() {
    }

    public static PanelClickGuiLayout.Rect cardBounds(PanelClickGuiLayout.Rect modulePanel) {
        return new PanelClickGuiLayout.Rect(
                modulePanel.x() + 3.0f,
                modulePanel.y() + 34.0f,
                modulePanel.width() - 6.0f,
                CARD_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect segmentBounds(PanelClickGuiLayout.Rect modulePanel) {
        PanelClickGuiLayout.Rect card = cardBounds(modulePanel);
        return new PanelClickGuiLayout.Rect(
                card.right() - TRAILING_INSET - SEGMENT_WIDTH,
                card.y() + (card.height() - SEGMENT_HEIGHT) * 0.5f,
                SEGMENT_WIDTH,
                SEGMENT_HEIGHT);
    }

    public static float settingsContentTop(PanelClickGuiLayout.Rect modulePanel) {
        return cardBounds(modulePanel).bottom() + CONTENT_GAP;
    }

    public static ClientLanguage languageAt(PanelClickGuiLayout.Rect segments, float mouseX) {
        return mouseX < segments.x() + segments.width() * 0.5f
                ? ClientLanguage.ENGLISH : ClientLanguage.CHINESE;
    }
}
