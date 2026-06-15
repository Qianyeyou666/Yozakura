package gq.yozakura.ui;

import gq.yozakura.module.render.HUD;

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
    public final int toggleOff;
    public final int toggleOn;
    public final int toggleKnobOff;
    public final int toggleKnobOn;
    public final int fieldFill;
    public final int fieldBorder;
    public final int fieldInnerBorder;
    public final int sliderTrack;
    public final int selectBorder;

    public UiTheme(int text, int muted, int faint, int accent, int danger, int panel, int panelBorder,
                   int control, int controlHover, int toggleOff, int toggleOn, int toggleKnobOff, int toggleKnobOn,
                   int fieldFill, int fieldBorder, int fieldInnerBorder, int sliderTrack, int selectBorder) {
        this.text = text;
        this.muted = muted;
        this.faint = faint;
        this.accent = accent;
        this.danger = danger;
        this.panel = panel;
        this.panelBorder = panelBorder;
        this.control = control;
        this.controlHover = controlHover;
        this.toggleOff = toggleOff;
        this.toggleOn = toggleOn;
        this.toggleKnobOff = toggleKnobOff;
        this.toggleKnobOn = toggleKnobOn;
        this.fieldFill = fieldFill;
        this.fieldBorder = fieldBorder;
        this.fieldInnerBorder = fieldInnerBorder;
        this.sliderTrack = sliderTrack;
        this.selectBorder = selectBorder;
    }

    public static UiTheme vape() {
        return vapeDark();
    }

    private static UiTheme vapeDark() {
        return new UiTheme(
                new Color(232, 234, 236).getRGB(),
                new Color(152, 154, 158).getRGB(),
                new Color(83, 86, 92).getRGB(),
                new Color(132, 117, 255).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(7, 9, 13, 154).getRGB(),
                new Color(154, 190, 214, 58).getRGB(),
                new Color(7, 9, 13, 136).getRGB(),
                new Color(23, 27, 35, 158).getRGB(),
                new Color(37, 39, 42, 235).getRGB(),
                new Color(132, 117, 255, 225).getRGB(),
                new Color(112, 118, 123).getRGB(),
                new Color(226, 241, 246).getRGB(),
                new Color(6, 8, 12, 156).getRGB(),
                new Color(132, 117, 255).getRGB(),
                new Color(104, 92, 210).getRGB(),
                new Color(58, 61, 72, 220).getRGB(),
                new Color(78, 85, 105).getRGB());
    }

    private static UiTheme vapeLight() {
        return new UiTheme(
                new Color(28, 30, 36).getRGB(),        // text
                new Color(96, 100, 108).getRGB(),       // muted
                new Color(148, 155, 168).getRGB(),      // faint
                new Color(24, 142, 198).getRGB(),       // accent
                new Color(182, 50, 55).getRGB(),        // danger
                new Color(232, 236, 244, 148).getRGB(), // panel
                new Color(160, 175, 198, 54).getRGB(),  // panelBorder
                new Color(225, 228, 235, 158).getRGB(), // control
                new Color(210, 214, 224, 182).getRGB(), // controlHover
                new Color(200, 205, 215, 225).getRGB(), // toggleOff
                new Color(90, 165, 230, 225).getRGB(),   // toggleOn
                new Color(140, 145, 155).getRGB(),      // toggleKnobOff
                new Color(230, 240, 248).getRGB(),      // toggleKnobOn
                new Color(216, 220, 228, 156).getRGB(), // fieldFill
                new Color(80, 175, 215).getRGB(),       // fieldBorder
                new Color(70, 155, 195).getRGB(),       // fieldInnerBorder
                new Color(200, 205, 216, 220).getRGB(), // sliderTrack
                new Color(100, 108, 130).getRGB());     // selectBorder
    }

    private static UiTheme vapeSakura() {
        return new UiTheme(
                new Color(36, 30, 38).getRGB(),         // text
                new Color(120, 108, 118).getRGB(),      // muted
                new Color(176, 156, 170).getRGB(),      // faint
                new Color(229, 107, 157).getRGB(),      // accent
                new Color(200, 70, 78).getRGB(),        // danger
                new Color(255, 249, 252, 250).getRGB(), // panel
                new Color(226, 165, 194, 104).getRGB(), // panelBorder
                new Color(252, 239, 247, 196).getRGB(), // control
                new Color(248, 226, 238, 220).getRGB(), // controlHover
                new Color(210, 198, 210, 232).getRGB(), // toggleOff
                new Color(226, 112, 162, 238).getRGB(), // toggleOn
                new Color(142, 132, 145).getRGB(),      // toggleKnobOff
                new Color(255, 248, 252).getRGB(),      // toggleKnobOn
                new Color(250, 238, 246, 205).getRGB(), // fieldFill
                new Color(221, 145, 180).getRGB(),      // fieldBorder
                new Color(204, 126, 164).getRGB(),      // fieldInnerBorder
                new Color(232, 211, 224, 226).getRGB(), // sliderTrack
                new Color(170, 128, 152).getRGB());     // selectBorder
    }

    private static UiTheme vapeGray() {
        return new UiTheme(
                new Color(232, 234, 236).getRGB(),
                new Color(168, 171, 176).getRGB(),
                new Color(112, 116, 122).getRGB(),
                new Color(184, 192, 204).getRGB(),
                new Color(190, 88, 92).getRGB(),
                new Color(20, 23, 28, 170).getRGB(),
                new Color(190, 196, 206, 62).getRGB(),
                new Color(24, 27, 32, 150).getRGB(),
                new Color(42, 46, 54, 170).getRGB(),
                new Color(58, 61, 66, 232).getRGB(),
                new Color(178, 186, 198, 230).getRGB(),
                new Color(132, 136, 142).getRGB(),
                new Color(245, 246, 248).getRGB(),
                new Color(22, 25, 30, 158).getRGB(),
                new Color(170, 176, 188).getRGB(),
                new Color(138, 146, 158).getRGB(),
                new Color(78, 82, 90, 220).getRGB(),
                new Color(118, 126, 140).getRGB());
    }

    public static UiTheme current() {
        try {
            HUD.Theme theme = HUD.getTheme();
            if (theme == HUD.Theme.SAKURA) {
                return vapeSakura();
            }
            if (theme == HUD.Theme.LIGHT) {
                return vapeLight();
            }
            if (theme == HUD.Theme.GRAY) {
                return vapeGray();
            }
            return vapeDark();
        } catch (Exception e) {
            return vapeDark();
        }
    }

    public int withAlpha(int color, float alpha) {
        int a = (int) Math.max(0.0f, Math.min(255.0f, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
