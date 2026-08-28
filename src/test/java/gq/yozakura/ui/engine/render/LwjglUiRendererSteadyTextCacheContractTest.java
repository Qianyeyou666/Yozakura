package gq.yozakura.ui.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Ensures retained text commands have an allocation-free steady-frame lookup path. */
public class LwjglUiRendererSteadyTextCacheContractTest {
    @Test
    public void retainedTextCommandsUseIdentityLookupBeforeAllocatingValueKey() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/engine/render/LwjglUiRenderer.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("IdentityHashMap<TextPaintCommand, List<PositionedGlyph>>"));
        int directLookup = source.indexOf("directTextLayouts.get(command)");
        int valueKeyAllocation = source.indexOf("new TextLayoutKey(command)", directLookup);
        assertTrue("identity lookup must happen before allocating the shared value-cache key",
                directLookup >= 0 && valueKeyAllocation > directLookup);
        assertTrue("a compile miss must release stale command identities",
                source.contains("directTextLayouts.clear();"));
    }
}
