package gq.vapulite.ui;

import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ui.RenderServices;

import java.awt.Color;

public class UiSlider extends UiComponent {
    private String label = "";
    private String valueText = "";
    private float value;
    private float active;

    public UiSlider data(String label, String valueText, float value, float active) {
        this.label = label == null ? "" : label;
        this.valueText = valueText == null ? "" : valueText;
        this.value = Math.max(0.0f, Math.min(1.0f, value));
        this.active = Math.max(0.0f, Math.min(1.0f, active));
        return this;
    }

    @Override
    public UiSlider setBounds(float x, float y, float width, float height) {
        super.setBounds(x, y, width, height);
        return this;
    }

    @Override
    public UiSlider setAlpha(float alpha) {
        super.setAlpha(alpha);
        return this;
    }

    @Override
    public UiSlider setTheme(UiTheme theme) {
        super.setTheme(theme);
        return this;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible || alpha <= 0.0f) {
            return;
        }
        float labelW = Math.min(118.0f, Math.max(72.0f, bounds.width * 0.34f));
        float barX = bounds.x + labelW;
        float valueW = 42.0f;
        float barW = Math.max(40.0f, bounds.width - labelW - valueW - 12.0f);
        FontLoaders.F14.drawString(trim(label, labelW - 10.0f), bounds.x, bounds.y + 8.0f,
                theme.withAlpha(theme.text, 255.0f * alpha));
        FontLoaders.F14.drawString(valueText, bounds.right() - FontLoaders.F14.getStringWidth(valueText), bounds.y + 8.0f,
                theme.withAlpha(theme.muted, 210.0f * alpha));
        if (active > 0.02f) {
            RenderServices.shapes().shadow(barX + barW * value - 3.0f, bounds.y + 12.0f, barX + barW * value + 3.0f, bounds.y + 20.0f,
                    3.0f, theme.withAlpha(theme.accent, 90.0f * active * alpha), 4, 2.5f);
        }
        RenderServices.shapes().rounded(barX, bounds.y + 15.0f, barX + barW, bounds.y + 17.0f, 2.0f,
                theme.withAlpha(theme.sliderTrack, 220.0f * alpha));
        RenderServices.shapes().progressBar(barX, bounds.y + 15.0f, barX + barW, bounds.y + 17.0f, 2.0f, value,
                0x00000000, theme.withAlpha(theme.accent, (220.0f + active * 35.0f) * alpha));
        float knobHalf = 2.2f + active * 1.1f;
        RenderServices.shapes().rounded(barX + barW * value - knobHalf, bounds.y + 12.0f - active,
                barX + barW * value + knobHalf, bounds.y + 20.0f + active, 3.0f,
                theme.withAlpha(theme.accent, 255.0f * alpha));
    }

    public float barX() {
        float labelW = Math.min(118.0f, Math.max(72.0f, bounds.width * 0.34f));
        return bounds.x + labelW;
    }

    public float barWidth() {
        float labelW = Math.min(118.0f, Math.max(72.0f, bounds.width * 0.34f));
        return Math.max(40.0f, bounds.width - labelW - 42.0f - 12.0f);
    }

    private static String trim(String text, float maxWidth) {
        if (FontLoaders.F14.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && FontLoaders.F14.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
}
