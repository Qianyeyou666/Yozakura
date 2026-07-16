package gq.yozakura.manager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigFileStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void malformedPrimaryLoadsBackupAndRepairsPrimary() throws Exception {
        File primary = temporaryFolder.newFile("module.json");
        File backup = temporaryFolder.newFile("module.json.bak");
        write(primary, "{broken");
        write(backup, "{\"Reach\":{\"state\":true}}");

        ConfigFileStore store = new ConfigFileStore(primary, backup);
        String loaded = store.load();

        assertEquals("{\"Reach\":{\"state\":true}}", loaded);
        assertTrue(store.wasBackupRecovered());
        assertEquals(read(backup), read(primary));
    }

    @Test
    public void invalidPrimaryAndBackupFailInsteadOfLoadingDefaults() throws Exception {
        File primary = temporaryFolder.newFile("module.json");
        File backup = temporaryFolder.newFile("module.json.bak");
        write(primary, "{broken");
        write(backup, "[]");

        ConfigFileStore store = new ConfigFileStore(primary, backup);

        try {
            store.load();
            fail("Expected invalid configuration to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("module.json"));
        }
    }

    @Test
    public void saveDoesNotReplaceValidBackupWithMalformedPrimary() throws Exception {
        File primary = temporaryFolder.newFile("module.json");
        File backup = temporaryFolder.newFile("module.json.bak");
        write(primary, "{broken");
        write(backup, "{\"safe\":true}");

        ConfigFileStore store = new ConfigFileStore(primary, backup);
        store.save("{\"new\":true}");

        assertEquals("{\"safe\":true}", read(backup));
        assertEquals("{\"new\":true}", read(primary));
    }

    @Test
    public void saveBacksUpThePreviousValidSnapshot() throws Exception {
        File primary = temporaryFolder.newFile("module.json");
        File backup = new File(temporaryFolder.getRoot(), "module.json.bak");
        write(primary, "{\"old\":true}");

        ConfigFileStore store = new ConfigFileStore(primary, backup);
        store.save("{\"new\":true}");

        assertEquals("{\"old\":true}", read(backup));
        assertEquals("{\"new\":true}", read(primary));
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
