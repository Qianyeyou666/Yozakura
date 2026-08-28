package gq.yozakura.manager;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CloudConfigSnapshotContractTest {
    @Test
    public void fileManagerExposesControlledSnapshotImportExportAndProfileTransfer() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        String bridge = source("src/main/java/gq/yozakura/core/ConfigBridge.java");

        assertTrue(source.contains("public synchronized String exportSnapshot()"));
        assertTrue(source.contains("public synchronized void importSnapshot(String snapshot)"));
        assertTrue(source.contains("public synchronized String readProfileSnapshot(String name)"));
        assertTrue(source.contains("public synchronized void saveProfileSnapshot(String name, String snapshot)"));
        assertTrue(bridge.contains("exportSnapshot()"));
        assertTrue(bridge.contains("importSnapshot(String snapshot)"));
        assertTrue(bridge.contains("readProfileSnapshot(String name)"));
        assertTrue(bridge.contains("saveProfileSnapshot(String name, String snapshot)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
