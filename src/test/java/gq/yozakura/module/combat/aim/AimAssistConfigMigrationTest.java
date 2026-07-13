package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AimAssistConfigMigrationTest {
    @Test
    public void combinesLegacySilentAndBlatantModes() {
        assertEquals("SILENT_BLATANT", AimAssistConfigMigration.resolveMode("SILENT", "BLATANT"));
    }

    @Test
    public void combinesEveryLegacyModePair() {
        assertEquals("NORMAL", AimAssistConfigMigration.resolveMode("NORMAL", "REGULAR"));
        assertEquals("SILENT", AimAssistConfigMigration.resolveMode("SILENT", "REGULAR"));
        assertEquals("BLATANT", AimAssistConfigMigration.resolveMode("NORMAL", "BLATANT"));
    }

    @Test
    public void preservesAlreadyMergedModes() {
        assertEquals("BLATANT", AimAssistConfigMigration.resolveMode("BLATANT", "REGULAR"));
        assertEquals("SILENT_BLATANT", AimAssistConfigMigration.resolveMode("silent_blatant", "REGULAR"));
    }

    @Test
    public void leavesUnknownLegacyModeUntouched() {
        assertNull(AimAssistConfigMigration.resolveMode("EXPERIMENTAL", "BLATANT"));
    }

    @Test
    public void invertsTheLegacyMoveDirectionMeaning() {
        assertFalse(AimAssistConfigMigration.migrateKeepMoveDirection(true));
        assertTrue(AimAssistConfigMigration.migrateKeepMoveDirection(false));
    }
}
