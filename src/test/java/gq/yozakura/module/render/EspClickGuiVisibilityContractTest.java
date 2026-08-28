package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class EspClickGuiVisibilityContractTest {
    @Test
    public void espSkipsWorldAndScreenOverlaysWhileAnyYozakuraClickGuiIsOpen() throws IOException {
        assertSuppressesClickGui("src/main/java/gq/yozakura/module/render/ESP.java");
        assertSuppressesClickGui("src/main/java/gq/yozakura/module/render/BedESP.java");
    }

    private static void assertSuppressesClickGui(String path) throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        assertTrue(source.contains("isClickGuiOpen()"));
        assertTrue(source.contains("currentScreen.getClass().getName().startsWith(\"gq.yozakura.ui.click.\")"));
        assertTrue(source.contains("if (!isInGame() || isClickGuiOpen()"));
    }
}
