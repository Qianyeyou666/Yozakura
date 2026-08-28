package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ScaffoldBlockCounterVisualContractTest {
    @Test
    public void counterUsesCircleFontConfigurableAlphaBlurAndBloomMask() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/world/Scaffold.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("blockCounterBackgroundAlpha"));
        assertTrue(source.contains("blockCounterBlur"));
        assertTrue(source.contains("FontLoaders.circular(14)"));
        assertTrue(source.contains("ScaffoldBlockCounterMotion"));
        assertTrue(source.contains("RenderServices.blur().glass"));
        assertTrue(source.contains("queueBlockCounterBloomMask"));
        assertTrue(source.contains("GlowProfile.SHADOW"));
    }
}
