package gq.vapulite.ui;

import java.awt.Color;

public final class UiTheme {
    public final int text;
    public final int muted;
    public final int faint;
    public final int accent;
    public final int danger;
    public final int panel;
    public final int panelBorder;
    public final int control;
    public final int controlHover;

    public UiTheme(int text, int muted, int faint, int accent, int danger, int panel, int panelBorder, int control, int controlHover) {
        this.text = text;
        this.muted = muted;
        this.faint = faint;
        this.accent = accent;
        this.danger = danger;
        this.panel = panel;
        this.panelBorder = panelBorder;
        this.control = control;
        this.controlHover = controlHover;
    }

    public static UiTheme vape() {
        return new UiTheme(
                new Color(232, 234, 236).getRGB(),
                new Color(152, 154, 158).getRGB(),
                new Color(83, 86, 92).getRGB(),
                new Color(112, 193, 220).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(13, 17, 23, 224).getRGB(),
                new Color(88, 98, 122).getRGB(),
                new Color(20, 24, 31, 230).getRGB(),
                new Color(37, 43, 54, 190).getRGB());
    }

    public int withAlpha(int color, float alpha) {
        int a = (int) Math.max(0.0f, Math.min(255.0f, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
