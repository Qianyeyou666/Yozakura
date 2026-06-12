package gq.vapulite.ui.click.vape;

import gq.vapulite.engine.render.ui.LiquidGlassSettings;
import gq.vapulite.module.render.HUD;
import gq.vapulite.module.render.ClickGUI;
import gq.vapulite.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;

final class ClickGuiThemeRenderer {
    private final VapeClickGui gui;
    private float glassProgress = 1.0f;

    ClickGuiThemeRenderer(VapeClickGui gui) {
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
        if (HUD.isSakuraTheme() || HUD.isLightTheme()) {
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
        float blurRadius = HUD.isSakuraTheme() ? 24.0f : HUD.isLightTheme() ? 26.0f : 30.0f;
        float refraction = 0.76f + Math.min(strength, 1.25f) * 0.20f;
        float highlight = HUD.isLightTheme() || HUD.isSakuraTheme() ? 1.08f : 0.96f;
        float grain = HUD.isSakuraTheme() ? 0.08f : 0.13f;
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
                gui.withAlpha(gui.guiColors().backdrop, 86.0f * gui.guiAlpha));
        if (HUD.isSakuraTheme()) {
            RenderServices.shapes().gradient(0, 0, width, height,
                    gui.withAlpha(new Color(255, 234, 244, 56).getRGB(), 56.0f * gui.guiAlpha),
                    gui.withAlpha(new Color(232, 198, 218, 34).getRGB(), 34.0f * gui.guiAlpha));
            RenderServices.shapes().gradient(0, height * 0.58f, width, height,
                    gui.withAlpha(new Color(255, 255, 255, 0).getRGB(), 0.0f),
                    gui.withAlpha(new Color(248, 223, 236, 70).getRGB(), 62.0f * gui.guiAlpha));
            return;
        }
        if (HUD.isLightTheme()) {
            RenderServices.shapes().gradient(0, 0, width, height,
                    gui.withAlpha(new Color(255, 255, 255, 72).getRGB(), 64.0f * gui.guiAlpha),
                    gui.withAlpha(new Color(210, 225, 242, 42).getRGB(), 38.0f * gui.guiAlpha));
            RenderServices.shapes().gradient(0, height * 0.62f, width, height,
                    gui.withAlpha(new Color(255, 255, 255, 0).getRGB(), 0.0f),
                    gui.withAlpha(new Color(194, 205, 218, 66).getRGB(), 58.0f * gui.guiAlpha));
            return;
        }
        RenderServices.shapes().gradient(0, 0, width, height,
                gui.withAlpha(new Color(51, 73, 99, 44).getRGB(), 44.0f * gui.guiAlpha),
                gui.withAlpha(new Color(6, 8, 10, 92).getRGB(), 92.0f * gui.guiAlpha));
        RenderServices.shapes().gradient(0, height * 0.62f, width, height,
                gui.withAlpha(new Color(0, 0, 0, 0).getRGB(), 0.0f),
                gui.withAlpha(new Color(0, 0, 0, 130).getRGB(), 92.0f * gui.guiAlpha));
    }

    void drawThemeFade(ScaledResolution sr) {
        if (gui.themeFadeProgress <= 0.01f) {
            return;
        }
        float eased = gui.easeOut(gui.themeFadeProgress);
        int color = HUD.isSakuraTheme()
                ? new Color(255, 226, 240).getRGB()
                : HUD.isLightTheme()
                ? new Color(230, 238, 248).getRGB()
                : new Color(20, 24, 32).getRGB();
        RenderServices.shapes().rect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                gui.withAlpha(color, 34.0f * eased * gui.guiAlpha));
    }
}
