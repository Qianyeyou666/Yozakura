package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.render.ClickGUI;

/** Compact HSV popup used by Panel custom-palette swatches. */
public final class PanelPaletteColorPicker {
    public static final float POPUP_WIDTH = 158.0f;
    public static final float POPUP_HEIGHT = 120.0f;
    public static final float PADDING = 8.0f;
    public static final float SV_WIDTH = 118.0f;
    public static final float SV_HEIGHT = 88.0f;
    public static final float HUE_WIDTH = 12.0f;
    public static final float GAP = 8.0f;

    private PanelPaletteColorControl.Group group;
    private float hue;
    private float saturation;
    private float value;
    private boolean draggingSv;
    private boolean draggingHue;
    private boolean draggingAlpha;
    private int lastAppliedColor;

    public void open(PanelPaletteColorControl.Group group) {
        this.group = group;
        float[] hsv = ClickGuiTheme.rgbToHsv(group.color());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        draggingSv = false;
        draggingHue = false;
        draggingAlpha = false;
        lastAppliedColor = group.color();
    }

    public void close() {
        group = null;
        draggingSv = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    public boolean isOpen() {
        return group != null;
    }

    public boolean isDragging() {
        return draggingSv || draggingHue || draggingAlpha;
    }

    public PanelPaletteColorControl.Group group() {
        return group;
    }

    public PanelClickGuiLayout.Rect bounds(PanelClickGuiLayout.Rect anchor,
                                           PanelClickGuiLayout.Rect panel) {
        float x = Math.max(panel.x() + 3.0f,
                Math.min(anchor.right() - POPUP_WIDTH, panel.right() - 3.0f - POPUP_WIDTH));
        float y = anchor.bottom() + 4.0f;
        if (y + POPUP_HEIGHT > panel.bottom() - 3.0f) {
            y = anchor.y() - POPUP_HEIGHT - 4.0f;
        }
        return new PanelClickGuiLayout.Rect(x, y, POPUP_WIDTH, POPUP_HEIGHT);
    }

    public void draw(PanelClickGuiLayout.Rect popup, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        RenderServices.shapes().shadow(popup.x(), popup.y(), popup.right(), popup.bottom(),
                EpsilonPanelMetrics.CARD_RADIUS, PanelClickGuiPalette.shadow(150), 8, 12.0f);
        RenderServices.shapes().roundedBorderWH(popup.x(), popup.y(), popup.width(), popup.height(),
                EpsilonPanelMetrics.CARD_RADIUS, 1.0f, PanelClickGuiPalette.overlay(),
                PanelClickGuiPalette.border());

        PanelClickGuiLayout.Rect sv = svBounds(popup);
        RenderServices.shapes().roundedPalette(sv.x(), sv.y(), sv.right(), sv.bottom(),
                EpsilonPanelMetrics.CONTROL_RADIUS, hue, 1.0f);
        RenderServices.shapes().roundedBorderWH(sv.x(), sv.y(), sv.width(), sv.height(),
                EpsilonPanelMetrics.CONTROL_RADIUS, 1.0f, 0x00000000,
                PanelClickGuiPalette.border());

        float cursorX = sv.x() + saturation * sv.width();
        float cursorY = sv.y() + (1.0f - value) * sv.height();
        RenderServices.shapes().circle(cursorX, cursorY, 0, 360, 5.0f, 0xFFFFFFFF);
        RenderServices.shapes().circle(cursorX, cursorY, 0, 360, 3.0f, currentColor());

        PanelClickGuiLayout.Rect hueBar = hueBounds(popup);
        RenderServices.shapes().roundedHue(hueBar.x(), hueBar.y(), hueBar.right(),
                hueBar.bottom(), EpsilonPanelMetrics.CONTROL_RADIUS, 1.0f);
        float hueY = hueBar.y() + hue * hueBar.height();
        RenderServices.shapes().roundedWH(hueBar.x() - 2.0f, hueY - 2.0f,
                hueBar.width() + 4.0f, 4.0f, 2.0f, 0xFFFFFFFF);

        if (!group.isHudPalette()) {
            PanelClickGuiLayout.Rect alpha = alphaBounds(popup);
            drawAlphaTrack(alpha);
            float alphaProgress = alphaProgress();
            float alphaX = alpha.x() + alpha.width() * alphaProgress;
            RenderServices.shapes().roundedBorderWH(alpha.x(), alpha.y(), alpha.width(), alpha.height(),
                    4.0f, 1.0f, 0x00000000, PanelClickGuiPalette.border());
            RenderServices.shapes().circle(alphaX, alpha.y() + alpha.height() * 0.5f,
                    0, 360, 4.0f, 0xFFFFFFFF);
            RenderServices.shapes().circle(alphaX, alpha.y() + alpha.height() * 0.5f,
                    0, 360, 2.5f, PanelClickGuiPalette.accent());
        }
    }

    public boolean mouseClicked(PanelClickGuiLayout.Rect popup, int mouseX, int mouseY, int button) {
        if (!isOpen() || button != 0) {
            return false;
        }
        PanelClickGuiLayout.Rect sv = svBounds(popup);
        if (sv.contains(mouseX, mouseY)) {
            draggingSv = true;
            updateSv(sv, mouseX, mouseY);
            apply();
            return true;
        }
        PanelClickGuiLayout.Rect hueBar = hueBounds(popup);
        if (hueBar.contains(mouseX, mouseY)) {
            draggingHue = true;
            updateHue(hueBar, mouseY);
            apply();
            return true;
        }
        PanelClickGuiLayout.Rect alpha = alphaBounds(popup);
        if (!group.isHudPalette() && alpha.contains(mouseX, mouseY)) {
            draggingAlpha = true;
            updateAlpha(alpha, mouseX);
            return true;
        }
        if (!popup.contains(mouseX, mouseY)) {
            close();
        }
        return true;
    }

    public boolean mouseDragged(PanelClickGuiLayout.Rect popup, float mouseX, float mouseY) {
        if (draggingSv) {
            updateSv(svBounds(popup), mouseX, mouseY);
            apply();
            return true;
        }
        if (draggingHue) {
            updateHue(hueBounds(popup), mouseY);
            apply();
            return true;
        }
        if (draggingAlpha) {
            updateAlpha(alphaBounds(popup), mouseX);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSv = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    private PanelClickGuiLayout.Rect svBounds(PanelClickGuiLayout.Rect popup) {
        return new PanelClickGuiLayout.Rect(popup.x() + PADDING, popup.y() + PADDING,
                SV_WIDTH, SV_HEIGHT);
    }

    private PanelClickGuiLayout.Rect hueBounds(PanelClickGuiLayout.Rect popup) {
        return new PanelClickGuiLayout.Rect(popup.x() + PADDING + SV_WIDTH + GAP,
                popup.y() + PADDING, HUE_WIDTH, SV_HEIGHT);
    }

    private PanelClickGuiLayout.Rect alphaBounds(PanelClickGuiLayout.Rect popup) {
        return new PanelClickGuiLayout.Rect(popup.x() + PADDING, popup.bottom() - 16.0f,
                popup.width() - PADDING * 2.0f, 8.0f);
    }

    private void drawAlphaTrack(PanelClickGuiLayout.Rect alpha) {
        RenderServices.stencil().initWrite();
        try {
            RenderServices.shapes().roundedWH(alpha.x(), alpha.y(), alpha.width(), alpha.height(),
                    4.0f, 0xFFFFFFFF);
            RenderServices.stencil().read(1);
            float tile = 4.0f;
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < (int) Math.ceil(alpha.width() / tile); column++) {
                    float x = alpha.x() + column * tile;
                    float x2 = Math.min(alpha.right(), x + tile);
                    float y = alpha.y() + row * tile;
                    int color = ((row + column) & 1) == 0 ? 0xFF6B6670 : 0xFFAAA4B0;
                    RenderServices.shapes().rect(x, y, x2, y + tile, color);
                }
            }
            int color = currentColor();
            RenderServices.shapes().roundedGradient(alpha.x(), alpha.y(), alpha.right(), alpha.bottom(),
                    4.0f, color & 0x00FFFFFF, color & 0x00FFFFFF, color, color);
        } finally {
            RenderServices.stencil().end();
        }
    }

    private float alphaProgress() {
        float alpha = ClickGUI.clickGuiAlpha.getValue().floatValue();
        return clamp((alpha - 0.3f) / 0.7f);
    }

    private void updateAlpha(PanelClickGuiLayout.Rect alpha, float mouseX) {
        double next = 0.3D + clamp((mouseX - alpha.x()) / alpha.width()) * 0.7D;
        double increment = ClickGUI.clickGuiAlpha.getIncrement().doubleValue();
        if (increment > 0.0D) {
            next = 0.3D + Math.round((next - 0.3D) / increment) * increment;
        }
        ClickGUI.clickGuiAlpha.setNumberValue(Math.max(0.3D, Math.min(1.0D, next)));
    }

    private void updateSv(PanelClickGuiLayout.Rect sv, float mouseX, float mouseY) {
        saturation = clamp((mouseX - sv.x()) / sv.width());
        value = 1.0f - clamp((mouseY - sv.y()) / sv.height());
    }

    private void updateHue(PanelClickGuiLayout.Rect hueBar, float mouseY) {
        hue = clamp((mouseY - hueBar.y()) / hueBar.height());
    }

    private int currentColor() {
        return ClickGuiTheme.hsvToArgb(hue, saturation, value, 0xFF);
    }

    private void apply() {
        int color = currentColor();
        if (color == lastAppliedColor) {
            return;
        }
        lastAppliedColor = color;
        group.setChannel(0, (color >> 16) & 0xFF);
        group.setChannel(1, (color >> 8) & 0xFF);
        group.setChannel(2, color & 0xFF);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
