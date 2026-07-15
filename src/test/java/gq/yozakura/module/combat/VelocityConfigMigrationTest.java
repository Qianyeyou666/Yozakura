package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VelocityConfigMigrationTest {
    @Test
    public void replacesTheUnusedLegacyReduceDefaultHorizontalValue() {
        assertEquals(Integer.valueOf(VelocityConfigMigration.DEFAULT_REDUCE_HORIZONTAL),
                VelocityConfigMigration.migrateLegacyReduceHorizontal("Reduce", 100.0D));
    }

    @Test
    public void preservesCustomizedLegacyHorizontalValues() {
        assertNull(VelocityConfigMigration.migrateLegacyReduceHorizontal("Reduce", 60.0D));
    }

    @Test
    public void preservesFractionalAndUnrelatedConfigurations() {
        assertNull(VelocityConfigMigration.migrateLegacyReduceHorizontal("Reduce", 100.1D));
        assertNull(VelocityConfigMigration.migrateLegacyReduceHorizontal("Attack", 100.0D));
        assertNull(VelocityConfigMigration.migrateLegacyReduceHorizontal(null, 100.0D));
    }

    @Test
    public void fileManagerWritesTheMigratedDefaultInsteadOfRemovingTheValue() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/manager/FileManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("moduleJson.addProperty(\"Horizontal\", migratedHorizontal);"));
    }

    @Test
    public void migrationTargetMatchesTheVelocityDefault() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/Velocity.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("VelocityConfigMigration.DEFAULT_REDUCE_HORIZONTAL"));
    }
}
