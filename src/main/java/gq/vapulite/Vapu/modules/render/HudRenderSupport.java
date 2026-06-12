package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.render.ShaderRenderer;
import gq.vapulite.render.ui.RenderServices;

final class HudRenderSupport {
    private HudRenderSupport() {
    }

    static void drawGlass(float x, float y, float x2, float y2, float radius,
                          int shadowColor, int fillColor, int borderColor) {
        RenderServices.shapes().shadow(x, y, x2, y2, radius, shadowColor, 6, 3.2f);
        drawThemedFrostedGlass(x, y, x2, y2, radius, 1.0f, fillColor, borderColor);
    }

    static void drawThemedFrostedGlass(float x, float y, float x2, float y2, float radius,
                                       float strength, int fillColor, int borderColor) {
        if (HUD.isLightTheme()) {
            RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, 0.8f, fillColor, borderColor);
        } else if (HUD.isGrayTheme()) {
            if (!ShaderRenderer.drawFrostedGlass(x, y, x2, y2, radius, strength, fillColor, borderColor)) {
                RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, 0.8f, fillColor, borderColor);
            }
        } else {
            RenderUtil.drawFrostedGlassRect(x, y, x2, y2, radius, strength, fillColor, borderColor);
        }
    }

    static void drawVapeCard(float x, float y, float x2, float y2, float radius,
                             int shadowColor, int fillColor, int borderColor) {
        RenderServices.shapes().shadow(x, y, x2, y2, radius, shadowColor, 6, 2.4f);
        RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, 0.8f, fillColor, borderColor);
        RenderServices.shapes().horizontalGradient(x + 1.0f, y + 1.0f, x2 - 1.0f,
                Math.min(y2 - 1.0f, y + 18.0f), 0x0EFFFFFF, 0x00000000);
    }
}
