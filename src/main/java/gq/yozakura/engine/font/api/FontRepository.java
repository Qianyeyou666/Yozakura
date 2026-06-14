package gq.yozakura.engine.font.api;

import gq.yozakura.engine.font.CFontRenderer;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FontRepository {
    private static final Font SYSTEM_FALLBACK = new Font("Dialog", Font.PLAIN, 16);

    private final List<FontFamily> families = new ArrayList<FontFamily>();
    private final Map<FontFamilyId, FontFamily> familyById = new HashMap<FontFamilyId, FontFamily>();
    private FontFamilyId defaultFamily = FontFamilyId.SF;
    private FontFamilyId currentFamily = FontFamilyId.SF;

    public FontRepository() {
        init();
    }

    public void init() {
        families.clear();
        add(FontFamilyId.SF);
        add(FontFamilyId.CIRCULAR);
        add(FontFamilyId.CIRCULAR_MEDIUM);
        add(FontFamilyId.PRODUCT_SANS);
        add(FontFamilyId.BAD_CACHE);
        add(FontFamilyId.INTER);
        add(FontFamilyId.ALIBABA);
        add(FontFamilyId.TENACITY_BOLD);
        add(FontFamilyId.ICON);
        defaultFamily = FontFamilyId.SF;
        currentFamily = defaultFamily;
    }

    public void add(FontFamilyId family) {
        if (family != null && !familyById.containsKey(family)) {
            FontFamily fontFamily = new FontFamily(family);
            familyById.put(family, fontFamily);
            families.add(fontFamily);
        }
    }

    public void load(FontFamilyId family) {
        if (family == null) {
            return;
        }
        add(family);
        currentFamily = family;
    }

    public CFontRenderer renderer(FontFamilyId family, int size) {
        if (family == null) {
            family = currentFamily;
        }
        int clampedSize = Math.max(1, size);
        FontFamily fontFamily = familyById.get(family);
        if (fontFamily == null) {
            add(family);
            fontFamily = familyById.get(family);
        }
        return fontFamily.size(clampedSize, fallbacks(family, clampedSize));
    }

    public CFontRenderer current(int size) {
        return renderer(currentFamily, size);
    }

    public FontFamilyId fontBy(String name) {
        if (name == null) {
            return defaultFamily;
        }
        for (FontFamily family : families) {
            if (family.name().equalsIgnoreCase(name)) {
                return family.id();
            }
        }
        return defaultFamily;
    }

    public List<FontFamily> fonts() {
        return Collections.unmodifiableList(families);
    }

    public FontFamilyId currentFont() {
        return currentFamily;
    }

    public FontFamilyId defaultFont() {
        return defaultFamily;
    }

    public Font derive(FontFamilyId family, int style, int size) {
        FontFamily fontFamily = familyById.get(family);
        if (fontFamily == null) {
            add(family);
            fontFamily = familyById.get(family);
        }
        return fontFamily.derive(style, size);
    }

    private Font[] fallbacks(FontFamilyId family, int size) {
        if (family == FontFamilyId.ICON) {
            return new Font[]{derive(FontFamilyId.SF, Font.PLAIN, size), derive(FontFamilyId.ALIBABA, Font.PLAIN, size), SYSTEM_FALLBACK};
        }
        if (family == FontFamilyId.SF || family == FontFamilyId.CIRCULAR
                || family == FontFamilyId.CIRCULAR_MEDIUM || family == FontFamilyId.PRODUCT_SANS) {
            return new Font[]{derive(FontFamilyId.ALIBABA, Font.PLAIN, size), SYSTEM_FALLBACK};
        }
        return new Font[]{derive(FontFamilyId.ALIBABA, Font.PLAIN, size), SYSTEM_FALLBACK};
    }
}
