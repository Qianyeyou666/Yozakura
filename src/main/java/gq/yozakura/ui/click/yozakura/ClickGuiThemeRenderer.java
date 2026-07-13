package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

final class ClickGuiThemeRenderer {
    private final YozakuraClickGui gui;
    private float glassProgress = 1.0f;

    ClickGuiThemeRenderer(YozakuraClickGui gui) {
        this.gui = gui;
    }

    void resetGlassAnimation() {
        glassProgress = isGlassEnabled() ? 1.0f : 0.0f;
    }

    void updateGlassAnimation() {
        glassProgress = gui.animate(glassProgress, isGlassEnabled() ? 1.0f : 0.0f, 0.16f);
    }

    void drawThemedGlass(float x, float y, float x2, float y2, float radius,
                         float strength, int fill, int border) {
        if (!isGlassEnabled()) {
            drawSolidGlass(x, y, x2, y2, radius, strength, fill, border);
            return;
        }
        if (usesSolidGlass(ClickGUI.palette.getValue())) {
            int solidFill = gui.withAlpha(gui.guiColors().glassFill, Math.max(gui.getAlpha(fill), 238.0f));
            int solidBorder = gui.withAlpha(border, Math.max(gui.getAlpha(border), 72.0f));
            drawSolidGlass(x, y, x2, y2, radius, strength, solidFill, solidBorder);
            return;
        }
        drawSolidGlass(x, y, x2, y2, radius, strength, fill, border);
    }

    void drawPanelGlass(float x, float y, float x2, float y2, float radius,
                        float strength, int fill, int border) {
        if (glassProgress <= 0.01f) {
            drawSolidGlass(x, y, x2, y2, radius, strength, fill, border);
            return;
        }
        if (glassProgress < 0.99f) {
            drawSolidGlass(x, y, x2, y2, radius, strength, fill, border);
        }
        boolean solidGlass = usesSolidGlass(ClickGUI.palette.getValue());
        float blurRadius = solidGlass ? 24.0f : 30.0f;
        float refraction = 0.76f + Math.min(strength, 1.25f) * 0.20f;
        float highlight = solidGlass ? 1.08f : 0.96f;
        float grain = solidGlass ? 0.012f : 0.018f;
        float eased = gui.easeSmooth(glassProgress);
        LiquidGlassSettings settings = LiquidGlassSettings.defaults()
                .withBlurRadius(blurRadius)
                .withRefractionScale(refraction)
                .withHighlight(highlight)
                .withNoise(grain);
        RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, strength,
                gui.withAlpha(fill, gui.getAlpha(fill) * eased),
                gui.withAlpha(border, gui.getAlpha(border) * eased),
                settings);
    }

    private boolean isGlassEnabled() {
        return Boolean.TRUE.equals(ClickGUI.glassBackground.getValue());
    }

    private void drawSolidGlass(float x, float y, float x2, float y2, float radius,
                                float borderWidth, int fill, int border) {
        RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, borderWidth, fill, border);
    }

    void drawBackdrop(ScaledResolution sr) {
        float width = sr.getScaledWidth();
        float height = sr.getScaledHeight();
        RenderServices.shapes().rect(0, 0, width, height,
                gui.withAlpha(ClickGUI.currentPalette().getShadow(), 128.0f * gui.guiAlpha));
    }

    static boolean usesSolidGlass(ClickGUI.Palette palette) {
        return palette == ClickGUI.Palette.SAKURA;
    }

    void drawThemeFade(ScaledResolution sr) {
        if (gui.themeFadeProgress <= 0.01f) {
            return;
        }
    }
}
