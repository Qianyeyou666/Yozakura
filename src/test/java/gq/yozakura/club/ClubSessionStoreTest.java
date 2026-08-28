package gq.yozakura.club;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClubSessionStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsTokenAndUsernameWithoutPassword() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "club-session.json");
        ClubSessionStore store = new ClubSessionStore(file);

        store.save(new ClubSession("club-token", "Sakura"));

        ClubSession loaded = store.load();
        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertEquals("club-token", loaded.getToken());
        assertEquals("Sakura", loaded.getUsername());
        assertFalse(raw.toLowerCase().contains("password"));
        assertFalse(raw.contains("correct horse battery"));
        assertFalse(new File(temporaryFolder.getRoot(), "club-session.json.tmp").exists());
    }

    @Test
    public void clearRemovesPersistedSession() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "club-session.json");
        ClubSessionStore store = new ClubSessionStore(file);
        store.save(new ClubSession("club-token", "Sakura"));

        store.clear();

        assertFalse(file.exists());
        assertNull(store.load());
    }

    @Test
    public void malformedSessionFailsClosed() throws Exception {
        File file = temporaryFolder.newFile("club-session.json");
        Files.write(file.toPath(), "{broken".getBytes(StandardCharsets.UTF_8));

        ClubSessionStore store = new ClubSessionStore(file);

        assertNull(store.load());
        assertTrue(file.exists());
    }
}
