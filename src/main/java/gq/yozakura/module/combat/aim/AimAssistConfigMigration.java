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
        if ("LOCK_ON".equals(mode) || "LOCK-ON".equals(mode)
                || "BLATANT".equals(mode) || "SILENT_BLATANT".equals(mode)) {
            return "LOCK_ON";
        }
        if (!"NORMAL".equals(mode) && !"SILENT".equals(mode) && !"ADAPTIVE".equals(mode)) {
            return null;
        }
        if (!"BLATANT".equals(normalize(legacyVapeMode))) {
            return "ADAPTIVE";
        }
        return "LOCK_ON";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
