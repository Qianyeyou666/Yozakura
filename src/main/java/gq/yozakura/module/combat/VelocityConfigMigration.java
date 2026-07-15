package gq.yozakura.module.combat;

import java.util.Locale;

/**
 * Recognizes values that were saved by the pre-Attack/Reduce Velocity layout.
 */
public final class VelocityConfigMigration {
    public static final int DEFAULT_REDUCE_HORIZONTAL = 60;

    private VelocityConfigMigration() {
    }

    public static Integer migrateLegacyReduceHorizontal(String storedMode, double storedHorizontal) {
        if (Double.compare(storedHorizontal, 100.0D) != 0
                || !"REDUCE".equals(normalize(storedMode))) {
            return null;
        }
        return Integer.valueOf(DEFAULT_REDUCE_HORIZONTAL);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
