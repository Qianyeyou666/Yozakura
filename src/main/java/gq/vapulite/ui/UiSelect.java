package gq.vapulite.ui;

import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ui.RenderServices;

import java.awt.Color;

public class UiSelect extends UiComponent {
    private String label = "";
    private String value = "";

    public UiSelect data(String label, String value) {
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
        return this;
    }

    @Override
    public UiSelect setBounds(float x, float y, float width, float height) {
        super.setBounds(x, y, width, height);
        return this;
    }

    @Override
    public UiSelect setAlpha(float alpha) {
        super.setAlpha(alpha);
        return this;
    }

    @Override
    public UiSelect setTheme(UiTheme theme) {
        super.setTheme(theme);
        return this;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible || alpha <= 0.0f) {
            return;
        }
        float labelW = Math.min(118.0f, Math.max(72.0f, bounds.width * 0.34f));
        FontLoaders.F14.drawString(trim(label, labelW - 10.0f), bounds.x, bounds.y + 8.0f,
                theme.withAlpha(theme.text, 255.0f * alpha));
        float pillX = bounds.x + labelW;
        float pillW = bounds.width - labelW;
        RenderServices.shapes().roundedBorder(pillX, bounds.y + 4.0f, pillX + pillW, bounds.y + 22.0f, 7.0f, 0.8f,
                theme.withAlpha(theme.control, 230.0f * alpha),
                theme.withAlpha(theme.selectBorder, 42.0f * alpha));
        FontLoaders.F14.drawString(trim(value, pillW - 26.0f), pillX + 12.0f, bounds.y + 10.0f,
                theme.withAlpha(theme.text, 245.0f * alpha));
        FontLoaders.F14.drawString("v", pillX + pillW - 13.0f, bounds.y + 9.0f,
                theme.withAlpha(theme.muted, 150.0f * alpha));
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
