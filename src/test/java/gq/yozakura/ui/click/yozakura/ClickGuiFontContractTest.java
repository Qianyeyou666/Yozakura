package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ClickGuiFontContractTest {
    @Test
    public void packagesBothFontsNamedByTheDesignSource() {
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/minecraft/font/Inter.ttf")));
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/minecraft/font/BricolageGrotesque.ttf")));
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/minecraft/font/JetBrainsMono.ttf")));
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/minecraft/font/epsilon-panel.ttf")));
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/minecraft/font/epsilon-icons.ttf")));
    }
}
