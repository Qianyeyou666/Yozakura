package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AimAssistConfigMigrationTest {
    @Test
    public void mapsLegacySilentBlatantToLockOn() {
        assertEquals("LOCK_ON", AimAssistConfigMigration.resolveMode("SILENT", "BLATANT"));
    }

    @Test
    public void combinesEveryLegacyModePair() {
        assertEquals("ADAPTIVE", AimAssistConfigMigration.resolveMode("NORMAL", "REGULAR"));
        assertEquals("ADAPTIVE", AimAssistConfigMigration.resolveMode("SILENT", "REGULAR"));
        assertEquals("LOCK_ON", AimAssistConfigMigration.resolveMode("NORMAL", "BLATANT"));
    }

    @Test
    public void preservesAlreadyMergedModes() {
        assertEquals("LOCK_ON", AimAssistConfigMigration.resolveMode("BLATANT", "REGULAR"));
        assertEquals("LOCK_ON", AimAssistConfigMigration.resolveMode("silent_blatant", "REGULAR"));
        assertEquals("LOCK_ON", AimAssistConfigMigration.resolveMode("lock-on", "REGULAR"));
    }

    @Test
    public void leavesUnknownLegacyModeUntouched() {
        assertNull(AimAssistConfigMigration.resolveMode("EXPERIMENTAL", "BLATANT"));
    }

}
