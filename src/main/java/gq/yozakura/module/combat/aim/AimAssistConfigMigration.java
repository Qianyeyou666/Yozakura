package gq.yozakura.module.combat.aim;

import java.util.Locale;

/**
 * Translates the two pre-rewrite AimAssist settings into the consolidated Mode value.
 */
public final class AimAssistConfigMigration {
    private AimAssistConfigMigration() {
    }

    public static String resolveMode(String storedMode, String legacyVapeMode) {
        String mode = normalize(storedMode);
        if ("BLATANT".equals(mode) || "SILENT_BLATANT".equals(mode)) {
            return mode;
        }
        if (!"NORMAL".equals(mode) && !"SILENT".equals(mode)) {
            return null;
        }
        if (!"BLATANT".equals(normalize(legacyVapeMode))) {
            return mode;
        }
        return "SILENT".equals(mode) ? "SILENT_BLATANT" : "BLATANT";
    }

    public static boolean migrateKeepMoveDirection(boolean legacyValue) {
        return !legacyValue;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
