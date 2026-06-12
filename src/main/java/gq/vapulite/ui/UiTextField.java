package gq.vapulite.ui;

import gq.vapulite.module.render.HUD;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class UiTextField extends UiComponent {
    private String text = "";
    private String placeholder = "";
    private boolean focused;
    private int maxLength = 32;
    private long cursorTime;

    public UiTextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public UiTextField text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    public UiTextField focused(boolean focused) {
        this.focused = focused;
        return this;
    }

    public UiTextField maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    public String text() {
        return text;
    }

    public boolean focused() {
        return focused;
    }

    @Override
    public UiTextField setBounds(float x, float y, float width, float height) {
        super.setBounds(x, y, width, height);
        return this;
    }

    @Override
    public UiTextField setAlpha(float alpha) {
        super.setAlpha(alpha);
        return this;
    }

    @Override
    public UiTextField setTheme(UiTheme theme) {
        super.setTheme(theme);
        return this;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible || alpha <= 0.0f) {
            return;
        }
        boolean hovered = isHovered(mouseX, mouseY);
        float radius = Math.min(17.0f, bounds.height / 2.0f);
        int border = focused || hovered ? theme.accent : theme.fieldBorder;
        float borderAlpha = focused ? 138.0f : hovered ? 96.0f : 72.0f;
        HUD.drawThemedFrostedGlass(bounds.x, bounds.y, bounds.right(), bounds.bottom(), radius, 1.15f,
                theme.withAlpha(theme.fieldFill, 156.0f * alpha),
                theme.withAlpha(border, borderAlpha * alpha));
        RenderServices.shapes().roundedBorder(bounds.x + 1.8f, bounds.y + 1.8f, bounds.right() - 1.8f,
                bounds.bottom() - 1.8f, Math.max(0.0f, radius - 1.8f), 0.6f,
                theme.withAlpha(new Color(255, 255, 255).getRGB(), 0.0f),
                theme.withAlpha(theme.fieldInnerBorder, 42.0f * alpha));
        if (focused || hovered) {
            RenderServices.shapes().shadow(bounds.x + 2.0f, bounds.y + 2.0f, bounds.right() - 2.0f, bounds.bottom() - 2.0f,
                    Math.max(0.0f, radius - 2.0f), theme.withAlpha(theme.accent, (focused ? 30.0f : 18.0f) * alpha), 4, 2.2f);
        }
        String shown = text.length() == 0 ? placeholder : text;
        int color = text.length() == 0 ? theme.muted : theme.text;
        float textY = bounds.y + (bounds.height - FontLoaders.F14.getStringHeight(shown)) / 2.0f + 0.5f;
        float textX = bounds.x + 24.0f;
        float iconCenterX = bounds.right() - 24.0f;
        FontLoaders.F14.drawString(trim(shown, bounds.width - 72.0f), textX, textY,
                theme.withAlpha(color, (text.length() == 0 ? 168.0f : 232.0f) * alpha));
        drawSearchIcon(iconCenterX, bounds.y + bounds.height / 2.0f,
                theme.withAlpha(focused || hovered ? new Color(206, 228, 236).getRGB() : theme.muted,
                        (focused ? 225.0f : hovered ? 198.0f : 176.0f) * alpha));
        if (focused && (System.currentTimeMillis() - cursorTime) / 360L % 2L == 0L) {
            float cursorX = textX + (text.length() == 0 ? 0.0f : FontLoaders.F14.getStringWidth(trim(text, bounds.width - 72.0f)) + 2.0f);
            RenderServices.shapes().rect(cursorX, bounds.y + 10.0f, cursorX + 0.8f, bounds.bottom() - 10.0f,
                    theme.withAlpha(theme.text, 190.0f * alpha));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        focused = isHovered(mouseX, mouseY);
        cursorTime = System.currentTimeMillis();
        return focused;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) {
            return false;
        }
        cursorTime = System.currentTimeMillis();
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (text.length() > 0) {
                text = "";
            } else {
                focused = false;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (text.length() > 0) {
                text = text.substring(0, text.length() - 1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            text = "";
            return true;
        }
        if (typedChar >= 32 && typedChar != 127 && text.length() < maxLength) {
            text += typedChar;
            return true;
        }
        return true;
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

    private static void drawSearchIcon(float centerX, float centerY, int color) {
        FontLoaders.I18.drawString(FontLoaders.ICON_SEARCH,
                centerX - FontLoaders.I18.getStringWidth(FontLoaders.ICON_SEARCH) / 2.0f,
                centerY - FontLoaders.I18.getHeight() / 2.0f + 2.0f, color);
    }
}
