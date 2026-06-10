package gq.vapulite.ui;

import gq.vapulite.Vapu.utils.RenderUtil;

import java.awt.Color;

public class UiPanel extends UiComponent {
    private float radius = 8.0f;
    private float borderWidth = 1.0f;
    private int fillColor = UiTheme.vape().panel;
    private int borderColor = UiTheme.vape().panelBorder;
    private int shadowColor = new Color(0, 0, 0, 220).getRGB();
    private int shadowLayers = 8;
    private float shadowSpread = 5.0f;
    private float shadowAlpha = 80.0f;

    public UiPanel radius(float radius) {
        this.radius = radius;
        return this;
    }

    public UiPanel fill(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public UiPanel border(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    public UiPanel borderWidth(float borderWidth) {
        this.borderWidth = borderWidth;
        return this;
    }

    public UiPanel shadow(int shadowColor, float alpha, int layers, float spread) {
        this.shadowColor = shadowColor;
        this.shadowAlpha = alpha;
        this.shadowLayers = layers;
        this.shadowSpread = spread;
        return this;
    }

    @Override
    public UiPanel setBounds(float x, float y, float width, float height) {
        super.setBounds(x, y, width, height);
        return this;
    }

    @Override
    public UiPanel setAlpha(float alpha) {
        super.setAlpha(alpha);
        return this;
    }

    @Override
    public UiPanel setTheme(UiTheme theme) {
        super.setTheme(theme);
        return this;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible || alpha <= 0.0f) {
            return;
        }
        if (shadowAlpha > 0.0f && shadowSpread > 0.0f && shadowLayers > 0) {
            RenderUtil.drawSoftShadow(bounds.x, bounds.y, bounds.right(), bounds.bottom(), radius,
                    theme.withAlpha(shadowColor, shadowAlpha * alpha), shadowLayers, shadowSpread);
        }
        int f = theme.withAlpha(fillColor, ((fillColor >>> 24) & 255) * alpha);
        int b = theme.withAlpha(borderColor, ((borderColor >>> 24) & 255) * alpha);
        if (gq.vapulite.Vapu.modules.render.HUD.isLightTheme()) {
            RenderUtil.drawRoundedBorderedRect(bounds.x, bounds.y, bounds.right(), bounds.bottom(), radius, borderWidth, f, b);
        } else {
            RenderUtil.drawFrostedGlassRect(bounds.x, bounds.y, bounds.right(), bounds.bottom(), radius, borderWidth, f, b);
        }
    }
}
