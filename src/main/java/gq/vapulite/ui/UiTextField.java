package gq.vapulite.ui;

import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
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
        int border = focused ? theme.accent : new Color(80, 84, 90).getRGB();
        RenderUtil.drawRoundedBorderedRect(bounds.x, bounds.y, bounds.right(), bounds.bottom(), 7.0f, 0.8f,
                theme.withAlpha(new Color(11, 15, 21, 218).getRGB(), 205.0f * alpha),
                theme.withAlpha(border, (focused ? 125.0f : 38.0f) * alpha));
        String shown = text.length() == 0 ? placeholder : text;
        int color = text.length() == 0 ? theme.muted : theme.text;
        FontLoaders.F14.drawString(trim(shown, bounds.width - 48.0f), bounds.x + 10.0f, bounds.y + 8.0f,
                theme.withAlpha(color, (text.length() == 0 ? 170.0f : 230.0f) * alpha));
        FontLoaders.F14.drawCenteredString("Q", bounds.right() - 14.0f, bounds.y + 8.0f,
                theme.withAlpha(theme.muted, 160.0f * alpha));
        if (focused && (System.currentTimeMillis() - cursorTime) / 360L % 2L == 0L) {
            float cursorX = bounds.x + 10.0f + (text.length() == 0 ? 0.0f : FontLoaders.F14.getStringWidth(trim(text, bounds.width - 48.0f)) + 2.0f);
            RenderUtil.drawRect(cursorX, bounds.y + 6.0f, cursorX + 0.8f, bounds.bottom() - 6.0f,
                    theme.withAlpha(theme.text, 190.0f * alpha));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        focused = isHovered(mouseX, mouseY);
        cursorTime = System.currentTimeMillis();
        if (focused && button == 0 && text.length() > 0 && mouseX >= bounds.right() - 34.0f) {
            text = "";
        }
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
}
