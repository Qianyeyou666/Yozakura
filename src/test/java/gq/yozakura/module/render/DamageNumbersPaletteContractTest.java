package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class DamageNumbersPaletteContractTest {
    @Test
    public void damageNumbersExposeOneNamedPaletteTriplet() throws IOException {
        String damageNumbers = source("src/main/java/gq/yozakura/module/render/DamageNumbers.java");
        String palette = source("src/main/java/gq/yozakura/ui/click/yozakura/PanelPaletteColorControl.java");

        assertTrue(damageNumbers.contains("\"DamageRed\""));
        assertTrue(damageNumbers.contains("\"DamageGreen\""));
        assertTrue(damageNumbers.contains("\"DamageBlue\""));
        assertTrue(palette.contains("DAMAGE_NUMBERS(\"Damage Numbers\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
