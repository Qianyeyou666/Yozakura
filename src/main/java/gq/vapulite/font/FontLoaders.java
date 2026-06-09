package gq.vapulite.font;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class FontLoaders {
    public static final String ICON_BUG = "a";
    public static final String ICON_LIST = "b";
    public static final String ICON_BOMB = "c";
    public static final String ICON_EYE = "d";
    public static final String ICON_PERSON = "e";
    public static final String ICON_MOVEMENT = "f";
    public static final String ICON_SCRIPT = "g";
    public static final String ICON_INFO = "m";
    public static final String ICON_SETTINGS = "n";
    public static final String ICON_CHECKMARK = "o";
    public static final String ICON_XMARK = "p";
    public static final String ICON_WARNING = "r";
    public static final String ICON_DROPDOWN_ARROW = "z";
    public static final String ICON_SEARCH = "B";
    public static final String ICON_STAR_OUTLINE = "F";
    public static final String ICON_STAR = "G";

    private static final Map<String, Font> FONT_DATA = new HashMap<String, Font>();
    private static final Map<String, CFontRenderer> RENDERERS = new HashMap<String, CFontRenderer>();

    public static final CFontRenderer F14 = regular(14);
    public static final CFontRenderer F16 = regular(16);
    public static final CFontRenderer F18 = regular(18);
    public static final CFontRenderer F20 = regular(20);
    public static final CFontRenderer F22 = regular(22);
    public static final CFontRenderer F23 = regular(23);
    public static final CFontRenderer F24 = regular(24);
    public static final CFontRenderer F30 = regular(30);
    public static final CFontRenderer F40 = regular(40);

    public static final CFontRenderer C12 = regular(12);
    public static final CFontRenderer C14 = regular(14);
    public static final CFontRenderer C16 = regular(16);
    public static final CFontRenderer C18 = regular(18);
    public static final CFontRenderer C20 = regular(20);
    public static final CFontRenderer C22 = regular(22);
    public static final CFontRenderer C30 = regular(30);

    public static final CFontRenderer Logo = icon(40);
    public static final CFontRenderer I14 = icon(14);
    public static final CFontRenderer I16 = icon(16);
    public static final CFontRenderer I18 = icon(18);
    public static final CFontRenderer I20 = icon(20);
    public static final CFontRenderer I26 = icon(26);
    public static final ArrayList<CFontRenderer> fonts = new ArrayList<CFontRenderer>();

    static {
        for (int size = 10; size <= 40; size++) {
            fonts.add(regular(size));
        }
    }

    public static CFontRenderer getFontRender(int size) {
        if (size >= 10 && size <= 40) {
            return fonts.get(size - 10);
        }
        return regular(size);
    }

    public static CFontRenderer regular(int size) {
        return renderer(FontFamily.INTER, size);
    }

    public static CFontRenderer icon(int size) {
        return renderer(FontFamily.ICON, size);
    }

    public static Font getFont(int size) {
        return derive(FontFamily.INTER, Font.PLAIN, size);
    }

    public static Font getComfortaa(int size) {
        return derive(FontFamily.INTER, Font.PLAIN, size);
    }

    public static Font getNovo(int size) {
        return derive(FontFamily.ICON, Font.PLAIN, size);
    }

    private static CFontRenderer renderer(FontFamily family, int size) {
        int clampedSize = Math.max(1, size);
        String key = family.name() + ":" + clampedSize;
        CFontRenderer cached = RENDERERS.get(key);
        if (cached != null) {
            return cached;
        }
        CFontRenderer renderer = new CFontRenderer(
                derive(family, Font.PLAIN, clampedSize),
                derive(family, Font.BOLD, clampedSize),
                derive(family, Font.ITALIC, clampedSize),
                derive(family, Font.BOLD | Font.ITALIC, clampedSize),
                true,
                true);
        RENDERERS.put(key, renderer);
        return renderer;
    }

    private static Font derive(FontFamily family, int style, int size) {
        boolean italic = (style & Font.ITALIC) != 0;
        boolean bold = (style & Font.BOLD) != 0;
        Font base = loadFont(family, italic && family.italicLocation != null);
        int deriveStyle = bold ? Font.BOLD : Font.PLAIN;
        if (italic && family.italicLocation == null) {
            deriveStyle |= Font.ITALIC;
        }
        return base.deriveFont(deriveStyle, (float) Math.max(1, size));
    }

    private static Font loadFont(FontFamily family, boolean italic) {
        String key = family.name() + (italic ? ":italic" : ":regular");
        Font cached = FONT_DATA.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation location = italic && family.italicLocation != null ? family.italicLocation : family.location;
        try {
            InputStream inputStream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                FONT_DATA.put(key, font);
                return font;
            } finally {
                inputStream.close();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error loading font " + location);
            Font fallback = new Font("default", Font.PLAIN, 16);
            FONT_DATA.put(key, fallback);
            return fallback;
        }
    }

    private enum FontFamily {
        INTER(new ResourceLocation("font/Inter.ttf"), new ResourceLocation("font/Inter-Italic.ttf")),
        ICON(new ResourceLocation("font/TenacityIcon.ttf"), null);

        private final ResourceLocation location;
        private final ResourceLocation italicLocation;

        FontFamily(ResourceLocation location, ResourceLocation italicLocation) {
            this.location = location;
            this.italicLocation = italicLocation;
        }
    }
}
