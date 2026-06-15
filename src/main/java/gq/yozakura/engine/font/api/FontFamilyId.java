package gq.yozakura.engine.font.api;

import net.minecraft.util.ResourceLocation;

public enum FontFamilyId {
    SF(new ResourceLocation("novo/fonts/SF.ttf"), null),
    CIRCULAR(new ResourceLocation("novo/fonts/CircularStd-Book.ttf"), null),
    CIRCULAR_MEDIUM(new ResourceLocation("novo/fonts/CircularStd-Medium.ttf"), null),
    PRODUCT_SANS(new ResourceLocation("novo/fonts/Product Sans Regular.ttf"), null),
    BAD_CACHE(new ResourceLocation("novo/fonts/badcache.ttf"), null),
    INTER(new ResourceLocation("font/Inter.ttf"), new ResourceLocation("font/Inter-Italic.ttf")),
    ALIBABA(new ResourceLocation("font/AlibabaSans-Regular.otf"), null),
    TENACITY_BOLD(new ResourceLocation("font/tenacity-bold.ttf"), null),
    ICON(new ResourceLocation("font/NovICON.ttf"), null);

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
