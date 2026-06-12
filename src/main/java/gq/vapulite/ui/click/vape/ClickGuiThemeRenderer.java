package gq.vapulite.ui.click.vape;

import gq.vapulite.module.render.HUD;
import gq.vapulite.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;

final class ClickGuiThemeRenderer {
    private final VapeClickGui gui;

    ClickGuiThemeRenderer(VapeClickGui gui) {
        this.gui = gui;
    }

    void drawThemedGlass(float x, float y, float x2, float y2, float radius,
                         float strength, int fill, int border) {
        if (HUD.isSakuraTheme()) {
            int solidFill = gui.withAlpha(gui.guiColors().glassFill, Math.max(gui.getAlpha(fill), 238.0f));
            int solidBorder = gui.withAlpha(gui.guiColors().glassBorder, Math.max(gui.getAlpha(border), 54.0f));
            RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, strength, solidFill, solidBorder);
            return;
        }
        RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, strength, fill, border);
    }

    void drawPanelGlass(float x, float y, float x2, float y2, float radius,
                        float strength, int fill, int border) {
        RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, strength, fill, border);
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
