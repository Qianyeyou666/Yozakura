package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class StandaloneInputEventBridgeContractTest {
    @Test
    public void keyInputEventsAreNotLimitedToModuleBindingKeys() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue("Standalone must scan the keyboard independently of module bindings",
                client.contains("for (int key = 0; key < KEYBOARD_SIZE; key++)"));
        assertTrue("Every physical key rising edge must dispatch the Forge key-input shim",
                client.contains("dispatchKeyPress(key);"));
        assertTrue("Module toggling remains a separate consumer of the detected key",
                client.contains("toggleModulesBoundTo(key);"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
