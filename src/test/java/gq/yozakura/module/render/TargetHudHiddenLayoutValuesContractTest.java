package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetHudHiddenLayoutValuesContractTest {
    @Test
    public void layoutNumbersRemainPersistedButAreHiddenFromClickGui() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/TargetHUD.java");

        assertTrue(source.contains("xPosition.visibleWhen(() -> false);"));
        assertTrue(source.contains("yPosition.visibleWhen(() -> false);"));
        assertTrue(source.contains("scale.visibleWhen(() -> false);"));
        assertTrue(source.contains("xOffset.visibleWhen(() -> false);"));
        assertTrue(source.contains("yOffset.visibleWhen(() -> false);"));
        assertTrue(source.contains("addValues(xPosition, yPosition, scale, xOffset, yOffset"));
        assertTrue(source.contains("HudDrag.update"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
