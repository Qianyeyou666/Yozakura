package gq.yozakura.engine.font.api;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontResourceLoader;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class FontFamily {
    private static final Font SYSTEM_FALLBACK = new Font("Dialog", Font.PLAIN, 16);

    private final FontFamilyId id;
    private final Map<String, Font> fontData = new HashMap<String, Font>();
    private final Map<Integer, CFontRenderer> renderers = new HashMap<Integer, CFontRenderer>();

    public FontFamily(FontFamilyId id) {
        this.id = id;
    }

    public CFontRenderer size(int size, Font[] fallbacks) {
        int clampedSize = Math.max(1, size);
        CFontRenderer cached = renderers.get(clampedSize);
        if (cached != null) {
            return cached;
        }
        CFontRenderer renderer = new CFontRenderer(
                derive(Font.PLAIN, clampedSize),
                derive(Font.BOLD, clampedSize),
                derive(Font.ITALIC, clampedSize),
                derive(Font.BOLD | Font.ITALIC, clampedSize),
                true,
                true,
                fallbacks == null ? new Font[]{SYSTEM_FALLBACK} : fallbacks);
        renderers.put(clampedSize, renderer);
        return renderer;
    }

    public Font derive(int style, int size) {
        boolean italic = (style & Font.ITALIC) != 0;
        boolean bold = (style & Font.BOLD) != 0;
        Font base = load(italic && id.italic() != null);
        int deriveStyle = bold ? Font.BOLD : Font.PLAIN;
        if (italic && id.italic() == null) {
            deriveStyle |= Font.ITALIC;
        }
        return base.deriveFont(deriveStyle, (float) Math.max(1, size));
    }

    private Font load(boolean italic) {
        String key = italic ? "italic" : "regular";
        Font cached = fontData.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation location = italic && id.italic() != null ? id.italic() : id.regular();
        try {
            InputStream inputStream = FontResourceLoader.open(location);
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                fontData.put(key, font);
                return font;
            } finally {
                inputStream.close();
            }
        } catch (Exception exception) {
            FontResourceLoader.logFailure(location, exception);
            fontData.put(key, SYSTEM_FALLBACK);
            return SYSTEM_FALLBACK;
        }
    }

    public String name() {
        return id.name();
    }

    public FontFamilyId id() {
        return id;
    }
}
