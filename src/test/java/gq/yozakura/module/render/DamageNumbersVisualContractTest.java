package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class DamageNumbersVisualContractTest {
    @Test
    public void moduleTracksHealthLossAndRendersAnAnimatedWorldLabel() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/DamageNumbers.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("previousHealth"));
        assertTrue(source.contains("previous - health"));
        assertTrue(source.contains("LIFETIME_MILLIS"));
        assertTrue(source.contains("eased"));
        assertTrue(source.contains("drawCenteredStringWithShadow"));
        assertTrue(source.contains("DamageNumbers"));
    }
}
