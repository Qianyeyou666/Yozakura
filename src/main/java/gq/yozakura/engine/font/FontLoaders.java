package gq.yozakura.engine.font;

import gq.yozakura.engine.font.api.FontFamilyId;
import gq.yozakura.engine.font.api.FontRepository;
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
    public static final String ICON_PAUSE = "i";
    public static final String ICON_PLAY = "j";
    public static final String ICON_SHUFFLE = "l";
    public static final String ICON_INFO = "m";
    public static final String ICON_SETTINGS = "n";
    public static final String ICON_CHECKMARK = "o";
    public static final String ICON_XMARK = "p";
    public static final String ICON_TRASH = "q";
    public static final String ICON_WARNING = "r";
    public static final String ICON_SPARK = "s";
    public static final String ICON_DOWNLOAD = "t";
    public static final String ICON_SAVE = "u";
    public static final String ICON_ARROW_UP = "w";
    public static final String ICON_ARROW_DOWN = "y";
    public static final String ICON_DROPDOWN_ARROW = "z";
    public static final String ICON_EDIT = "A";
    public static final String ICON_SEARCH = "B";
    public static final String ICON_CLOUD_UPLOAD = "C";
    public static final String ICON_REFRESH = "D";
    public static final String ICON_FILE_ADD = "E";
    public static final String ICON_STAR_OUTLINE = "F";
    public static final String ICON_STAR = "G";
    public static final String ICON_SWORDS = "H";
    public static final String ICON_SHIELD = "I";
    public static final String ICON_CROSSHAIR = "J";
    public static final String ICON_CLOCK = "K";
    public static final String ICON_FOCUS = "L";
    public static final String ICON_SUN = "M";
    public static final String ICON_CURSOR = "N";
    public static final String ICON_RUN = "O";
    public static final String ICON_HEARTBEAT = "P";
    public static final String ICON_PICKAXE = "Q";
    public static final String ICON_CUBE = "R";
    public static final String ICON_GLOBE = "S";
    public static final String ICON_USER = "T";
    public static final String ICON_SUN_ALT = "U";
    public static final String ICON_FOLDER = "V";
    public static final String ICON_CODE = "W";
    public static final String ICON_DOWNLOAD_ALT = "X";
    public static final String ICON_MORE = "Y";

    private static final FontRepository FONT_REPOSITORY = new FontRepository();
    private static final Map<String, Font> FONT_DATA = new HashMap<String, Font>();
    private static final Font SYSTEM_FALLBACK = new Font("Dialog", Font.PLAIN, 16);
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

    public static final CFontRenderer TB12 = tenacityBold(12);
    public static final CFontRenderer TB14 = tenacityBold(14);
    public static final CFontRenderer TB16 = tenacityBold(16);
    public static final CFontRenderer TB18 = tenacityBold(18);

    public static final CFontRenderer Logo = icon(40);
    public static final CFontRenderer I14 = icon(14);
    public static final CFontRenderer I16 = icon(16);
    public static final CFontRenderer I18 = icon(18);
    public static final CFontRenderer I20 = icon(20);
    public static final CFontRenderer I26 = icon(26);
    public static final ArrayList<CFontRenderer> fonts = new LazyFontList();

    static {
        for (int size = 10; size <= 40; size++) {
            fonts.add(null);
        }
    }

    public static CFontRenderer getFontRender(int size) {
        if (size >= 10 && size <= 40) {
            return fonts.get(size - 10);
        }
        return regular(size);
    }

    public static CFontRenderer regular(int size) {
        return renderer(FontFamily.SF, size);
    }

    public static CFontRenderer circular(int size) {
        return renderer(FontFamily.CIRCULAR, size);
    }

    public static CFontRenderer circularMedium(int size) {
        return renderer(FontFamily.CIRCULAR_MEDIUM, size);
    }

    public static CFontRenderer productSans(int size) {
        return renderer(FontFamily.PRODUCT_SANS, size);
    }

    public static CFontRenderer badCache(int size) {
        return renderer(FontFamily.BAD_CACHE, size);
    }

    public static CFontRenderer icon(int size) {
        return renderer(FontFamily.ICON, size);
    }

    public static CFontRenderer tenacityBold(int size) {
        return renderer(FontFamily.TENACITY_BOLD, size);
    }

    public static Font getFont(int size) {
        return derive(FontFamily.SF, Font.PLAIN, size);
    }

    public static Font getComfortaa(int size) {
        return derive(FontFamily.CIRCULAR, Font.PLAIN, size);
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
        CFontRenderer renderer = FONT_REPOSITORY.renderer(toId(family), clampedSize);
        RENDERERS.put(key, renderer);
        return renderer;
    }

    private static FontFamilyId toId(FontFamily family) {
        if (family == FontFamily.ICON) {
            return FontFamilyId.ICON;
        }
        if (family == FontFamily.TENACITY_BOLD) {
            return FontFamilyId.TENACITY_BOLD;
        }
        if (family == FontFamily.SF) {
            return FontFamilyId.SF;
        }
        if (family == FontFamily.CIRCULAR) {
            return FontFamilyId.CIRCULAR;
        }
        if (family == FontFamily.CIRCULAR_MEDIUM) {
            return FontFamilyId.CIRCULAR_MEDIUM;
        }
        if (family == FontFamily.PRODUCT_SANS) {
            return FontFamilyId.PRODUCT_SANS;
        }
        if (family == FontFamily.BAD_CACHE) {
            return FontFamilyId.BAD_CACHE;
        }
        if (family == FontFamily.ALIBABA) {
            return FontFamilyId.ALIBABA;
        }
        return FontFamilyId.SF;
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
            InputStream inputStream = FontResourceLoader.open(location);
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                FONT_DATA.put(key, font);
                return font;
            } finally {
                inputStream.close();
            }
        } catch (Exception exception) {
            FontResourceLoader.logFailure(location, exception);
            Font fallback = SYSTEM_FALLBACK;
            FONT_DATA.put(key, fallback);
            return fallback;
        }
    }

    private static Font[] fallbacks(FontFamily family, int size) {
        if (family == FontFamily.ICON) {
            return new Font[]{derive(FontFamily.INTER, Font.PLAIN, size), derive(FontFamily.ALIBABA, Font.PLAIN, size), SYSTEM_FALLBACK};
        }
        return new Font[]{derive(FontFamily.ALIBABA, Font.PLAIN, size), SYSTEM_FALLBACK};
    }

    private enum FontFamily {
        SF(new ResourceLocation("novo/fonts/SF.ttf"), null),
        CIRCULAR(new ResourceLocation("novo/fonts/CircularStd-Book.ttf"), null),
        CIRCULAR_MEDIUM(new ResourceLocation("novo/fonts/CircularStd-Medium.ttf"), null),
        PRODUCT_SANS(new ResourceLocation("novo/fonts/Product Sans Regular.ttf"), null),
        BAD_CACHE(new ResourceLocation("novo/fonts/badcache.ttf"), null),
        INTER(new ResourceLocation("font/Inter.ttf"), new ResourceLocation("font/Inter-Italic.ttf")),
        ALIBABA(new ResourceLocation("font/AlibabaSans-Regular.otf"), null),
        TENACITY_BOLD(new ResourceLocation("font/tenacity-bold.ttf"), null),
        ICON(new ResourceLocation("font/NovICON.ttf"), null);

        private final ResourceLocation location;
        private final ResourceLocation italicLocation;

        FontFamily(ResourceLocation location, ResourceLocation italicLocation) {
            this.location = location;
            this.italicLocation = italicLocation;
        }
    }

    private static final class LazyFontList extends ArrayList<CFontRenderer> {
        @Override
        public CFontRenderer get(int index) {
            CFontRenderer renderer = super.get(index);
            if (renderer == null && index >= 0 && index <= 30) {
                renderer = regular(index + 10);
                super.set(index, renderer);
            }
            return renderer;
        }
    }
}
