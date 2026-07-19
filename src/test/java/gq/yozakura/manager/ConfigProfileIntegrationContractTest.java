package gq.yozakura.manager;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ConfigProfileIntegrationContractTest {
    @Test
    public void startupAndShareableProfilesUseTheSameSnapshotApplicationPath() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        String startupLoad = method(source, "    public synchronized void loadModules(boolean quiet)",
                "    public synchronized List<String> listProfiles()");
        String profileLoad = method(source, "    public synchronized void loadProfile(String name)",
                "    public synchronized void markDirty()");

        assertTrue(startupLoad.contains("applySnapshot(snapshot);"));
        assertTrue(profileLoad.contains("applySnapshot(profileStore.load(name));"));
    }

    @Test
    public void loadedProfileBecomesTheCurrentPersistedConfiguration() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        String profileLoad = method(source, "    public synchronized void loadProfile(String name)",
                "    public synchronized void markDirty()");

        int apply = profileLoad.indexOf("applySnapshot(profileStore.load(name));");
        int persist = profileLoad.indexOf("writeSnapshot(snapshot);");
        assertTrue("The selected profile must apply before it replaces the current startup snapshot",
                apply >= 0 && persist > apply);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue(begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
