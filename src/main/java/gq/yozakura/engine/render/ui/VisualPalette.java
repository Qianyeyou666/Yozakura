package gq.yozakura.engine.render.ui;

/**
 * Immutable semantic colors for the shared Night Bloom visual language.
 * Every value is packed as ARGB (alpha in the most-significant byte).
 */
public final class VisualPalette {
    private static final VisualPalette NIGHT_BLOOM = new VisualPalette(
            0xF20D0914, 0xE61A101E, 0xF0251728, 0xD92D1B32,
            0xFFF7EEF8, 0xFFBDAFBE, 0xFF706372,
            0x665F465E, 0xD8F39BCC,
            0xFFE98BC1, 0x66E98BC1, 0xFF72DFF6,
            0xFF70D8FF, 0xFF75D6A4, 0xFFFFC66D, 0xFFFF718C,
            0xFF75D6A4, 0xFFFFC66D, 0xFFFF718C, 0xB8A94B70,
            0xFF72DFF6, 0xFFFF7792, 0xFF8FDE9E, 0xAA9E91FF, 0xFFFFC66D,
            0xFFE6A66B, 0xFF9F8CFF,
            0x99EC8EC5, 0x7772DFF6, 0xB0000000
    );

    private static final VisualPalette SAKURA = new VisualPalette(
            0xFFF9EFF4, 0xFFFFF9FC, 0xFFFFF1F7, 0xFFFFDDEB,
            0xFF2C1D27, 0xFF806272, 0xFFB796A7,
            0x66DCA0BD, 0xFFE56B9D,
            0xFFE56B9D, 0x66E56B9D, 0xFF8C6BFF,
            0xFF5C8FE9, 0xFF4FAF7A, 0xFFE5A04F, 0xFFDB5A6A,
            0xFF4FAF7A, 0xFFE5A04F, 0xFFDB5A6A, 0xB8C77998,
            0xFFE56B9D, 0xFF8C6BFF, 0xFF6DC59B, 0xAAA58CFF, 0xFFFFB067,
            0xFFD69A60, 0xFF9A83E9,
            0x99E56B9D, 0x778C6BFF, 0x38000000
    );

    private static final VisualPalette OCEAN = new VisualPalette(
            0xF2071420, 0xE6102334, 0xF0173147, 0xD921405C,
            0xFFF0F8FF, 0xFFB2C8D8, 0xFF6D8799,
            0x665A8CA9, 0xFF4CC8FF,
            0xFF4CC8FF, 0x664CC8FF, 0xFF76E0C2,
            0xFF74B8FF, 0xFF72D49B, 0xFFFFC66D, 0xFFFF718C,
            0xFF72D49B, 0xFFFFC66D, 0xFFFF718C, 0xB86A9BB4,
            0xFF4CC8FF, 0xFF9E86FF, 0xFF75D6A4, 0xAA76CFFF, 0xFFFFC66D,
            0xFFDC9F63, 0xFF819DFF,
            0x994CC8FF, 0x7776E0C2, 0xB0000000
    );

    private static final VisualPalette GRAPHITE = new VisualPalette(
            0xF20B0D10, 0xE6161A20, 0xF0222730, 0xD92E3540,
            0xFFF4F6F8, 0xFFBEC5CD, 0xFF747D87,
            0x665F6874, 0xFFA7C7E7,
            0xFFA7C7E7, 0x66A7C7E7, 0xFFB9A8E5,
            0xFF8AB6E3, 0xFF7DCAA0, 0xFFF1C76F, 0xFFE87882,
            0xFF7DCAA0, 0xFFF1C76F, 0xFFE87882, 0xB88B98A6,
            0xFFA7C7E7, 0xFFB9A8E5, 0xFF8BC7A7, 0xAAA7C7E7, 0xFFF1C76F,
            0xFFE1A76D, 0xFFB7A8E7,
            0x99A7C7E7, 0x77B9A8E5, 0xB0000000
    );

    private final int canvas;
    private final int surface;
    private final int surfaceRaised;
    private final int surfaceOverlay;
    private final int textPrimary;
    private final int textSecondary;
    private final int textDisabled;
    private final int borderSubtle;
    private final int borderFocus;
    private final int accentPrimary;
    private final int accentSoft;
    private final int accentAlt;
    private final int info;
    private final int success;
    private final int warning;
    private final int danger;
    private final int healthHigh;
    private final int healthMid;
    private final int healthLow;
    private final int healthDamageTrail;
    private final int entityPlayer;
    private final int entityMob;
    private final int entityAnimal;
    private final int entityInvisible;
    private final int entityHurt;
    private final int storageChest;
    private final int storageEnderChest;
    private final int glowPrimary;
    private final int glowSecondary;
    private final int shadow;

    private VisualPalette(int canvas, int surface, int surfaceRaised, int surfaceOverlay,
                          int textPrimary, int textSecondary, int textDisabled,
                          int borderSubtle, int borderFocus,
                          int accentPrimary, int accentSoft, int accentAlt,
                          int info, int success, int warning, int danger,
                          int healthHigh, int healthMid, int healthLow, int healthDamageTrail,
                          int entityPlayer, int entityMob, int entityAnimal, int entityInvisible, int entityHurt,
                          int storageChest, int storageEnderChest,
                          int glowPrimary, int glowSecondary, int shadow) {
        this.canvas = canvas;
        this.surface = surface;
        this.surfaceRaised = surfaceRaised;
        this.surfaceOverlay = surfaceOverlay;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textDisabled = textDisabled;
        this.borderSubtle = borderSubtle;
        this.borderFocus = borderFocus;
        this.accentPrimary = accentPrimary;
        this.accentSoft = accentSoft;
        this.accentAlt = accentAlt;
        this.info = info;
        this.success = success;
        this.warning = warning;
        this.danger = danger;
        this.healthHigh = healthHigh;
        this.healthMid = healthMid;
        this.healthLow = healthLow;
        this.healthDamageTrail = healthDamageTrail;
        this.entityPlayer = entityPlayer;
        this.entityMob = entityMob;
        this.entityAnimal = entityAnimal;
        this.entityInvisible = entityInvisible;
        this.entityHurt = entityHurt;
        this.storageChest = storageChest;
        this.storageEnderChest = storageEnderChest;
        this.glowPrimary = glowPrimary;
        this.glowSecondary = glowSecondary;
        this.shadow = shadow;
    }

    public static VisualPalette nightBloom() {
        return NIGHT_BLOOM;
    }

    public static VisualPalette sakura() {
        return SAKURA;
    }

    public static VisualPalette ocean() {
        return OCEAN;
    }

    public static VisualPalette graphite() {
        return GRAPHITE;
    }

    public static VisualPalette custom(VisualPalette base, int canvas, int surface,
                                       int accentPrimary, int accentAlt, int danger,
                                       int entityPlayer, int entityMob, int entityAnimal,
                                       int storageChest, int storageEnderChest) {
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
        return new VisualPalette(
                canvas, surface, base.surfaceRaised, base.surfaceOverlay,
                base.textPrimary, base.textSecondary, base.textDisabled,
                base.borderSubtle, accentPrimary,
                accentPrimary, withAlpha(accentPrimary, 0x66), accentAlt,
                accentAlt, base.success, base.warning, danger,
                base.success, base.warning, danger, entityPlayer,
                entityPlayer, entityMob, entityAnimal, base.entityInvisible, danger,
                storageChest, storageEnderChest,
                accentPrimary, accentAlt, base.shadow
        );
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public int getCanvas() {
        return canvas;
    }

    public int getSurface() {
        return surface;
    }

    public int getSurfaceRaised() {
        return surfaceRaised;
    }

    public int getSurfaceOverlay() {
        return surfaceOverlay;
    }

    public int getTextPrimary() {
        return textPrimary;
    }

    public int getTextSecondary() {
        return textSecondary;
    }

    public int getTextDisabled() {
        return textDisabled;
    }

    public int getBorderSubtle() {
        return borderSubtle;
    }

    public int getBorderFocus() {
        return borderFocus;
    }

    public int getAccentPrimary() {
        return accentPrimary;
    }

    public int getAccentSoft() {
        return accentSoft;
    }

    public int getAccentAlt() {
        return accentAlt;
    }

    public int getInfo() {
        return info;
    }

    public int getSuccess() {
        return success;
    }

    public int getWarning() {
        return warning;
    }

    public int getDanger() {
        return danger;
    }

    public int getHealthHigh() {
        return healthHigh;
    }

    public int getHealthMid() {
        return healthMid;
    }

    public int getHealthLow() {
        return healthLow;
    }

    public int getHealthDamageTrail() {
        return healthDamageTrail;
    }

    public int getEntityPlayer() {
        return entityPlayer;
    }

    public int getEntityMob() {
        return entityMob;
    }

    public int getEntityAnimal() {
        return entityAnimal;
    }

    public int getEntityInvisible() {
        return entityInvisible;
    }

    public int getEntityHurt() {
        return entityHurt;
    }

    public int getStorageChest() {
        return storageChest;
    }

    public int getStorageEnderChest() {
        return storageEnderChest;
    }

    public int getGlowPrimary() {
        return glowPrimary;
    }

    public int getGlowSecondary() {
        return glowSecondary;
    }

    public int getShadow() {
        return shadow;
    }
}
