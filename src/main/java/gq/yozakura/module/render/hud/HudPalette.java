package gq.yozakura.module.render.hud;

public final class HudPalette {
    public final int text, muted, glass, glassSoft, border, accent, accentAlt;
    public final int vapePrimary, vapeSecondary, vapeTertiary;
    public final int vapeSurface, vapeSurfaceVariant, vapeOnSurface, vapeOnVariant;
    public final int shadowColor;

    public HudPalette(int text, int muted, int glass, int glassSoft, int border, int accent, int accentAlt,
                      int vapePrimary, int vapeSecondary, int vapeTertiary,
                      int vapeSurface, int vapeSurfaceVariant, int vapeOnSurface, int vapeOnVariant,
                      int shadowColor) {
        this.text = text; this.muted = muted; this.glass = glass; this.glassSoft = glassSoft;
        this.border = border; this.accent = accent; this.accentAlt = accentAlt;
        this.vapePrimary = vapePrimary; this.vapeSecondary = vapeSecondary; this.vapeTertiary = vapeTertiary;
        this.vapeSurface = vapeSurface; this.vapeSurfaceVariant = vapeSurfaceVariant;
        this.vapeOnSurface = vapeOnSurface; this.vapeOnVariant = vapeOnVariant;
        this.shadowColor = shadowColor;
    }

    public static final HudPalette DARK = new HudPalette(
            0xFFE8EAEC, 0xFF9EA8B8, 0xFF07090D, 0xFF0A0D12, 0xFF8DBED8,
            0xFF70C1DC, 0xFF8B7CFF,
            0xFF7C9DFF, 0xFF838CEF, 0xFF5AD4FF,
            0xFF171A20, 0xFF1E222B, 0xFFFFFFFF, 0xFFAAB2C5,
            0xFF000000);

    public static final HudPalette LIGHT = new HudPalette(
            0xFF1C1E22, 0xFF606468, 0xFFEBEDF2, 0xFFE0E3EA, 0xFF6BA0C0,
            0xFF18A0C8, 0xFF6088E8,
            0xFF6090E0, 0xFF6888E0, 0xFF20AAD4,
            0xFFE8EBF0, 0xFFDCE0E8, 0xFF181A20, 0xFF505560,
            0xFFFFFFFF);

    public static final HudPalette SAKURA = new HudPalette(
            0xFF241E26, 0xFF7A6E78, 0xFFFDF4F8, 0xFFF6E8F0, 0xFFE5AFC7,
            0xFFE56B9D, 0xFFD88AC4,
            0xFFE56B9D, 0xFFD979A8, 0xFFF3A4C8,
            0xFFFFF7FA, 0xFFF4E4ED, 0xFF241E26, 0xFF786A75,
            0x66F1B5CC);

    public static final HudPalette GRAY = new HudPalette(
            0xFFE7E8EA, 0xFFA8ABB0, 0xFF171A1F, 0xFF1F232A, 0xFFB7BDC6,
            0xFFB8C0CC, 0xFFD6DAE0,
            0xFFB8C0CC, 0xFFA8B0BC, 0xFFE0E4EA,
            0xFF1B1F25, 0xFF252A32, 0xFFF4F5F6, 0xFFB8BEC8,
            0xFF000000);
}
