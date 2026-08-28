package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickerVapePortContractTest {
    @Test
    public void exposesThePortableVapeClickerSettings() throws IOException {
        String source = source();

        assertTrue(source.contains("Mode<AutoClickRandomization>"));
        assertTrue(source.contains("\"Randomization\""));
        assertTrue(source.contains("\"Hold to Click\""));
        assertTrue(source.contains("\"Trigger Mode\""));
        assertTrue(source.contains("\"Jitter\""));
        assertTrue(source.contains("\"Limit Items\""));
        assertTrue(source.contains("\"Allow Swords\""));
        assertTrue(source.contains("\"Allow Axes\""));
        assertTrue(source.contains("\"Allow Pickaxes\""));
        assertTrue(source.contains("\"Allow Shovels\""));
        assertTrue(source.contains("\"Break Blocks Min Delay (ms)\""));
        assertTrue(source.contains("\"Break Blocks Max Delay (ms)\""));
        assertTrue(source.contains("\"Break Blocks Whitelist\""));
        assertTrue(source.contains("\"Break With Pickaxes\""));
        assertTrue(source.contains("\"Break With Shovels\""));
    }

    @Test
    public void keepsMinecraftClicksOnTheExistingClientThreadPipeline() throws IOException {
        String source = source();

        assertTrue(source.contains("MinecraftAccessor.clickMouse(mc)"));
        assertTrue(source.contains("event.phase == TickEvent.Phase.START"));
        assertTrue(source.contains("handleAttackClick();"));
        assertFalse(source.contains("new Thread"));
        assertFalse(source.contains("Thread.sleep"));
        assertFalse(source.contains("java.awt.Robot"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/AutoClicker.java")), StandardCharsets.UTF_8);
    }
}
