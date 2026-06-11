package gq.vapulite.ui;

import gq.vapulite.Vapu.modules.render.HUD;

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
                new Color(112, 193, 220).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(7, 9, 13, 154).getRGB(),
                new Color(154, 190, 214, 58).getRGB(),
                new Color(7, 9, 13, 136).getRGB(),
                new Color(23, 27, 35, 158).getRGB(),
                new Color(37, 39, 42, 235).getRGB(),
                new Color(82, 79, 190, 225).getRGB(),
                new Color(112, 118, 123).getRGB(),
                new Color(226, 241, 246).getRGB(),
                new Color(6, 8, 12, 156).getRGB(),
                new Color(52, 135, 164).getRGB(),
                new Color(47, 108, 131).getRGB(),
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
                new Color(28, 30, 36).getRGB(),        // text
                new Color(96, 100, 108).getRGB(),       // muted
                new Color(148, 155, 168).getRGB(),      // faint
                new Color(225, 135, 162).getRGB(),      // accent — sakura pink
                new Color(200, 70, 78).getRGB(),        // danger
                new Color(248, 232, 238, 148).getRGB(), // panel — pink tint
                new Color(210, 175, 190, 54).getRGB(),  // panelBorder
                new Color(240, 225, 234, 158).getRGB(), // control
                new Color(228, 210, 222, 182).getRGB(), // controlHover
                new Color(218, 205, 216, 225).getRGB(), // toggleOff
                new Color(215, 118, 152, 225).getRGB(), // toggleOn — sakura pink
                new Color(145, 140, 150).getRGB(),      // toggleKnobOff
                new Color(248, 238, 242).getRGB(),      // toggleKnobOn
                new Color(236, 222, 230, 156).getRGB(), // fieldFill
                new Color(190, 130, 155).getRGB(),      // fieldBorder
                new Color(170, 110, 138).getRGB(),      // fieldInnerBorder
                new Color(220, 205, 215, 220).getRGB(), // sliderTrack
                new Color(150, 120, 140).getRGB());     // selectBorder
    }

    public static UiTheme current() {
        try {
            return HUD.isLightTheme() ? (HUD.getTheme() == HUD.Theme.SAKURA ? vapeSakura() : vapeLight()) : vapeDark();
        } catch (Exception e) {
            return vapeDark();
        }
    }

    public int withAlpha(int color, float alpha) {
        int a = (int) Math.max(0.0f, Math.min(255.0f, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
