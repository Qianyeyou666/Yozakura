package gq.yozakura.engine.font.api;

import net.minecraft.util.ResourceLocation;

public enum FontFamilyId {
    SF(new ResourceLocation("novo/fonts/SF.ttf"), null),
    CIRCULAR(new ResourceLocation("novo/fonts/CircularStd-Book.ttf"), null),
    CIRCULAR_MEDIUM(new ResourceLocation("novo/fonts/CircularStd-Medium.ttf"), null),
    PRODUCT_SANS(new ResourceLocation("novo/fonts/Product Sans Regular.ttf"), null),
    PRODUCT_SANS_LIGHT(new ResourceLocation("novo/fonts/Product Sans Light.ttf"), null),
    PRODUCT_SANS_MEDIUM(new ResourceLocation("novo/fonts/Product Sans Medium.ttf"), null),
    BAD_CACHE(new ResourceLocation("novo/fonts/badcache.ttf"), null),
    INTER(new ResourceLocation("font/Inter.ttf"), new ResourceLocation("font/Inter-Italic.ttf")),
    BRICOLAGE(new ResourceLocation("font/BricolageGrotesque.ttf"), null),
    JETBRAINS_MONO(new ResourceLocation("font/JetBrainsMono.ttf"), null),
    ALIBABA(new ResourceLocation("font/AlibabaSans-Regular.otf"), null),
    TENACITY_BOLD(new ResourceLocation("font/tenacity-bold.ttf"), null),
    ICON(new ResourceLocation("font/TenacityIcon.ttf"), null),
    EPSILON_PANEL(new ResourceLocation("font/epsilon-panel.ttf"), null),
    EPSILON_ICONS(new ResourceLocation("font/epsilon-icons.ttf"), null);

    private final ResourceLocation regular;
    private final ResourceLocation italic;

    FontFamilyId(ResourceLocation regular, ResourceLocation italic) {
        this.regular = regular;
        this.italic = italic;
    }

    public ResourceLocation regular() {
        return regular;
    }

    public ResourceLocation italic() {
        return italic;
    }
}
