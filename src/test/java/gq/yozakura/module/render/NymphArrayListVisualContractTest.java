package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NymphArrayListVisualContractTest {
    @Test
    public void hudExposesAndRendersEverySourceArrayListControl() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/HUD.java")), StandardCharsets.UTF_8);
        String backgrounds = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/NymphArrayListBackgroundPlan.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("NYMPHILILA"));
        assertTrue(source.contains("NymphArrayListStyle.ColorMode"));
        assertTrue(source.contains("enum NymphFont"));
        assertTrue(source.contains("enum NymphBackground"));
        assertTrue(source.contains("enum NymphToggleAnimation"));
        assertTrue(source.contains("nymphGlow"));
        assertTrue(source.contains("nymphGlowSize"));
        assertTrue(source.contains("drawNymphModuleList(screenWidth, factor, modules)"));
        assertTrue(source.contains("FontLoaders.circular(size)"));
        assertTrue(source.contains("mc.fontRendererObj.drawStringWithShadow"));
        assertTrue(source.contains("NymphArrayListStyle.animatedTextX("));
        assertFalse(source.contains("nymphRetainedEntries"));
        assertTrue(source.contains("NYMPH_TOGGLE_DURATION_MS = 500L"));
        assertTrue(source.contains("nymphDecelerate("));
        assertTrue(source.contains("entry.module.getName()"));
        assertTrue(backgrounds.contains("NymphBackground.BLUR"));
        assertTrue(backgrounds.contains("NymphBackground.BARLEFT"));
        assertTrue(backgrounds.contains("NymphBackground.NONE"));
        assertFalse("wheel scaling is the only ArrayList size control",
                source.contains("nymphFontSize"));
        assertFalse(source.contains("Nymph Font Size"));
        assertFalse(source.contains("Nymph Blur Passes"));
        assertFalse(source.contains("Nymph Blur Offset"));
        assertFalse(source.contains("Nymph Shadow"));
        assertTrue(source.contains("Nymph Glow Size"));
        assertTrue(source.contains("NymphArrayListStyle.FONT_SIZE"));
        assertTrue(source.contains("NymphArrayListStyle.ROW_HEIGHT"));
    }
}
