package gq.yozakura.util.render;

import gq.yozakura.engine.render.ui.VisualPalette;

/**
 * Resolves the two world-glow layers exclusively from a semantic palette.
 */
public final class ScreenSpaceGlowColors {
    private final int coreColor;
    private final int outerColor;

    private ScreenSpaceGlowColors(VisualPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        coreColor = palette.getAccentAlt();
        outerColor = palette.getGlowPrimary();
    }

    public static ScreenSpaceGlowColors from(VisualPalette palette) {
        return new ScreenSpaceGlowColors(palette);
    }

    public int getCoreColor() {
        return coreColor;
    }

    public int getOuterColor() {
        return outerColor;
    }
}
