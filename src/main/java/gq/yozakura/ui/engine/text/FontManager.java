package gq.yozakura.ui.engine.text;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registered font faces, deterministic fallback selection and derived-size caching. */
public final class FontManager {
    private static final Map<TextAttribute, Object> KERNING_ATTRIBUTES =
            Collections.<TextAttribute, Object>singletonMap(
                    TextAttribute.KERNING, TextAttribute.KERNING_ON);

    private final Map<String, Family> families = new LinkedHashMap<String, Family>();
    private final List<String> fallbackFamilies = new ArrayList<String>();
    private final Map<DerivedKey, Font> derived = new HashMap<DerivedKey, Font>();

    public void register(String family, Font regular, Font bold) {
        String key = normalizeFamily(family);
        if (regular == null) {
            throw new IllegalArgumentException("regular font must not be null for " + family);
        }
        families.put(key, new Family(regular, bold));
        derived.clear();
    }

    public void registerResource(String family, String regularPath, String boldPath) {
        Font regular = loadResource(regularPath);
        Font bold = boldPath == null ? null : loadResource(boldPath);
        register(family, regular, bold);
    }

    public void addFallback(String family) {
        String key = normalizeFamily(family);
        if (!families.containsKey(key)) {
            throw new IllegalArgumentException("fallback family is not registered: " + family);
        }
        if (!fallbackFamilies.contains(key)) {
            fallbackFamilies.add(key);
            derived.clear();
        }
    }

    public Font resolve(String requestedFamily, boolean bold, int codePoint, float size) {
        if (!Character.isValidCodePoint(codePoint) || size <= 0.0F) {
            throw new IllegalArgumentException("valid codePoint and positive size are required");
        }
        String requested = normalizeFamily(requestedFamily);
        Font base = displayFace(families.get(requested), bold, codePoint);
        if (base == null) {
            for (int i = 0; i < fallbackFamilies.size(); i++) {
                base = displayFace(families.get(fallbackFamilies.get(i)), bold, codePoint);
                if (base != null) break;
            }
        }
        if (base == null) {
            throw new IllegalStateException("no registered font can display U+"
                    + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT)
                    + " for family '" + requestedFamily + "'");
        }

        DerivedKey cacheKey = new DerivedKey(base, bold, size);
        Font cached = derived.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Font face = base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size)
                .deriveFont(KERNING_ATTRIBUTES);
        derived.put(cacheKey, face);
        return face;
    }

    private static Font displayFace(Family family, boolean bold, int codePoint) {
        if (family == null) return null;
        Font candidate = bold && family.bold != null ? family.bold : family.regular;
        return candidate.canDisplay(codePoint) ? candidate : null;
    }

    private static Font loadResource(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("font resource path must not be empty");
        }
        InputStream input = FontManager.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("font resource not found: " + path);
        }
        try {
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (FontFormatException e) {
            throw new IllegalStateException("invalid font resource " + path + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("failed reading font resource " + path + ": " + e.getMessage(), e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // The parse/read exception above remains the actionable root cause.
            }
        }
    }

    private static String normalizeFamily(String family) {
        if (family == null || family.trim().isEmpty()) {
            throw new IllegalArgumentException("font family must not be empty");
        }
        return family.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Family {
        private final Font regular;
        private final Font bold;

        private Family(Font regular, Font bold) {
            this.regular = regular;
            this.bold = bold;
        }
    }

    private static final class DerivedKey {
        private final Font base;
        private final boolean bold;
        private final int sizeBits;

        private DerivedKey(Font base, boolean bold, float size) {
            this.base = base;
            this.bold = bold;
            this.sizeBits = Float.floatToIntBits(size);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof DerivedKey)) return false;
            DerivedKey other = (DerivedKey) value;
            return base == other.base && bold == other.bold && sizeBits == other.sizeBits;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(base);
            result = 31 * result + (bold ? 1 : 0);
            result = 31 * result + sizeBits;
            return result;
        }
    }
}
