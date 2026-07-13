package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.VisualPalette;

/**
 * Night Bloom tokens for the standard ClickGUI. Surfaces stay quiet while focus and hover retain
 * distinct chroma roles: magenta for selection, cyan for auxiliary feedback.
 */
final class YozakuraVisualTokens {
    final int backdrop;
    final int topBar;
    final int card;
    final int cardHover;
    final int cardOpen;
    final int text;
    final int muted;
    final int faint;
    final int accent;
    final int danger;
    final int glassFill;
    final int glassFillSoft;
    final int glassBorder;
    final int navHover;
    final int selectedFill;
    final int focusBorder;
    final int switchGlow;
    final int valueTrack;
    final int valueFill;
    final int modeExpandedFill;
    final int modeSelected;
    final int modeHover;
    final int dropdown;
    final int dropdownShadow;
    final int shadow;

    private YozakuraVisualTokens(VisualPalette palette) {
        backdrop = withAlpha(palette.getCanvas(), 164);
        topBar = withAlpha(palette.getSurface(), 232);
        card = withAlpha(palette.getSurface(), 222);
        cardHover = withAlpha(palette.getSurfaceRaised(), 232);
        cardOpen = withAlpha(palette.getSurfaceOverlay(), 238);
        text = palette.getTextPrimary();
        muted = palette.getTextSecondary();
        faint = palette.getTextDisabled();
        accent = palette.getAccentPrimary();
        danger = palette.getDanger();
        glassFill = withAlpha(palette.getCanvas(), 154);
        glassFillSoft = withAlpha(palette.getSurface(), 122);
        glassBorder = withAlpha(palette.getBorderSubtle(), 58);
        navHover = withAlpha(palette.getInfo(), 190);
        selectedFill = withAlpha(palette.getSurfaceOverlay(), 218);
        focusBorder = palette.getBorderFocus();
        switchGlow = palette.getGlowPrimary();
        valueTrack = withAlpha(palette.getInfo(), 178);
        valueFill = withAlpha(palette.getAccentPrimary(), 230);
        modeExpandedFill = withAlpha(palette.getSurfaceOverlay(), 200);
        modeSelected = withAlpha(palette.getAccentSoft(), 160);
        modeHover = withAlpha(palette.getInfo(), 140);
        dropdown = withAlpha(palette.getSurfaceRaised(), 240);
        dropdownShadow = withAlpha(palette.getShadow(), 200);
        shadow = withAlpha(palette.getShadow(), 210);
    }

    static YozakuraVisualTokens from(VisualPalette palette) {
        return new YozakuraVisualTokens(palette);
    }

    static YozakuraVisualTokens nightBloom() {
        return from(VisualPalette.nightBloom());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
