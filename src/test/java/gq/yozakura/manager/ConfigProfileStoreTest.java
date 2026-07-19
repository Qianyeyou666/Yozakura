package gq.yozakura.manager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigProfileStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void listsOnlyYzkProfilesInCaseInsensitiveNameOrder() throws Exception {
        File directory = temporaryFolder.newFolder("configs");
        write(new File(directory, "Zulu.yzk"), "{}");
        write(new File(directory, "alpha.YZK"), "{}");
        write(new File(directory, "ignored.json"), "{}");
        new File(directory, "folder.yzk").mkdir();

        ConfigProfileStore store = new ConfigProfileStore(directory);

        assertEquals(Arrays.asList("alpha", "Zulu"), store.list());
        assertEquals("{}", store.load("alpha"));
    }

    @Test
    public void savesAndLoadsAPlainJsonYzkProfile() throws Exception {
        ConfigProfileStore store = new ConfigProfileStore(temporaryFolder.newFolder("configs"));

        store.save("Legit Config (v2)+", "{\"Reach\":{\"state\":true}}");

        assertEquals("{\"Reach\":{\"state\":true}}", store.load("Legit Config (v2)+"));
        assertTrue(new File(store.getDirectory(), "Legit Config (v2)+.yzk").isFile());
    }

    @Test
    public void loadsAProfileDroppedIntoTheDirectory() throws Exception {
        File directory = temporaryFolder.newFolder("configs");
        write(new File(directory, "shared.yzk"), "{\"Velocity\":{\"state\":false}}");

        ConfigProfileStore store = new ConfigProfileStore(directory);

        assertEquals("{\"Velocity\":{\"state\":false}}", store.load("shared"));
    }

    @Test
    public void rejectsNamesThatCanEscapeTheProfileDirectory() throws Exception {
        ConfigProfileStore store = new ConfigProfileStore(temporaryFolder.newFolder("configs"));

        assertRejected(store, "../outside");
        assertRejected(store, "nested\\outside");
        assertRejected(store, "bad/name");
        assertRejected(store, "profile.yzk.bak");
    }

    @Test
    public void malformedImportedProfileFailsExplicitly() throws Exception {
        File directory = temporaryFolder.newFolder("configs");
        write(new File(directory, "broken.yzk"), "{broken");
        ConfigProfileStore store = new ConfigProfileStore(directory);

        try {
            store.load("broken");
            fail("Expected malformed imported profile to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("broken.yzk"));
        }
    }

    private static void assertRejected(ConfigProfileStore store, String name) throws IOException {
        try {
            store.save(name, "{}");
            fail("Expected unsafe profile name to be rejected: " + name);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("profile name"));
        }
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
